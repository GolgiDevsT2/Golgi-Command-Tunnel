package io.golgi.gct;

import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.TermStatus;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;

public class ProcessHandler implements PacketTransmitter.ActivityWatcher {
	public interface CompletionHandler{
		public void processComplete(ProcessHandler p, int exitCode);
	}
	private static final int MAX_INFLIGHT = 5;
	private Object syncObj = new Object();
	private GolgiTransportOptions stdGto;
	private Process p;
	private String owner;
	private String ownerKey;
	private int exitCode = 0;
	private int pktNum = 0;
	private boolean verbose = false;
	private boolean processDead = false;
	private boolean stdOutClosed = false;
	private boolean stdErrClosed = false;
	private CompletionHandler completionHandler;
	private String cmdLine = null;
	private boolean termStatusSent = false;
	
	
	private PacketTransmitter packetTransmitter;
	
	@Override
	public void transmitterActivity(){
		maybeFinished();
	}

	public String getCmdLine(){
		return cmdLine;
	}
	
	private void maybeFinished(){
		synchronized(syncObj){
			if(processDead && stdOutClosed && stdErrClosed && packetTransmitter.queueSize() == 0 && !termStatusSent){
				if(verbose) System.out.println("We are finished");
				TermStatus s = new TermStatus();
				s.setKey(ownerKey);
				s.setPktTotal(pktNum);
				s.setExitCode(exitCode);
				s.setErrorText("");
				GCTService.commandComplete.sendTo(new GCTService.commandComplete.ResultReceiver(){
					@Override
					public void success() {
					}

					@Override
					public void failure(GolgiException ex) {
						System.out.println("Error sending commandComplete: " + ex.getErrText());
						
					}
				},stdGto, owner, s);
				
				completionHandler.processComplete(this, exitCode);
				termStatusSent = true;
			}
		}
	}
	
	abstract class StreamHandler extends Thread{
		abstract void streamClosed();
		private String name;
		InputStream iStream;
		int fdNumForPkt;
		
		public void run(){
			boolean eof = false;
			if(verbose) System.out.println("Hello fromn '" + name + "'");
			byte[] buf = new byte[10240*128];
			BufferedInputStream bs = new BufferedInputStream(iStream);
			try{
				while(!eof){
					int len = bs.read(buf);
					if(len > 0){
						byte[] data = new byte[len];
						System.arraycopy(buf, 0, data, 0, len);
						OutputPacket pkt = new OutputPacket();
						pkt.setDstKey(ownerKey);
						pkt.setFd(fdNumForPkt);
						pkt.setData(data);
						synchronized(syncObj){
							pkt.setPktNum(++pktNum);
						}
						packetTransmitter.sendPacket(pkt);
					}
					else if(len < 0){
						eof = true;
					}
				}
			}
			catch(IOException ioex){
				System.out.println(name + ": IOEX: " + ioex.getMessage());
			}
			if(verbose) System.out.println(name + ": END");
			streamClosed();
			
		}
		StreamHandler(String name, InputStream iStream, int fdNumForPkt){
			this.name = name;
			this.iStream = iStream;
			this.fdNumForPkt = fdNumForPkt;
		}
	}
	
	class StdOutHandler extends StreamHandler{
		void streamClosed(){
			synchronized(syncObj){
				stdOutClosed = true;
			}
			maybeFinished();
		}
		StdOutHandler(){
			super("STDOUT", p.getInputStream(), 1);
		}
	}
	
	class StdErrHandler extends StreamHandler{
		void streamClosed(){
			synchronized(syncObj){
				stdErrClosed = true;
			}
			maybeFinished();
		}
		StdErrHandler(){
			super("STDERR", p.getErrorStream(), 2);
		}
	}
	
	class StatusHandler extends Thread{
		public void run(){
			boolean dead = false;
			while(!dead){
				try{
					exitCode = p.exitValue();
					dead = true;
				}
				catch(IllegalThreadStateException ise){
				}
				if(!dead){
					try{
						p.waitFor();
					}
					catch(InterruptedException iex){
					}
				}
			}
			// System.out.println("Process complete: " + exitCode);
			synchronized(syncObj){
				processDead = true;
			}
			maybeFinished();
		}
	}
	
	
	public void startCommand(String cmdLine) throws Exception{
		ProcessBuilder pb = new ProcessBuilder();
		ArrayList<String> list = new ArrayList<String>();
		this.cmdLine = cmdLine;
		StringTokenizer stk = new StringTokenizer(cmdLine);
		while(stk.hasMoreTokens()){
			String arg = stk.nextToken();
			// System.out.println("Adding arg: " + arg);
			list.add(arg);
		}
		
		pb.command(list);

		this.p = pb.start();
		new StdOutHandler().start();
		new StdErrHandler().start();
		new StatusHandler().start();
	}

	ProcessHandler(CompletionHandler completionHandler, String owner, String ownerKey, boolean verbose){
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);
        
        packetTransmitter = new PacketTransmitter(this, owner);

        this.completionHandler = completionHandler;
		this.owner = owner;
		this.ownerKey = ownerKey;
		this.verbose = verbose;
	}
}
