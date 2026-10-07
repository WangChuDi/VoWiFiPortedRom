// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.*;

public final class ImsClientSnapshotContract {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("client-contract-"+checks);}
    private static Map<String,Object> sample(){
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("schema",1);v.put("channel","ims");v.put("slot",1);v.put("sub",7);v.put("nonce","0123456789abcdef");v.put("pid",123);v.put("boot",9);v.put("sample_elapsed",1200L);v.put("generation",3L);
        v.put("enabled_mask",9);v.put("last_notified_mask",9);v.put("feature_state",2);
        for(String key:new String[]{"notification_returns","sms_ready_events","enable_events","disable_events","client_rebind_returns"})v.put(key,1L);
        for(String key:new String[]{"registered","initialized","sms_session_idle"})v.put(key,true);
        for(String key:new String[]{"removed","client_rebind_pending","rebind_requested","rebind_accepted","callback_count_observed"})v.put(key,false);
        return v;
    }
    private static Map<String,Object> validate(Map<String,Object> v,boolean rebind){return ImsClientSnapshot.validate(v,1,7,"0123456789abcdef",123,9,1000,1300,rebind);}
    private static void rejects(String key,Object value){Map<String,Object> v=sample();v.put(key,value);boolean refused=false;try{validate(v,false);}catch(IllegalArgumentException expected){refused=true;}check(refused);}
    public static void main(String[] args){
        Map<String,Object> clean=validate(sample(),false);check(!clean.containsKey("pid")&&!clean.containsKey("boot")&&!clean.containsKey("nonce"));check(!clean.containsKey("capability_callback_count"));
        rejects("schema",2);rejects("channel","iwlan");rejects("slot",0);rejects("sub",8);rejects("nonce","fedcba9876543210");rejects("pid",124);rejects("boot",10);
        rejects("sample_elapsed",999);rejects("sample_elapsed",1301);rejects("sms_ready_events",-1);rejects("sms_ready_events",1.5);rejects("sms_ready_events",Double.NaN);rejects("registered","true");rejects("generation",0);rejects("feature_state",3);rejects("enabled_mask",65536);
        rejects("callback_count_observed",true);rejects("capability_callback_count",0);rejects("rebind_accepted",true);rejects("rebind_requested",true);rejects("subscriber_id","synthetic-not-private");
        Map<String,Object> down=sample();down.put("registered",false);boolean refused=false;try{validate(down,false);}catch(IllegalArgumentException expected){refused=true;}check(refused);
        down.put("last_notified_mask",0);check(Boolean.FALSE.equals(validate(down,false).get("registered")));
        Map<String,Object> callbacks=sample();callbacks.put("callback_count_observed",true);callbacks.put("capability_callback_count",0);check(validate(callbacks,false).get("capability_callback_count").equals(0));
        callbacks.put("capability_callback_count",-1);refused=false;try{validate(callbacks,false);}catch(IllegalArgumentException expected){refused=true;}check(refused);
        Map<String,Object> action=sample();action.put("rebind_requested",true);action.put("rebind_accepted",true);check(Boolean.TRUE.equals(validate(action,true).get("rebind_accepted")));
        check(ImsClientSnapshot.profileEligible(30,"raphael",true));check(!ImsClientSnapshot.profileEligible(30,"raphael",false));check(!ImsClientSnapshot.profileEligible(30,"other",true));
        for(int sdk=31;sdk<=37;sdk++)check(!ImsClientSnapshot.profileEligible(sdk,"raphael",true));
        boolean stale=false;try{ImsClientSnapshot.validate(sample(),1,7,"0123456789abcdef",123,9,1000,6201,false);}catch(IllegalArgumentException expected){stale=true;}check(stale);
        System.out.println("ims-client-snapshot-contracts="+checks);
    }
}
