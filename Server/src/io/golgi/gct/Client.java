package io.golgi.gct;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;

import io.golgi.gct.gen.CommandDetails;
import io.golgi.gct.gen.FgetDetails;
import io.golgi.gct.gen.FputDetails;
import io.golgi.gct.gen.FputException;
import io.golgi.gct.gen.FputParams;
import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.TermStatus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.openmindnetworks.golgi.JavaType;
import com.openmindnetworks.golgi.api.GolgiAPI;
import com.openmindnetworks.golgi.api.GolgiAPIHandler;
import com.openmindnetworks.golgi.api.GolgiAPIImpl;
import com.openmindnetworks.golgi.api.GolgiAPINetworkImpl;
import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;
import com.openmindnetworks.slingshot.ntl.NTL;
import com.openmindnetworks.slingshot.tbx.TBX;


public class Client{
	private Object syncObj = new Object();
	private long timeOfDeath = 0;
	private String cliKey = null;
    private String devKey = null;
    private String appKey = null;
    private String identity = null;
    private String target = null;
    private String cmd = null;
    private GolgiTransportOptions stdGto;
    private boolean complete;
    private int pktTarget = 0;
    private int pktCount = 0;
    private int charCount = 0;
    private String exitErrString = null;
    private int exitCode = 0;
    private boolean runningFget = false;
    private boolean runningFput = false;
    private String fgetSrc = null;
    private String fgetDst = null;
    private String fputSrc = null;
    private String fputDst = null;
    private FileOutputStream fgetFos = null;
    private boolean verbose = false;
    private boolean force = false;
    private Hashtable<String,OutputPacket> pktHash = new Hashtable<String,OutputPacket>();
    private PacketReceiver packetReceiver;
    private Vector<FileSender> fsenders = new Vector<FileSender>();
    
    FileSender.CompletionHandler fputCompletionHandler = new FileSender.CompletionHandler() {
		@Override
		public void fileSenderComplete(FileSender fSender, TermStatus s) {
			fsenders.remove(fSender);
			GCTService.fputComplete.sendTo(new GCTService.fputComplete.ResultReceiver(){
				@Override
				public void success(TermStatus termStatus) {
					if(termStatus.getExitCode() == 0){
						System.exit(0);
					}
					else{
						System.err.println("fput failed: " + termStatus.getExitCode() + " '" + termStatus.getErrorText() + "'");
						System.exit(termStatus.getExitCode());
					}
				}

				@Override
				public void failure(GolgiException ex) {
					System.err.println("Error sending commandComplete: " + ex.getErrText());
					System.exit(-1);
				}
			},stdGto, fSender.getRemoteId(), s);
		}
	}; 
    
    
    
    void maybeFinished(){
    	synchronized(syncObj){
    		if(verbose) System.out.println("maybeFinished: " + complete + "/" + pktCount + "/" + pktTarget);
    		if(timeOfDeath == 0 && complete && pktCount == pktTarget){
    			timeOfDeath = System.currentTimeMillis();
    			// System.err.println("Data length: " + charCount);
    			
    			if(runningFget){
    				try{
    					fgetFos.close();
    				}
    				catch(IOException ioex){
    					exitCode = -1;
    					exitErrString = "Exception while closing destination file";
    				}
    			}
    		}
    	}
    }

    PacketReceiver.Consumer packetConsumer = new PacketReceiver.Consumer(){
    
    	@Override
    	public void receiverActivity(){
			maybeFinished();
    	}
    
    	@Override
    	public void inboundPacket(OutputPacket pkt){
    		synchronized(syncObj){
    			pktCount++;
    		}
    		byte[] data = pkt.getData();
    		if(pkt.getFd() == 1){
    			charCount += data.length;
    			if(runningFget){
    				System.out.print("|/-\\".charAt(pktCount%4) + "\b");
    				System.out.flush();
    				try{
    					fgetFos.write(pkt.getData());
    				}
    				catch(IOException ioex){
    					exitCode = -1;
    					exitErrString = "Exception while writing to the destination";
    				}
    			}
    			else{
    				try{
    					System.out.write(data);
    				}
    				catch(IOException ioex){
    				}
    			}
    		}
    		else{
    			try{
    				System.err.write(data);
    			}
    			catch(IOException ioex){
    			}
    		}
			maybeFinished();
    	}
    };
    	
    private GCTService.commandComplete.RequestReceiver inboundCommandComplete = new GCTService.commandComplete.RequestReceiver(){

		@Override
		public void receiveFrom(GCTService.commandComplete.ResultSender resultSender, TermStatus termStatus) {
			if(cliKey != null && termStatus.getKey().compareTo(cliKey) == 0){
				synchronized(syncObj){
					pktTarget = termStatus.getPktTotal();
					complete = true;
					exitCode = termStatus.getExitCode();
					if(verbose) System.out.println("Command Complete, pktTarget: " + pktTarget);
				}
				maybeFinished();
			}
		}
    };
    
    private GCTService.fgetComplete.RequestReceiver inboundFgetComplete = new GCTService.fgetComplete.RequestReceiver(){

		@Override
		public void receiveFrom(GCTService.fgetComplete.ResultSender resultSender, TermStatus termStatus) {
			if(cliKey != null && termStatus.getKey().compareTo(cliKey) == 0){
				synchronized(syncObj){
					pktTarget = termStatus.getPktTotal();
					complete = true;
					exitCode = termStatus.getExitCode();
					if(verbose) System.out.println("fget Complete, pktTarget: " + pktTarget);
				}
				maybeFinished();
			}
		}
    };
    
	
    private void looper(){
        Class<GolgiAPI> apiRef = GolgiAPI.class;
        GolgiAPI.setCryptoImpl(new BlowFishWrapper("crypto.keys"));
        GolgiAPINetworkImpl impl = new GolgiAPINetworkImpl();
        GolgiAPI.setAPIImpl(impl);
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);
        
        packetReceiver = new PacketReceiver();
        
        GCTService.commandComplete.registerReceiver(inboundCommandComplete);
        GCTService.fgetComplete.registerReceiver(inboundFgetComplete);
        
        GCTService.moreData.registerReceiver(packetReceiver);

        GolgiAPI.register(devKey,
                          appKey,
                          identity,
                          new GolgiAPIHandler(){

							@Override
							public void registerFailure() {
								System.out.println("Failed to register");
								System.exit(-1);
							}

							@Override
							public void registerSuccess() {
								
								cliKey = "" + System.currentTimeMillis();
								
								packetReceiver.addConsumer(packetConsumer, target, cliKey);
								
								if(runningFput){
									FputDetails fd = new FputDetails();
									fd.setCliKey(cliKey);
									fd.setFilename(fputDst);
									
									GCTService.fputStart.sendTo(new GCTService.fputStart.ResultReceiver(){

										@Override
										public void success(FputParams fputParams) {
											FileSender fSender = new FileSender(fputCompletionHandler,
													  							target,
													  							fputParams.getSvrKey(),
													  							fputSrc);
											fsenders.add(fSender);
											fSender.start();
											
										}
										
										@Override
										public void failure(FputException fpe) {
											System.err.println("Failed to start fput: " + fpe.getErrCode() + " '" + fpe.getErrText() + "'");
											System.exit(-1);
										}

										@Override
										public void failure(GolgiException ex) {
											System.err.println("Failed: " + ex.getErrText());
											System.exit(-1);
										}

										
									}, stdGto, target, fd);
									
								}
								else if(runningFget){
									FgetDetails fd = new FgetDetails();
									fd.setCliKey(cliKey);
									fd.setFilename(fgetSrc);
									
									GCTService.fget.sendTo(new GCTService.fget.ResultReceiver(){

										@Override
										public void success() {
										}

										@Override
										public void failure(GolgiException ex) {
											System.err.println("Failed: " + ex.getErrText());
											System.exit(-1);
										}
										
									}, stdGto, target, fd);
								}
								else{
									CommandDetails cd = new CommandDetails();
									cd.setCliKey(cliKey);
									cd.setCmdLine(cmd);
								
									GCTService.launchCommand.sendTo(new GCTService.launchCommand.ResultReceiver(){

										@Override
										public void success() {
										}

										@Override
										public void failure(GolgiException ex) {
											System.out.println("Failed: " + ex.getErrText());
											System.exit(-1);
										}
										
									}, stdGto, target, cd);
								}
								
							}
        });
        
        Timer hkTimer;
        hkTimer = new Timer();
        hkTimer.schedule(new TimerTask(){
            @Override
            public void run() {
            	if(timeOfDeath != 0){
            		long sinceDeath = System.currentTimeMillis() - timeOfDeath;
            		if(sinceDeath > 250){
            			if(exitErrString != null){
            				System.err.println(exitErrString);
            			}
            			System.exit(exitCode);
            		}
            	}
            }
        }, 250, 250);
        
    }
    

    private Client(String[] args){
    	boolean gathering = false;
        for(int i = 0; i < args.length; i++){
        	if(gathering){
        		if(cmd == null){
        			cmd = "";
        		}
        		else{
        			cmd = cmd + " ";
        		}
        		cmd += args[i];
        	}
        	else if(args[i].compareTo("-devKey") == 0){
        		devKey = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-appKey") == 0){
        		appKey = args[i+1];
        		i++;
        	}	
        	else if(args[i].compareTo("-identity") == 0){
        		identity = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-target") == 0){
        		target= args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-fgetSrc") == 0){
        		fgetSrc = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-fgetDst") == 0){
        		fgetDst = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-fputSrc") == 0){
        		fputSrc = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-fputDst") == 0){
        		fputDst = args[i+1];
        		i++;
        	}
        	else if(args[i].compareTo("-f") == 0){
        		force = true;
        	}
        	else if(args[i].compareTo("-v") == 0){
        		verbose = true;
        	}
        	else if(args[i].compareTo("--") == 0){
        		gathering = true;
        	}
        	else{
        		System.err.println("Zoikes, unrecognised option '" + args[i] + "'");
        		System.exit(-1);;
        	}
        }
        if(devKey == null){
        	System.err.println("No -devKey specified");
        	System.exit(-1);
        }
        else if(appKey == null){
        	System.err.println("No -appKey specified");
        	System.exit(-1);
        }
        else if(identity == null){
        	System.err.println("No -identity specified");
        	System.exit(-1);
        }
        else if(target == null){
        	System.err.println("No -target specified");
        	System.exit(-1);
        }
        else{
        	//
        	// Per role validation
        	//
        	
        	if(fputSrc != null || fputDst != null){
        		//
        		// Someone trying for fput
        		//
        		
        		runningFput = true;
        		
        		if(fputSrc == null){
        			System.err.println("No source file sepcified for fput");
        			System.exit(-1);
        		}
        		else if(fputDst == null){
        			System.err.println("No destination file sepcified for fput");
        			System.exit(-1);
        		}
        		
        		File f = new File(fputSrc);
        		if(!f.exists()){
        			System.err.println("Source file doesn't exist");
        			System.exit(-1);
        		}
        		else if(!f.isFile()){
        			System.err.println("Source isn't a regular file");
        			System.exit(-1);
        		}
        	}
        	else if(fgetSrc != null || fgetDst != null){
        		//
        		// Someone trying for fget
        		//
        		
        		runningFget = true;
        		
        		if(fgetSrc == null){
        			System.err.println("No source file sepcified for fget");
        			System.exit(-1);
        		}
        		else if(fgetDst == null){
        			System.err.println("No destination file sepcified for fget");
        			System.exit(-1);
        		}
        		
        		File f = new File(fgetDst);
        		if(f.exists()){
        			if(!force){
            			System.err.println("Destination already exists in some guise");
            			System.exit(-1);
        			}
        			if(!f.isFile()){
            			System.err.println("Destination already exists and is not a file!");
            			System.exit(-1);
        			}
        			if(!f.delete()){
            			System.err.println("Failed to delete destination first");
            			System.exit(-1);
        			}
        		}
        		try{
        			fgetFos = new FileOutputStream(f);
        		}
        		catch(FileNotFoundException fnfEx){
        			System.err.println("Cannot create destination file for writing");
        			System.exit(-1);
        		}
        		
        	}
        	else{
        		//
        		// Normal command operation
        		//
        		if(gathering == false){
        			System.err.println("No -- delimiter specified");
        			System.exit(-1);
        		}
        		else if(cmd == null){
        			System.err.println("No commands/args specified");
        			System.exit(-1);
        		}
        	}
        }
    }
        
    public static void main(String[] args) {
    	Logger.getLogger("com.openmindnetworks.slingshot.ntl.NTLClient").addAppender(new NullAppender());
    	(new Client(args)).looper();
    }

}
