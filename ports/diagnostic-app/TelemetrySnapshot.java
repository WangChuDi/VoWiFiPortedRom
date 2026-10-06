// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import org.json.JSONObject;
import java.util.*;

/** Reject stale/wrong-owner status; copy only the fixed, non-sensitive schema. */
public final class TelemetrySnapshot {
    private static final Set<String> BASE=new HashSet<>(Arrays.asList("schema","channel","slot","sub","nonce","pid","sample_elapsed","boot","authorized","observed"));
    private static final Set<String> TEXT=new HashSet<>(Arrays.asList("phase","failed_stage","interface"));
    private static final Set<String> FLAGS=new HashSet<>(Arrays.asList("failed","retired","ike_open","child_open","registered"));
    private static final Set<String> NUMBERS=new HashSet<>(Arrays.asList("generation","started_elapsed","updated_elapsed","inbound_transforms","outbound_transforms","address_count","dns_count","pcscf_count","sip_status","epdg_dns_count","register_tx","sms_rx","sms_ack_ok","sms_ack_failed","sms_tx","sms_tx_ok","sms_tx_failed","voice_tx_frames","voice_played_frames"));
    private static final Set<String> PHASES=new HashSet<>(Arrays.asList("STARTING","WAITING_SIM","WAITING_NETWORK","DNS","IKE_PARAMETERS","IKE_NEGOTIATING","IKE_AUTHENTICATED","CHILD_OPENED","IWLAN_SELECTED","NO_IWLAN","REGISTERING","REGISTERED","DOWN","FAILED","CLOSED"));
    private static long number(JSONObject value,String key)throws Exception{
        Object n=value.get(key);if(!(n instanceof Number))throw new IllegalArgumentException("status-number");
        long result=((Number)n).longValue();if(((Number)n).doubleValue()!=result)throw new IllegalArgumentException("status-integer");return result;
    }
    private static boolean flag(JSONObject value,String key)throws Exception{
        Object b=value.get(key);if(!(b instanceof Boolean))throw new IllegalArgumentException("status-flag");return (Boolean)b;
    }
    public static JSONObject validate(JSONObject value,String channel,int slot,int sub,String nonce,int pid,int boot,long began,long now)throws Exception{
        if(number(value,"schema")!=1||!channel.equals(value.getString("channel"))||number(value,"slot")!=slot||number(value,"sub")!=sub||!nonce.equals(value.getString("nonce"))||number(value,"pid")!=pid||number(value,"boot")!=boot||boot<0)
            throw new IllegalArgumentException("status-provenance");
        long sample=number(value,"sample_elapsed");if(sample<began||sample>now||now-sample>5000)throw new IllegalArgumentException("status-stale");
        boolean observed=flag(value,"observed"),authorized=flag(value,"authorized");
        if(observed&&!authorized)throw new IllegalArgumentException("status-unauthorized");
        for(Iterator<String> i=value.keys();i.hasNext();){String key=i.next();if(!BASE.contains(key)&&!TEXT.contains(key)&&!FLAGS.contains(key)&&!NUMBERS.contains(key))throw new IllegalArgumentException("status-schema");}
        JSONObject clean=new JSONObject();for(String key:BASE)clean.put(key,value.get(key));
        if(!observed)return clean;
        for(String key:FLAGS)clean.put(key,flag(value,key));
        for(String key:NUMBERS){long n=number(value,key);if(n<0)throw new IllegalArgumentException("status-range");clean.put(key,n);}
        if(number(value,"generation")<1||number(value,"started_elapsed")>number(value,"updated_elapsed")||number(value,"updated_elapsed")>sample)throw new IllegalArgumentException("status-timeline");
        String phase=value.getString("phase"),failed=value.getString("failed_stage"),iface=value.getString("interface");
        if(!PHASES.contains(phase)||!PHASES.contains(failed)||!iface.matches("[A-Za-z0-9_.-]{0,48}"))throw new IllegalArgumentException("status-text");
        if(flag(value,"retired")&&(flag(value,"ike_open")||flag(value,"child_open")||flag(value,"registered")))throw new IllegalArgumentException("status-retired");
        if(flag(value,"registered")!=phase.equals("REGISTERED")||flag(value,"failed")!=phase.equals("FAILED")||flag(value,"retired")!=(phase.equals("CLOSED")||phase.equals("FAILED")))throw new IllegalArgumentException("status-lifecycle");
        clean.put("phase",phase).put("failed_stage",failed).put("interface",iface);return clean;
    }
}
