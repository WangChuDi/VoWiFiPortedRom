// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** One submission's protocol metadata. This never decides or retries delivery. */
public final class SmsSendObservation {
    public enum Failure {NONE,FORMAT_UNSUPPORTED,NOT_READY,NO_SMSC,REF_IN_USE,SIP_REJECTED,RP_REJECTED,TRANSPORT_IO,SIP_TIMEOUT,RP_TIMEOUT,FRAMEWORK_ERROR,OTHER}
    private final long attempt,started;
    private final LongSupplier clock;
    private long updated;
    private String state="PREPARING",rp="UNOBSERVED";
    private Failure failure=Failure.NONE;
    private int sip,cause=-1;
    private boolean finished;
    public SmsSendObservation(long attempt,LongSupplier clock){
        if(attempt<1||clock==null)throw new IllegalArgumentException("sms-observation-owner");
        this.attempt=attempt;this.clock=clock;started=updated=clock.getAsLong();
    }
    private void touch(){updated=Math.max(updated,clock.getAsLong());}
    public synchronized void submitted(){if(!finished){state="WAITING_NETWORK_ACK";touch();}}
    public synchronized void onSip(int code){
        if(finished||code<100||code>699)return;
        // A late provisional response cannot erase a final acceptance.
        if(sip>=200&&code<200)return;
        sip=code;
        if(code>=200&&code!=200&&code!=202)failure=Failure.SIP_REJECTED;
        touch();
    }
    public synchronized void onRp(boolean accepted,int rpCause){
        if(finished)return;
        rp=accepted?"ACCEPTED":"REJECTED";
        cause=!accepted&&rpCause>=0&&rpCause<=255?rpCause:-1;
        if(!accepted)failure=Failure.RP_REJECTED;
        touch();
    }
    public synchronized void fail(Failure value){
        if(value==null||value==Failure.NONE)throw new IllegalArgumentException("sms-observation-failure");
        if(!finished){failure=value;touch();}
    }
    public synchronized void timeout(){
        if(!finished){
            if(failure==Failure.NONE)failure=(sip==200||sip==202)&&!"ACCEPTED".equals(rp)?Failure.RP_TIMEOUT:Failure.SIP_TIMEOUT;
            touch();
        }
    }
    public synchronized void finish(boolean success){
        if(finished)return;
        finished=true;state=success?"ACCEPTED":"FAILED";
        if(success)failure=Failure.NONE;else if(failure==Failure.NONE)failure=Failure.OTHER;
        touch();
    }
    public synchronized Map<String,Object> snapshot(){
        Map<String,Object> out=new LinkedHashMap<>();out.put("sms_send_schema",1);
        out.put("sms_send_attempt",attempt);out.put("sms_send_started_elapsed",started);out.put("sms_send_updated_elapsed",updated);
        out.put("sms_send_state",state);out.put("sms_send_failure",failure.name());out.put("sms_send_sip_status",sip);
        out.put("sms_send_rp_state",rp);out.put("sms_send_rp_cause",cause);return out;
    }
    public static Map<String,Object> unobserved(){
        Map<String,Object> out=new LinkedHashMap<>();out.put("sms_send_schema",1);out.put("sms_send_attempt",0L);
        out.put("sms_send_started_elapsed",0L);out.put("sms_send_updated_elapsed",0L);out.put("sms_send_state","UNOBSERVED");
        out.put("sms_send_failure","NONE");out.put("sms_send_sip_status",0);out.put("sms_send_rp_state","UNOBSERVED");out.put("sms_send_rp_cause",-1);return out;
    }
}
