package io.golgi.gct;

import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.TermStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;

public class FileSender {
	private Object syncObj = new Object();
	private String remoteId;
	private String remoteKey;
	private String filename;
	private FileInputStream fis;
	private GolgiTransportOptions stdGto;
	
	private boolean complete = false;
	private boolean termStatusSent = false;
	private int exitCode = 0;
	private int pktNum = 0;
	private String errorText = "";
	private CompletionHandler completionHandler; 
	private SenderThread fileSender;
	private PacketTransmitter pktTx;
	
	public interface CompletionHandler{
		public void fileSenderComplete(FileSender fgh, TermStatus termStatus);
	}
	
	public String getRemoteId(){
		return remoteId;
	}
	
	public String getRemoteKey(){
		return remoteKey;
	}
	
	private PacketTransmitter.ActivityWatcher activityWatcher = new PacketTransmitter.ActivityWatcher() {
		
		@Override
		public void transmitterActivity() {
			maybeFinished();
		}
	};
	
	private void maybeFinished(){
		synchronized(syncObj){
			if(complete && pktTx.queueSize() == 0 && !termStatusSent){
				TermStatus s = new TermStatus();
				s.setKey(remoteKey);
				s.setPktTotal(pktNum);
				s.setExitCode(exitCode);
				s.setErrorText("");
				completionHandler.fileSenderComplete(this, s);
				termStatusSent = true;
			}

		}
	}
	
	private class SenderThread extends Thread{
		
		public void run(){
			try{
				byte[] buffer = new byte[1024 * 1];
				int rc;
				
				while((rc = fis.read(buffer)) >= 0){
					// System.out.println("Read " + rc);
					OutputPacket pkt = new OutputPacket();
					byte[] data = new byte[rc];
					System.arraycopy(buffer, 0, data, 0, rc);
					pkt.setDstKey(remoteKey);
					pkt.setFd(1);
					pkt.setData(data);
					synchronized(syncObj){
						pkt.setPktNum(++pktNum);
					}
					pktTx.sendPacket(pkt);
				}
				synchronized(syncObj){
					exitCode = 0;
					errorText = "";
					complete = true;
				}
			}
			catch(IOException ioex){
				synchronized(syncObj){
					exitCode = -1;
					errorText = "Problem reading file";
					complete = true;
				}
			}
			maybeFinished();
		}
	}
	
	public void start(){
        pktTx = new PacketTransmitter(activityWatcher, remoteId);
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);
        
		try{
			fis = new FileInputStream(new File(filename));
			fileSender = new SenderThread();
			fileSender.start();
		}
		catch(FileNotFoundException fnfex){
			errorText = "File Not Found";
			exitCode = -1;
			complete = true;
			maybeFinished();
		}
	}
	public FileSender(CompletionHandler completionHandler, String remoteId, String remoteKey, String filename){
		this.completionHandler = completionHandler;
		this.remoteId = remoteId;
		this.remoteKey = remoteKey;
		this.filename = filename;
	}
}
