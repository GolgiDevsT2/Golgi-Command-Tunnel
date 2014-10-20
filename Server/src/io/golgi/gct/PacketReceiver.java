package io.golgi.gct;


import java.util.Hashtable;

import io.golgi.gct.gen.GCTService;
import io.golgi.gct.gen.OutputPacket;

public class PacketReceiver implements GCTService.moreData.RequestReceiver{
	public interface Consumer{
		public void inboundPacket(OutputPacket pkt);
		public void receiverActivity();
	}
	
	private class PacketAligner{
		private Object syncObj = new Object();
		private Hashtable<String,OutputPacket> hash = new Hashtable<String,OutputPacket>();
		private int pktCount = 0;
		Consumer consumer;
		
	    private void drainPackets(){
	    	String key;
	    	
	    	while(true){
	    		key = "" + (pktCount + 1);
	    		synchronized(syncObj){
	    			if(!hash.containsKey(key)){
	    				break;
	    			}
	    			OutputPacket pkt = hash.remove(key);
	    			consumer.inboundPacket(pkt);
	    			pktCount++;
	    		}
	    	}
	    	consumer.receiverActivity();
	    }
		
	    private void inboundPacket(OutputPacket pkt){
	    	if(pkt.getPktNum() == (pktCount + 1)){
	    		consumer.inboundPacket(pkt);
	    		synchronized(syncObj){
	    			pktCount++;
	    		}
	    	}
	    	else{
	    		synchronized(syncObj){
	    			hash.put("" + pkt.getPktNum(),  pkt);
	    		}
	    	}
	    	drainPackets();
	    }
		
		PacketAligner(Consumer consumer){
			this.consumer = consumer;
		}
	}
	
	private Object syncObj = new Object();
    private Hashtable<String,PacketAligner> alignerHash = new Hashtable<String,PacketAligner>();
	
	
	@Override
	public void receiveFrom(GCTService.moreData.ResultSender resultSender, OutputPacket pkt) {
		String key = resultSender.getRequestSenderId() + ":" + pkt.getDstKey();
		
		PacketAligner pa = null;
		synchronized(syncObj){
			pa = alignerHash.get(key); 
		}
		
		// System.out.println("Packet received for: " + key);
		
		if(pa != null){
			pa.inboundPacket(pkt);
		}
		
		resultSender.success();
	}
	
	public void removeConsumer(String expectedSender, String expectedKey){
		String key = expectedSender + ":" + expectedKey;
		
		synchronized(syncObj){
			alignerHash.remove(key);
		}
		
	}
	
	public void addConsumer(Consumer consumer, String expectedSender, String expectedKey){
		String key = expectedSender + ":" + expectedKey;
		// System.out.println("Adding Consumer: " + key);
		synchronized(syncObj){
			alignerHash.put(key, new PacketAligner(consumer));
		}
	}
	
	public PacketReceiver(){
	}
}
