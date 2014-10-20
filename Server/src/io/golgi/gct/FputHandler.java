package io.golgi.gct;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import io.golgi.gct.gen.FputDetails;
import io.golgi.gct.gen.FputException;
import io.golgi.gct.gen.FputParams;
import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.TermStatus;

public class FputHandler {
	private Object syncObj = new Object();
	private PacketReceiver packetReceiver;
	private String identity;
	private Hashtable<String,FputContext> ctxtHash = new Hashtable<String,FputContext>();
	
	
	private class FputContext{
		private PacketReceiver.Consumer packetConsumer;
		private int pktTotal;
		private int pktCount;
		private String key;
		private FileOutputStream fos;
		private GCTService.fputComplete.ResultSender termResultSender;	
		private String errTxt;
	}
	
	private void maybeFinished(FputContext ctxt){
		synchronized(syncObj){
			if(ctxt.pktTotal >= 0 && ctxt.pktCount == ctxt.pktTotal){
				if(ctxt.termResultSender != null){
					try{
						ctxt.fos.close();
					}
					catch(IOException ioex){
						ctxt.errTxt = "Error writing file";
					}
					
					TermStatus s = new TermStatus();
					
					if(ctxt.errTxt != null){
						s.setErrorText(ctxt.errTxt);
						s.setExitCode(-1);
					}
					else{
						s.setErrorText("");
						s.setExitCode(0);
					}
					s.setKey(ctxt.key);
					s.setPktTotal(ctxt.pktTotal);
					ctxt.termResultSender.success(s);					
				}
				ctxtHash.remove(ctxt.key);
			}
		}
	}
	
    private GCTService.fputStart.RequestReceiver inboundFputStart = new GCTService.fputStart.RequestReceiver(){
		@Override
		public void receiveFrom(GCTService.fputStart.ResultSender resultSender,
								 FputDetails fputDetails) {
			System.out.println("[" + resultSender.getRequestSenderId() + "] ' PUT '" + fputDetails.getFilename() + "'");
			String errTxt = null;
			File f = new File(fputDetails.getFilename());
			FputParams params = new FputParams();
			FputContext ctxt = new FputContext();
			
			if(f.exists()){
				errTxt = "Destination already exists";
			}
			else{
				ctxt.pktTotal = -1;
				ctxt.pktCount = 0;
				ctxt.key = "" + System.currentTimeMillis() + "-" + identity;
				params.setSvrKey(ctxt.key);

				
        		try{
        			ctxt.fos = new FileOutputStream(f);
        		}
        		catch(FileNotFoundException fnfEx){
        			errTxt = "Cannot create destination file for writing";
        		}
        		
				ctxt.packetConsumer = new PacketReceiver.Consumer() {
				
					@Override
					public void receiverActivity() {
						// TODO Auto-generated method stub
						
					}
				
					@Override
					public void inboundPacket(OutputPacket pkt) {
						FputContext ctxt; 
						synchronized(syncObj){
							ctxt = ctxtHash.get(pkt.getDstKey());
						}
					
						if(ctxt != null){
							synchronized(syncObj){
								// System.out.println("Packet: " + pkt.getPktNum());
								ctxt.pktCount++;
								if(ctxt.errTxt == null){
									try{
										ctxt.fos.write(pkt.getData());
									}
									catch(IOException ioex){
										ctxt.errTxt = "Error writing file";
									}
								}
							}
							maybeFinished(ctxt);
						}
					}
				};
			
				resultSender.success(params);
			}
			if(errTxt != null){
				FputException fpe = new FputException();
				fpe.setErrCode(-1);
				fpe.setErrText(errTxt);
				resultSender.failure(fpe);
			}
			else{
				packetReceiver.addConsumer(ctxt.packetConsumer, resultSender.getRequestSenderId(), ctxt.key);
				
				synchronized(syncObj){
					ctxtHash.put(ctxt.key, ctxt);
				}
				resultSender.success(params);
			}
		}
    };
    
    private GCTService.fputComplete.RequestReceiver inboundFputComplete = new GCTService.fputComplete.RequestReceiver() {
		@Override
		public void receiveFrom(
				GCTService.fputComplete.ResultSender resultSender,
				TermStatus termStatus) {
			// System.out.println("fput complete: " + termStatus.getPktTotal());
			FputContext ctxt;
			synchronized(syncObj){
				ctxt = ctxtHash.get(termStatus.getKey());
				if(ctxt != null){
					ctxt.pktTotal = termStatus.getPktTotal();
					ctxt.termResultSender = resultSender;
				}
			}
			
			if(ctxt != null){
				maybeFinished(ctxt);
			}
		}
	};
    
	
	FputHandler(String identity, PacketReceiver packetReceiver){
		this.identity = identity;
		this.packetReceiver = packetReceiver;
        GCTService.fputStart.registerReceiver(inboundFputStart);
        GCTService.fputComplete.registerReceiver(inboundFputComplete);
	}
    

}
