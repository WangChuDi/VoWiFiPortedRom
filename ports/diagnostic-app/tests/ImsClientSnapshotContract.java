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
    private static Map<String,Object> watch(){Map<String,Object> v=sample();
        v.put("native_watch_schema",1);v.put("native_watch_status","HEALTHY");v.put("native_watch_native","TRUE");v.put("native_watch_self_uid",true);
        v.put("native_watch_checks",2L);v.put("native_watch_requests",1L);v.put("native_watch_checked_elapsed",1100L);
        v.put("native_watch_mismatch_since_elapsed",0L);v.put("native_watch_last_request_elapsed",1050L);return v;
    }
    private static void watchRejects(String key,Object value){Map<String,Object> v=watch();v.put(key,value);boolean refused=false;try{validate(v,false);}catch(IllegalArgumentException expected){refused=true;}check(refused);}
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
        check(Boolean.TRUE.equals(validate(watch(),false).get("native_watch_self_uid")));
        watchRejects("native_watch_schema",2);watchRejects("native_watch_schema",1.5);watchRejects("native_watch_self_uid","true");watchRejects("native_watch_self_uid",false);
        watchRejects("native_watch_status","made-up");watchRejects("native_watch_native","made-up");watchRejects("native_watch_native","FALSE");
        watchRejects("native_watch_checks",-1);watchRejects("native_watch_requests",4);watchRejects("native_watch_checked_elapsed",1301L);watchRejects("native_watch_last_request_elapsed",1101L);watchRejects("native_watch_mismatch_since_elapsed",1101L);
        for(String key:new ArrayList<>(watch().keySet()))if(key.startsWith("native_watch_")){
            Map<String,Object> incomplete=watch();incomplete.remove(key);boolean missing=false;try{validate(incomplete,false);}catch(IllegalArgumentException expected){missing=true;}check(missing);
        }
        Map<String,Object> unknown=watch();unknown.put("native_watch_status","UNKNOWN");unknown.put("native_watch_native","UNKNOWN");unknown.put("native_watch_self_uid",false);check(validate(unknown,false).containsKey("native_watch_schema"));
        Map<String,Object> mismatch=watch();mismatch.put("native_watch_status","MISMATCH");mismatch.put("native_watch_native","FALSE");check(validate(mismatch,false).get("native_watch_native").equals("FALSE"));
        System.out.println("ims-client-snapshot-contracts="+checks);
    }
}
