package io.golgi.gct;

import io.golgi.gct.gen.CommandDetails;
import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.GCTService.launchCommand.ResultSender;
import io.golgi.gct.gen.GCTService.*;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;

import com.openmindnetworks.golgi.JavaType;
import com.openmindnetworks.golgi.api.GolgiAPI;
import com.openmindnetworks.golgi.api.GolgiAPIHandler;
import com.openmindnetworks.golgi.api.GolgiAPIImpl;
import com.openmindnetworks.golgi.api.GolgiAPINetworkImpl;
import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;
import com.openmindnetworks.slingshot.ntl.NTL;
import com.openmindnetworks.slingshot.tbx.TBX;

public class Server {
	private boolean verbose = false;
    private String devKey = null;
    private String appKey = null;
    private String identity;
    private GolgiTransportOptions stdGto;
    
    private Vector<ProcessHandler> pHandlers = new Vector<ProcessHandler>();
    
    private GCTService.launchCommand.RequestReceiver inboundLaunchCommand = new GCTService.launchCommand.RequestReceiver(){

		@Override
		public void receiveFrom(ResultSender resultSender,
				CommandDetails cmdDetails) {
			System.out.println("Asked to launch '" + cmdDetails.getCmdLine() + "' by '" + resultSender.getRequestSenderId() + "' key '" + cmdDetails.getLclKey() + "'");
			ProcessBuilder pb = new ProcessBuilder();
			ArrayList<String> list = new ArrayList<String>();
			StringTokenizer stk = new StringTokenizer(cmdDetails.getCmdLine());
			while(stk.hasMoreTokens()){
				String arg = stk.nextToken();
				System.out.println("Adding arg: " + arg);
				list.add(arg);
			}
			
			pb.command(list);

			try{
				Process p = pb.start();
				pHandlers.add(new ProcessHandler(p, resultSender.getRequestSenderId(), cmdDetails.getLclKey(), verbose));
			}
			catch(Exception e){
				System.out.println("Zoikes, exploded: " + e.toString() + e.getMessage());
				e.printStackTrace();
			}
			resultSender.success();

		}
    };
    
    private void looper(){
        Class<GolgiAPI> apiRef = GolgiAPI.class;
        GolgiAPINetworkImpl impl = new GolgiAPINetworkImpl();
        GolgiAPI.setAPIImpl(impl);
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);

        GCTService.launchCommand.registerReceiver(inboundLaunchCommand);
        

        // startStreaming.registerReceiver(inboundStartStreaming);
        // stopStreaming.registerReceiver(inboundStopStreaming);

        // WhozinService.registerDevice.registerReceiver(registerDeviceSS);
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
								System.out.println("Ready, registered as '" + identity + "'");
							}
        });
        
        Timer hkTimer;
        hkTimer = new Timer();
        hkTimer.schedule(new TimerTask(){
            @Override
            public void run() {
                // houseKeep();
            }
        }, 5000, 10000);
        
    }
    

    private Server(String[] args){
        for(int i = 0; i < args.length; i++){
        	if(args[i].compareTo("-devKey") == 0){
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
        	else if(args[i].compareTo("-v") == 0){
        		verbose = true;
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
    }
        
    public static void main(String[] args) {
    	Logger.getLogger("com.openmindnetworks.slingshot.ntl.NTLClient").addAppender(new NullAppender());
        (new Server(args)).looper();
    }
}
