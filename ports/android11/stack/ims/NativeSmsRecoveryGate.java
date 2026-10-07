// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-feature, monotonic native-state confirmation. Unknown never requests a rebind. */
public final class NativeSmsRecoveryGate {
    private final boolean supported;
    private long eligibleSince=-1,lastNegative=-1,checked,checks,requests,lastRequest,mismatch;
    private int negatives;
    private boolean selfUid,requestProof;
    private String status,nativeState="UNKNOWN";
    public NativeSmsRecoveryGate(boolean supported){this.supported=supported;status=supported?"WAITING":"UNSUPPORTED";}
    public synchronized boolean sample(long now,boolean eligible,Boolean nativeSupported){
        if(now<0||now<checked)throw new IllegalArgumentException("native-sms-clock");
        checked=now;checks++;requestProof=false;
        if(!supported){status="UNSUPPORTED";return false;}
        nativeState=nativeSupported==null?"UNKNOWN":nativeSupported?"TRUE":"FALSE";
        if(!eligible){eligibleSince=-1;clearNegative();status="INACTIVE";return false;}
        if(eligibleSince<0)eligibleSince=now;
        if(nativeSupported==null){clearNegative();status="UNKNOWN";return false;}
        if(nativeSupported){clearNegative();status="HEALTHY";return false;}
        if(now-eligibleSince<20000){clearNegative();status="WAITING";return false;}
        if(lastNegative<0||now-lastNegative>=3000){negatives++;lastNegative=now;if(mismatch==0)mismatch=now;}
        if(negatives<3){status="WAITING";return false;}
        if(requests>=3){status="LIMIT";return false;}
        if(requests>0&&now-lastRequest<120000){status="COOLDOWN";return false;}
        status="MISMATCH";requestProof=true;return true;
    }
    public synchronized void requested(long now){
        if(!requestProof||now!=checked||requests>=3)throw new IllegalStateException("native-sms-request-without-proof");
        // An accepted queued request must be counted even if a concurrent DOWN
        // invalidated eligibility. Its execution rechecks current registration.
        requestProof=false;requests++;lastRequest=now;
        if(!"INACTIVE".equals(status))status="RECOVERING";
        clearNegative();
    }
    private void clearNegative(){negatives=0;lastNegative=-1;mismatch=0;}
    /** Registration withdrawal invalidates prior samples without inventing a new query. */
    public synchronized void inactive(){eligibleSince=-1;clearNegative();nativeState="UNKNOWN";status=supported?"INACTIVE":"UNSUPPORTED";}
    public synchronized void contextObserved(boolean ownUid){selfUid=ownUid;}
    public synchronized Map<String,Object> snapshot(){
        Map<String,Object> out=new LinkedHashMap<>();out.put("native_watch_schema",1);
        out.put("native_watch_status",status);out.put("native_watch_native",nativeState);
        out.put("native_watch_checks",checks);out.put("native_watch_requests",requests);
        out.put("native_watch_checked_elapsed",checked);out.put("native_watch_mismatch_since_elapsed",mismatch);
        out.put("native_watch_last_request_elapsed",lastRequest);out.put("native_watch_self_uid",selfUid);return out;
    }
}
