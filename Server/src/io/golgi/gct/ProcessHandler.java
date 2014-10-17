package io.golgi.gct;

import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;
import io.golgi.gct.gen.TermStatus;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;

public class ProcessHandler {
	private Object syncObj = new Object();
	private GolgiTransportOptions stdGto;
	private Process p;
	private String owner;
	private String ownerKey;
	private Charset utf8Charset = Charset.forName("UTF-8");
	private int exitCode = 0;
	private int pktNum = 0;
	private boolean verbose = false;
	private boolean processDead = false;
	private boolean stdOutClosed = false;
	private boolean stdErrClosed = false;
	
	private void maybeFinished(){
		synchronized(syncObj){
			if(processDead && stdOutClosed && stdErrClosed){
				if(verbose) System.out.println("We are finished");
				TermStatus s = new TermStatus();
				s.setLclKey(ownerKey);
				s.setPktTotal(pktNum);
				s.setExitCode(exitCode);
				GCTService.commandComplete.sendTo(new GCTService.commandComplete.ResultReceiver(){

					@Override
					public void success() {
					}

					@Override
					public void failure(GolgiException ex) {
						System.out.println("Error sending commandComplete: " + ex.getErrText());
						
					}
				},stdGto, owner, s);
				
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
						String str = new String(buf, 0, len, utf8Charset);
						if(verbose) System.out.println("[" + name + "]:" + str);
						OutputPacket pkt = new OutputPacket();
						pkt.setLclKey(ownerKey);
						pkt.setFd(fdNumForPkt);
						synchronized(syncObj){
							pkt.setPktNum(++pktNum);
						}
						pkt.setData(str);
						GCTService.commandOutput.sendTo(new GCTService.commandOutput.ResultReceiver(){

							@Override
							public void success() {
							}

							@Override
							public void failure(GolgiException ex) {
								System.out.println("ERROR for '" + name + "' packet");
							}
							
						}, stdGto, owner, pkt);
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
			System.out.println("Process complete: " + exitCode);
			synchronized(syncObj){
				processDead = true;
			}
			maybeFinished();
		}
	}

	ProcessHandler(Process p, String owner, String ownerKey, boolean verbose){
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);

		this.owner = owner;
		this.ownerKey = ownerKey;
		this.verbose = verbose;
		this.p = p;
		new StdOutHandler().start();
		new StdErrHandler().start();
		new StatusHandler().start();
	}
}
