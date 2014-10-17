package io.golgi.gct;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;

import io.golgi.gct.gen.CommandDetails;
import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.GCTService.commandComplete.ResultSender;
import io.golgi.gct.gen.TermStatus;

import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import com.openmindnetworks.golgi.JavaType;
import com.openmindnetworks.golgi.api.GolgiAPI;
import com.openmindnetworks.golgi.api.GolgiAPIHandler;
import com.openmindnetworks.golgi.api.GolgiAPIImpl;
import com.openmindnetworks.golgi.api.GolgiAPINetworkImpl;
import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;
import com.openmindnetworks.slingshot.ntl.NTL;
import com.openmindnetworks.slingshot.tbx.TBX;


public class Client {
	private Object syncObj = new Object();
	private long timeOfDeath = 0;
	private String lclKey = null;
    private String devKey = null;
    private String appKey = null;
    private String identity = null;
    private String target = null;
    private String cmd = null;
    private GolgiTransportOptions stdGto;
    private boolean complete;
    private int pktTarget = 0;
    private int pktCount = 0;
    private int exitCode = 0;
    private boolean verbose = false;
    private Hashtable<String,OutputPacket> pktHash = new Hashtable<String,OutputPacket>();
    
    void maybeFinished(){
    	synchronized(syncObj){
    		if(verbose) System.out.println("maybeFinished: " + complete + "/" + pktCount + "/" + pktTarget);
    		if(complete && pktCount == pktTarget){
    			timeOfDeath = System.currentTimeMillis();
    		}
    	}
    }
    
    void printPacket(OutputPacket pkt){
    	if(pkt.getFd() == 1){
    		System.out.print(pkt.getData());
    	}
    	else{
    		System.err.print(pkt.getData());
    	}
    }
    
    void drainPackets(){
    	String key;
    	
    	while(true){
    		key = "" + (pktCount + 1);
    		synchronized(syncObj){
    			if(!pktHash.containsKey(key)){
    				break;
    			}
    			OutputPacket pkt = pktHash.remove(key);
    			printPacket(pkt);
    			pktCount++;
    		}
    	}
    	maybeFinished();
    }
    
    private GCTService.commandComplete.RequestReceiver inboundCommandComplete = new GCTService.commandComplete.RequestReceiver(){

		@Override
		public void receiveFrom(GCTService.commandComplete.ResultSender resultSender, TermStatus termStatus) {
			if(lclKey != null && termStatus.getLclKey().compareTo(lclKey) == 0){
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
    

    private GCTService.commandOutput.RequestReceiver inboundCommandOutput = new GCTService.commandOutput.RequestReceiver() {

		@Override
		public void receiveFrom(GCTService.commandOutput.ResultSender resultSender, OutputPacket pkt) {
			if(lclKey != null && pkt.getLclKey().compareTo(lclKey) == 0){
				if(pkt.getPktNum() == (pktCount + 1)){
					printPacket(pkt);
					synchronized(syncObj){
						pktCount++;
					}
				}
				else{
					synchronized(syncObj){
						pktHash.put("" + pkt.getPktNum(),  pkt);
					}
				}
				drainPackets();
			}
		}
	};
    
    private void looper(){
        Class<GolgiAPI> apiRef = GolgiAPI.class;
        GolgiAPINetworkImpl impl = new GolgiAPINetworkImpl();
        GolgiAPI.setAPIImpl(impl);
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);
        
        GCTService.commandOutput.registerReceiver(inboundCommandOutput);
        GCTService.commandComplete.registerReceiver(inboundCommandComplete);

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
								CommandDetails cd = new CommandDetails();
								
								lclKey = "" + System.currentTimeMillis();
								
								cd.setLclKey(lclKey);
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
        });
        
        Timer hkTimer;
        hkTimer = new Timer();
        hkTimer.schedule(new TimerTask(){
            @Override
            public void run() {
            	if(timeOfDeath != 0){
            		long sinceDeath = System.currentTimeMillis() - timeOfDeath;
            		if(sinceDeath > 250){
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
        	System.out.println("No -devKey specified");
        	System.exit(-1);
        }
        else if(appKey == null){
        	System.out.println("No -appKey specified");
        	System.exit(-1);
        }
        else if(identity == null){
        	System.out.println("No -identity specified");
        	System.exit(-1);
        }
        else if(target == null){
        	System.out.println("No -target specified");
        	System.exit(-1);
        }
        else if(gathering == false){
        	System.out.println("No -- delimiter specified");
        	System.exit(-1);
        }
        else if(cmd == null){
        	System.out.println("No commands/args specified");
        	System.exit(-1);
        }
    }
        
    public static void main(String[] args) {
    	Logger.getLogger("com.openmindnetworks.slingshot.ntl.NTLClient").addAppender(new NullAppender());
    	(new Client(args)).looper();
    }

}
