// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.*;
import java.util.function.LongSupplier;

/** Process-local status only: no identities, endpoints, PDU, headers or free text. */
public final class StackTelemetry {
    public enum Phase {STARTING,WAITING_SIM,WAITING_NETWORK,DNS,IKE_PARAMETERS,IKE_NEGOTIATING,IKE_AUTHENTICATED,CHILD_OPENED,IWLAN_SELECTED,NO_IWLAN,REGISTERING,REGISTERED,DOWN,FAILED,CLOSED}
    public enum Counter {REGISTER_TX,SMS_RX,SMS_ACK_OK,SMS_ACK_FAILED,SMS_TX,SMS_TX_OK,SMS_TX_FAILED,VOICE_TX_FRAMES,VOICE_PLAYED_FRAMES}
    private static final Map<String,Owner> owners=new HashMap<>();
    private static long sequence;
    private StackTelemetry(){}
    private static String key(String channel,int slot){
        if(!Arrays.asList("iwlan","qns","ims").contains(channel)||slot<0||slot>7)throw new IllegalArgumentException("status-selection");
        return channel+":"+slot;
    }
    public static synchronized Owner begin(String channel,int slot,int sub,LongSupplier clock){
        if(sub<0||clock==null)throw new IllegalArgumentException("status-owner");
        Owner owner=new Owner(key(channel,slot),channel,slot,sub,++sequence,clock);
        owners.put(owner.key,owner);return owner;
    }
    public static synchronized Map<String,Object> snapshot(String channel,int slot,int sub){
        Owner owner=owners.get(key(channel,slot));
        return owner==null||owner.sub!=sub?null:owner.copy();
    }
    public static final class Owner {
        private final String key,channel;
        private final int slot,sub;
        private final long generation,started;
        private final LongSupplier clock;
        private long updated;
        private Phase phase=Phase.STARTING,failedStage=Phase.STARTING;
        private boolean retired,failed,ike,child,registered;
        private String iface="";
        private int inbound,outbound,addresses,dns,pcscf,sipStatus,epdgDns;
        private final EnumMap<Counter,Long> counts=new EnumMap<>(Counter.class);
        private Owner(String key,String channel,int slot,int sub,long generation,LongSupplier clock){
            this.key=key;this.channel=channel;this.slot=slot;this.sub=sub;this.generation=generation;this.clock=clock;
            started=updated=clock.getAsLong();for(Counter c:Counter.values())counts.put(c,0L);
        }
        private boolean live(){return !retired&&owners.get(key)==this;}
        private void touched(){updated=clock.getAsLong();}
        public void phase(Phase value){
            if(value==null||value==Phase.CLOSED||value==Phase.FAILED)throw new IllegalArgumentException("status-phase");
            synchronized(StackTelemetry.class){if(live()){phase=value;registered=value==Phase.REGISTERED;touched();}}
        }
        public void ikeOpened(){synchronized(StackTelemetry.class){if(live()){ike=true;phase=child?Phase.CHILD_OPENED:Phase.IKE_AUTHENTICATED;touched();}}}
        public void epdgResolved(int count){synchronized(StackTelemetry.class){if(live()){epdgDns=Math.max(0,count);touched();}}}
        public void transform(int direction,int change){synchronized(StackTelemetry.class){if(live()){
            if(direction==0)inbound=Math.max(0,inbound+change);else if(direction==1)outbound=Math.max(0,outbound+change);touched();
        }}}
        public void childOpened(String name,int addressCount,int dnsCount,int pcscfCount){synchronized(StackTelemetry.class){if(live()){
            iface=name!=null&&name.matches("[A-Za-z0-9_.-]{1,48}")?name:"";
            addresses=Math.max(0,addressCount);dns=Math.max(0,dnsCount);pcscf=Math.max(0,pcscfCount);
            child=true;phase=Phase.CHILD_OPENED;touched();
        }}}
        public void sipResponse(int code){synchronized(StackTelemetry.class){if(live()){sipStatus=code>=100&&code<=699?code:0;touched();}}}
        public void add(Counter counter,int value){synchronized(StackTelemetry.class){if(live()&&value>0){counts.put(counter,Math.min(Integer.MAX_VALUE,counts.get(counter)+value));touched();}}}
        public void end(boolean error){synchronized(StackTelemetry.class){if(live()){
            failedStage=phase;phase=error?Phase.FAILED:Phase.CLOSED;failed=error;retired=true;
            ike=child=registered=false;inbound=outbound=0;iface="";touched();
        }}}
        private Map<String,Object> copy(){
            Map<String,Object> data=new LinkedHashMap<>();
            data.put("channel",channel);data.put("slot",slot);data.put("sub",sub);data.put("generation",generation);
            data.put("started_elapsed",started);data.put("updated_elapsed",updated);data.put("phase",phase.name());
            data.put("failed",failed);data.put("failed_stage",failedStage.name());data.put("retired",retired);
            data.put("ike_open",ike);data.put("child_open",child);data.put("registered",registered);
            data.put("inbound_transforms",inbound);data.put("outbound_transforms",outbound);
            data.put("interface",iface);data.put("address_count",addresses);data.put("dns_count",dns);data.put("pcscf_count",pcscf);
            data.put("sip_status",sipStatus);
            data.put("epdg_dns_count",epdgDns);
            for(Counter c:Counter.values())data.put(c.name().toLowerCase(Locale.ROOT),counts.get(c));
            return Collections.unmodifiableMap(data);
        }
    }
}
