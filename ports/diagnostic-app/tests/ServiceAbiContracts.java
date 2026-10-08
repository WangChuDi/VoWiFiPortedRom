// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import dev.codex.vowifi.common.ServiceAbiCatalog;
import java.util.*;
import org.json.JSONObject;

/** Provenance and failure boundaries for the actual service-process protocol. */
public final class ServiceAbiContracts {
    static int assertions;
    static void check(boolean b){assertions++;if(!b)throw new AssertionError("abi-contract-"+assertions);}
    static JSONObject sample(String role,boolean observed)throws Exception{
        JSONObject v=new JSONObject().put("schema",1).put("catalogue",1).put("channel",role).put("slot",1).put("sub",7).put("nonce","0123456789abcdef").put("pid",123).put("boot",4).put("sdk",30)
            .put("scope","active_service_process_lookup").put("read_only",true).put("initialization_performed",false).put("calls_verified",false).put("authorized",true).put("observed",observed).put("sample_elapsed",1100);
        if(observed){Map<String,String> states=new LinkedHashMap<>();for(ServiceAbiCatalog.Entry e:ServiceAbiCatalog.entries(role))states.put(e.key,"visible:0");
            v.put("generation",2).put("checks",new JSONObject(states)).put("total",states.size()).put("visible",states.size()).put("missing",0).put("inaccessible",0).put("linkage_errors",0);}
        return v;
    }
    static void valid(JSONObject v,String role)throws Exception{ServiceAbiSnapshot.validate(v,role,1,7,"0123456789abcdef",123,4,30,1000,1200);assertions++;}
    static void reject(JSONObject v,String role)throws Exception{try{ServiceAbiSnapshot.validate(v,role,1,7,"0123456789abcdef",123,4,30,1000,1200);}catch(Exception expected){assertions++;return;}throw new AssertionError("accepted-invalid-abi");}
    public static void main(String[] args)throws Exception{
        String[] fields={"schema","catalogue","channel","slot","sub","nonce","pid","boot","sdk","scope","read_only","initialization_performed","calls_verified","authorized","sample_elapsed","generation","total","visible","missing","inaccessible","linkage_errors"};
        Object[] wrong={2,2,"other",0,8,"bad",124,5,31,"root_app_process_core_lookup",false,true,true,false,999,0,999,999,1,1,1};
        int[] sizes={22,5,15};int index=0;
        for(String role:new String[]{"iwlan","qns","ims"}){
            JSONObject original=sample(role,true);valid(original,role);valid(sample(role,false),role);check(ServiceAbiCatalog.entries(role).size()==sizes[index++]);
            Set<String> ids=new HashSet<>();for(ServiceAbiCatalog.Entry entry:ServiceAbiCatalog.entries(role))check(ids.add(entry.key));
            for(int i=0;i<fields.length;i++){JSONObject changed=new JSONObject(original.toString());changed.put(fields[i],wrong[i]);reject(changed,role);changed=new JSONObject(original.toString());changed.remove(fields[i]);reject(changed,role);}
            JSONObject changed=new JSONObject(original.toString());changed.put("extra","not-metadata");reject(changed,role);
            changed=new JSONObject(original.toString());changed.getJSONObject("checks").put("extra","visible:0");reject(changed,role);
            changed=new JSONObject(original.toString());changed.getJSONObject("checks").put(ServiceAbiCatalog.entries(role).get(0).key,"visible:1");reject(changed,role);
            changed=new JSONObject(original.toString());changed.getJSONObject("checks").put(ServiceAbiCatalog.entries(role).get(0).key,"visible:99");reject(changed,role);
            changed=new JSONObject(original.toString());changed.put("sample_elapsed",1300);reject(changed,role);
            changed=sample(role,false);changed.put("checks",new JSONObject());reject(changed,role);
            changed=sample(role,false);changed.put("authorized",false);valid(changed,role);
            for(int mode=0;mode<3;mode++){final int failureMode=mode;ClassLoader blocked=new ClassLoader(null){protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(failureMode==0)throw new ClassNotFoundException();if(failureMode==1)throw new SecurityException();throw new LinkageError();}};
                Map<String,String> checks=ServiceAbiCatalog.inspect(role,blocked);int[] counts=ServiceAbiCatalog.counts(checks);check(counts[mode+1]==checks.size());check(counts[0]==0);}
        }
        try{ServiceAbiCatalog.entries("arbitrary");throw new AssertionError();}catch(IllegalArgumentException expected){assertions++;}
        JSONObject shown=sample("iwlan",true).put("status","OBSERVED");check(ServiceAbiSnapshot.describe(shown,"iwlan").contains("实际 IWLAN 服务进程"));
        check(!ServiceAbiSnapshot.describe(new JSONObject().put("status","UNAVAILABLE"),"iwlan").contains("签名可见"));
        System.out.println("{\"status\":\"passed\",\"assertions\":"+assertions+",\"network_traffic\":false,\"device_verified\":false}");
    }
}
