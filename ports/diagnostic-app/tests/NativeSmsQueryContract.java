// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.*;
final class NativeSmsQueryContract {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("native-query-contract-"+checks);}
    private static Map<String,Object> sample(){Map<String,Object>v=new LinkedHashMap<>();
        v.put("schema",1);v.put("channel","ims-native-sms");v.put("slot",1);v.put("sub",7);v.put("nonce","0123456789abcdef");v.put("pid",123);v.put("boot",9);v.put("sample_elapsed",1200L);
        v.put("query_elapsed",1100L);v.put("result","TRUE");v.put("status","OBSERVED");v.put("self_uid",true);v.put("read_only",true);v.put("scope","IMS_OR_RADIO");return v;}
    private static Map<String,Object> validate(Map<String,Object>v){return NativeSmsQuerySnapshot.validate(v,1,7,"0123456789abcdef",123,9,1000,1300);}
    private static void reject(String key,Object value){Map<String,Object>v=sample();v.put(key,value);boolean refused=false;try{validate(v);}catch(IllegalArgumentException expected){refused=true;}check(refused);}
    static void run(){
        Map<String,Object>clean=validate(sample());check(!clean.containsKey("pid")&&!clean.containsKey("boot")&&!clean.containsKey("nonce"));
        reject("schema",2);reject("schema",1.5);reject("channel","ims");reject("slot",0);reject("sub",8);reject("nonce","different");reject("pid",124);reject("boot",10);
        reject("sample_elapsed",999);reject("sample_elapsed",1301);reject("query_elapsed",999);reject("query_elapsed",1201);reject("query_elapsed",0);reject("query_elapsed",-1);reject("query_elapsed",Double.NaN);
        reject("self_uid",false);reject("self_uid","true");reject("read_only",false);reject("scope","WLAN_READY");reject("result","UNKNOWN");reject("result","invalid");reject("status","HEALTHY");reject("status","UNAVAILABLE");reject("body","never-exported");
        for(String key:sample().keySet()){Map<String,Object>v=sample();v.remove(key);boolean refused=false;try{validate(v);}catch(IllegalArgumentException expected){refused=true;}check(refused);}
        for(String status:new String[]{"UNAVAILABLE","TIMEOUT","BUSY","UNSUPPORTED"}){Map<String,Object>v=sample();v.put("status",status);v.put("result","UNKNOWN");v.put("self_uid",false);v.put("query_elapsed",0L);check(validate(v).get("result").equals("UNKNOWN"));}
        Map<String,Object>v=sample();v.put("result","FALSE");check(validate(v).get("result").equals("FALSE"));
        boolean refused=false;try{NativeSmsQuerySnapshot.validate(sample(),0,8,"0123456789abcdef",123,9,1000,1300);}catch(IllegalArgumentException expected){refused=true;}check(refused);
        refused=false;try{NativeSmsQuerySnapshot.validate(sample(),1,7,"0123456789abcdef",123,9,1000,7001);}catch(IllegalArgumentException expected){refused=true;}check(refused);
        System.out.println("native-sms-query-contracts="+checks);
    }
}
