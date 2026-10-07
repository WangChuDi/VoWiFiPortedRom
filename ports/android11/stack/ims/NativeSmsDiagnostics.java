// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import android.content.Context;
import android.os.SystemClock;
import android.telephony.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded same-process observation for an active SIM; no lease, registration or traffic. */
public final class NativeSmsDiagnostics {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"ImsNativeSmsDiagnostic");t.setDaemon(true);return t;});
    private static void owner(Context context,int slot,int sub){
        SubscriptionManager manager=context.getSystemService(SubscriptionManager.class);
        TelephonyManager phone=context.getSystemService(TelephonyManager.class);
        List<SubscriptionInfo> active=manager==null?null:manager.getActiveSubscriptionInfoList();int matched=0;
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot){
            if(info.getSubscriptionId()!=sub)throw new IllegalStateException("native-query-owner-changed");matched++;
        }
        if(matched!=1||phone==null||phone.getSimState(slot)!=TelephonyManager.SIM_STATE_READY)throw new IllegalStateException("native-query-owner-unavailable");
    }
    private static Map<String,Object> value(String status,Boolean supported,boolean self,long checked){
        Map<String,Object> v=new LinkedHashMap<>();v.put("result",supported==null?"UNKNOWN":supported?"TRUE":"FALSE");
        v.put("status",status);v.put("self_uid",self);v.put("query_elapsed",checked);v.put("read_only",true);v.put("scope","IMS_OR_RADIO");return v;
    }
    public static Map<String,Object> query(Context context,int slot,int sub){
        if(context==null||slot<0||slot>7||sub<0)throw new IllegalArgumentException("native-query-selection");
        if(!NativeSmsStatusReader.diagnosticProfileEligible())return value("UNSUPPORTED",null,false,0);
        if(!busy.compareAndSet(false,true))return value("BUSY",null,false,0);
        Future<Map<String,Object>> result;
        try{
            result=worker.submit(()->{
                try{
                    owner(context,slot,sub);boolean own=NativeSmsStatusReader.ownUid();
                    Boolean supported=NativeSmsStatusReader.observeForDiagnostics(sub);
                    owner(context,slot,sub);
                    return value(supported==null?"UNAVAILABLE":"OBSERVED",supported,own,SystemClock.elapsedRealtime());
                }finally{busy.set(false);}
            });
        }catch(RuntimeException refused){busy.set(false);return value("UNAVAILABLE",null,false,0);}
        try{return result.get(2500,TimeUnit.MILLISECONDS);}
        catch(TimeoutException expired){return value("TIMEOUT",null,false,0);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return value("UNAVAILABLE",null,false,0);}
        catch(ExecutionException unavailable){return value("UNAVAILABLE",null,false,0);}
        // A stuck Binder occupies only this worker. Its late result is discarded;
        // subsequent requests report BUSY until it returns or the process restarts.
    }
}
