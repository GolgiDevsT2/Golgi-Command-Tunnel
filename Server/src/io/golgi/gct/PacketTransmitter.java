package io.golgi.gct;

import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;

import java.util.Vector;

import com.openmindnetworks.golgi.api.GolgiException;
import com.openmindnetworks.golgi.api.GolgiTransportOptions;

public class PacketTransmitter {
	public interface ActivityWatcher{
		public void transmitterActivity();
	}
	
	private static final int MAX_INFLIGHT = 5;
	private Object syncObj = new Object();
	private GolgiTransportOptions stdGto;
	private String target;
	private ActivityWatcher watcher;

	private Vector<OutputPacket> pktQueue = new Vector<OutputPacket>();
	private int pktsInFlight = 0;
	
	private void nextPacket(){
		synchronized(syncObj){
			pktsInFlight--;
			while(pktQueue.size() > 0 && pktsInFlight < MAX_INFLIGHT){
				pktsInFlight++;
				OutputPacket nextPkt = pktQueue.remove(0);
				sendPacketWorker(nextPkt);
				// System.out.print("Queue: " + pktQueue.size() + "\r");
				// System.out.flush();
			}
		}
		watcher.transmitterActivity();
	}
	
	public int queueSize(){
		synchronized(syncObj){
			return pktQueue.size();
		}
	}
	
	private void sendPacketWorker(OutputPacket pkt){
		// System.out.println("sendPacketWorker()");
		GCTService.moreData.sendTo(new GCTService.moreData.ResultReceiver(){
			@Override
			public void success() {
				// System.out.println("Success: " + pktQueue.size());
				nextPacket();
			}
			@Override
			public void failure(GolgiException ex) {
				System.out.println("ERROR sending output packet");
				nextPacket();
			}
		}, stdGto, target, pkt);
		
	}
	
	public void sendPacket(OutputPacket pkt){
		synchronized(syncObj){
			if(pktQueue.size() > 0 || pktsInFlight >= MAX_INFLIGHT){
				pktQueue.add(pkt);
			}
			else{
				pktsInFlight++;
				sendPacketWorker(pkt);
				watcher.transmitterActivity();
			}
		}
	}
	
	public PacketTransmitter(ActivityWatcher watcher, String target){
        stdGto = new GolgiTransportOptions();
        stdGto.setValidityPeriod(60);
        this.watcher = watcher;
        this.target = target;
	}
}
