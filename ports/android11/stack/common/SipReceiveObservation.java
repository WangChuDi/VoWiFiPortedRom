// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** One captured receive attempt; closed enums/counters, never packets or identities. */
public final class SipReceiveObservation {
    public enum Channel {MAIN,TCP,UDP}
    public enum State {UNOBSERVED,RUNNING,ENDED,FAILED}
    public enum Rp {DATA,ACK,ERROR,OTHER,DECODE_ERROR}
    private enum Count {MESSAGES_MAIN,MESSAGES_TCP,MESSAGES_UDP,MESSAGE_REQUESTS,UDP_DATAGRAMS,TCP_ACCEPTED,RP_DATA,RP_ACK,RP_ERROR,RP_OTHER,RP_DECODE_ERROR,RP_UNMATCHED}
    private final long attempt;
    private final LongSupplier clock;
    private long updated,lastMessage;
    private boolean retired;
    private int activePeers;
    private final EnumMap<Channel,State> states=new EnumMap<>(Channel.class);
    private final EnumMap<Count,Long> counts=new EnumMap<>(Count.class);
    public SipReceiveObservation(long attempt,LongSupplier clock){
        if(attempt<1||clock==null)throw new IllegalArgumentException("receive-owner");
        this.attempt=attempt;this.clock=clock;updated=clock.getAsLong();
        for(Channel c:Channel.values())states.put(c,State.UNOBSERVED);
        for(Count c:Count.values())counts.put(c,0L);
    }
    public long attempt(){return attempt;}
    private void touch(){updated=clock.getAsLong();}
    private void add(Count c){counts.put(c,Math.min(Integer.MAX_VALUE,counts.get(c)+1));touch();}
    public synchronized void loop(Channel channel,State state){
        if(channel==null||state==null||state==State.UNOBSERVED)throw new IllegalArgumentException("receive-state");
        if(retired)return;
        State previous=states.get(channel);
        if(previous==State.FAILED||previous==State.ENDED)return;
        states.put(channel,state);touch();
    }
    public synchronized void parsed(Channel channel,boolean messageRequest){
        if(channel==null)throw new IllegalArgumentException("receive-channel");
        if(retired)return;
        add(channel==Channel.MAIN?Count.MESSAGES_MAIN:channel==Channel.TCP?Count.MESSAGES_TCP:Count.MESSAGES_UDP);
        lastMessage=updated;if(messageRequest)add(Count.MESSAGE_REQUESTS);
    }
    public synchronized void datagram(){if(!retired)add(Count.UDP_DATAGRAMS);}
    public synchronized void rp(Rp type){
        if(type==null)throw new IllegalArgumentException("receive-rp");if(retired)return;
        switch(type){case DATA:add(Count.RP_DATA);break;case ACK:add(Count.RP_ACK);break;case ERROR:add(Count.RP_ERROR);break;case OTHER:add(Count.RP_OTHER);break;case DECODE_ERROR:add(Count.RP_DECODE_ERROR);break;}
    }
    public synchronized void unmatched(){if(!retired)add(Count.RP_UNMATCHED);}
    public synchronized Peer accepted(){
        if(retired)return new Peer(false);
        if(activePeers>=4)throw new IllegalStateException("receive-peer-limit");
        activePeers++;add(Count.TCP_ACCEPTED);return new Peer(true);
    }
    public final class Peer implements AutoCloseable {
        private final boolean retained;private final AtomicBoolean closed=new AtomicBoolean();
        private Peer(boolean retained){this.retained=retained;}
        public void close(){if(!retained||!closed.compareAndSet(false,true))return;synchronized(SipReceiveObservation.this){if(!retired){activePeers--;touch();}}}
    }
    public synchronized void retire(){
        if(retired)return;
        for(Channel c:Channel.values())if(states.get(c)==State.RUNNING)states.put(c,State.ENDED);
        activePeers=0;retired=true;touch();
    }
    public synchronized Map<String,Object> snapshot(){
        Map<String,Object> data=new LinkedHashMap<>();data.put("schema",1);data.put("attempt",attempt);
        data.put("updated_elapsed",updated);data.put("last_message_elapsed",lastMessage);data.put("retired",retired);data.put("tcp_active",activePeers);
        for(Channel c:Channel.values())data.put(c.name().toLowerCase(Locale.ROOT),states.get(c).name());
        for(Count c:Count.values())data.put(c.name().toLowerCase(Locale.ROOT),counts.get(c));return data;
    }
    public static Map<String,Object> unobserved(){
        Map<String,Object> data=new LinkedHashMap<>();data.put("schema",1);data.put("attempt",0);
        data.put("updated_elapsed",0);data.put("last_message_elapsed",0);data.put("retired",false);data.put("tcp_active",0);
        for(Channel c:Channel.values())data.put(c.name().toLowerCase(Locale.ROOT),State.UNOBSERVED.name());
        for(Count c:Count.values())data.put(c.name().toLowerCase(Locale.ROOT),0L);return data;
    }
}
