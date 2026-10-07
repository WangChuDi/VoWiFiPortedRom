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
    private static final Set<String> SIP_KEYS=new HashSet<>(Arrays.asList("sip_connect_schema","sip_attempt","sip_attempt_started_elapsed","sip_stage","sip_stage_elapsed","sip_transport","sip_failure","sip_failure_stage","sip_failure_elapsed","sip_response_elapsed","sip_retry_due_elapsed"));
    private static final Set<String> SIP_STAGES=new HashSet<>(Arrays.asList("UNOBSERVED","WAITING_NETWORK","PLAIN_CONNECT","INITIAL_REGISTER","AKA","IPSEC_PARAMETERS","SECURE_CONNECT","AUTH_REGISTER","REGISTERED","DOWN","RETRY_WAIT","STOPPED"));
    private static final Set<String> SIP_FAILURES=new HashSet<>(Arrays.asList("NONE","TIMEOUT","NETWORK_IO","SIP_REJECTED","INVALID_RESPONSE","AKA_ERROR","PARAMETER_ERROR","UNAVAILABLE","CANCELED","OTHER"));
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
        for(Iterator<String> i=value.keys();i.hasNext();){String key=i.next();if(!BASE.contains(key)&&!TEXT.contains(key)&&!FLAGS.contains(key)&&!NUMBERS.contains(key)&&!SIP_KEYS.contains(key))throw new IllegalArgumentException("status-schema");}
        JSONObject clean=new JSONObject();for(String key:BASE)clean.put(key,value.get(key));
        if(!observed)return clean;
        for(String key:FLAGS)clean.put(key,flag(value,key));
        for(String key:NUMBERS){long n=number(value,key);if(n<0)throw new IllegalArgumentException("status-range");clean.put(key,n);}
        if(number(value,"generation")<1||number(value,"started_elapsed")>number(value,"updated_elapsed")||number(value,"updated_elapsed")>sample)throw new IllegalArgumentException("status-timeline");
        String phase=value.getString("phase"),failed=value.getString("failed_stage"),iface=value.getString("interface");
        if(!PHASES.contains(phase)||!PHASES.contains(failed)||!iface.matches("[A-Za-z0-9_.-]{0,48}"))throw new IllegalArgumentException("status-text");
        if(flag(value,"retired")&&(flag(value,"ike_open")||flag(value,"child_open")||flag(value,"registered")))throw new IllegalArgumentException("status-retired");
        if(flag(value,"registered")!=phase.equals("REGISTERED")||flag(value,"failed")!=phase.equals("FAILED")||flag(value,"retired")!=(phase.equals("CLOSED")||phase.equals("FAILED")))throw new IllegalArgumentException("status-lifecycle");
        clean.put("phase",phase).put("failed_stage",failed).put("interface",iface);
        boolean sip=false;for(String key:SIP_KEYS)if(value.has(key)){sip=true;break;}
        if(sip){
            if(!"ims".equals(channel)||number(value,"sip_connect_schema")!=1)throw new IllegalArgumentException("status-sip-schema");
            for(String key:SIP_KEYS)if(!value.has(key))throw new IllegalArgumentException("status-sip-incomplete");
            String stage=value.getString("sip_stage"),failure=value.getString("sip_failure"),failureStage=value.getString("sip_failure_stage"),transport=value.getString("sip_transport");
            if(!SIP_STAGES.contains(stage)||!SIP_STAGES.contains(failureStage)||!SIP_FAILURES.contains(failure)||!Arrays.asList("UNOBSERVED","TCP","UDP").contains(transport))throw new IllegalArgumentException("status-sip-text");
            long attempt=number(value,"sip_attempt"),start=number(value,"sip_attempt_started_elapsed"),at=number(value,"sip_stage_elapsed"),response=number(value,"sip_response_elapsed"),error=number(value,"sip_failure_elapsed"),due=number(value,"sip_retry_due_elapsed");
            if(attempt<0||start<0||response<0||error<0||due<0||at<number(value,"started_elapsed")||at>number(value,"updated_elapsed"))throw new IllegalArgumentException("status-sip-timeline");
            if(attempt==0){if(start!=0||response!=0||error!=0||due!=0||!"UNOBSERVED".equals(transport)||!Arrays.asList("UNOBSERVED","STOPPED").contains(stage))throw new IllegalArgumentException("status-sip-unobserved");}
            else if(start<number(value,"started_elapsed")||start>at||"UNOBSERVED".equals(transport)||"UNOBSERVED".equals(stage))throw new IllegalArgumentException("status-sip-attempt");
            if(response!=0&&(response<start||response>number(value,"updated_elapsed")||number(value,"sip_status")<100||number(value,"sip_status")>699))throw new IllegalArgumentException("status-sip-response");
            if("NONE".equals(failure)){if(error!=0||!"UNOBSERVED".equals(failureStage))throw new IllegalArgumentException("status-sip-failure");}
            else if(attempt==0||error<start||error>number(value,"updated_elapsed")||"UNOBSERVED".equals(failureStage))throw new IllegalArgumentException("status-sip-failure");
            if(due!=0&&(!"RETRY_WAIT".equals(stage)||due<at||due-at>60000))throw new IllegalArgumentException("status-sip-retry");
            if("RETRY_WAIT".equals(stage)&&due==0)throw new IllegalArgumentException("status-sip-retry");
            for(String key:SIP_KEYS)clean.put(key,value.get(key));
        }
        return clean;
    }
}
