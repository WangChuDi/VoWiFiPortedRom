// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.*;

public final class SmsDispatcherWindowContract {
    private static int checks;
    private static final long BEGIN=1791334817173L;
    private static void require(boolean value){checks++;if(!value)throw new AssertionError("sms-window-contract-"+checks);}
    private static String log(String pid,int slot,String time,boolean up,boolean reg,boolean cap){
        return time+" "+pid+" 19 D ImsSmsDispatcher ["+slot+"]: isAvailable: up="+up+", reg= "+reg+", cap= "+cap;
    }
    private static SmsDispatcherWindow window(){return new SmsDispatcherWindow("123",1,BEGIN,BEGIN+2,2);}
    private static void invalid(String pid,int slot,long begin,long end,long duration){
        try{new SmsDispatcherWindow(pid,slot,begin,end,duration);require(false);}catch(IllegalArgumentException expected){require(true);}
    }
    public static void main(String[]args){
        SmsDispatcherWindow all=window();all.accept(log("123",1,"1791334817.173",true,true,true));
        Map<String,Object> result=all.finish(true);require("observed".equals(result.get("status")));require(Boolean.TRUE.equals(result.get("available")));
        for(int bits=0;bits<8;bits++){
            SmsDispatcherWindow flags=window();flags.accept(log("123",1,"1791334817.174123",(bits&1)!=0,(bits&2)!=0,(bits&4)!=0));
            require(Boolean.valueOf(bits==7).equals(flags.finish(true).get("available")));
        }
        SmsDispatcherWindow rejects=window();
        rejects.accept(log("999",1,"1791334817.173",true,true,true));
        rejects.accept(log("123",0,"1791334817.173",true,true,true));
        rejects.accept(log("123",1,"1791334817.172",true,true,true));
        rejects.accept(log("123",1,"1791334817.176",true,true,true));
        rejects.accept(log("123",1,"1791334817.173",true,true,true)+" arbitrary-content");
        rejects.accept("unrelated phone or SMS log");rejects.accept(null);
        require("no-window-sample".equals(rejects.finish(true).get("reason")));require(!rejects.finish(true).containsKey("available"));
        SmsDispatcherWindow conflict=window();conflict.accept(log("123",1,"1791334817.173",true,true,true));conflict.accept(log("123",1,"1791334817.174",true,true,false));
        require("state-changed".equals(conflict.finish(true).get("reason")));require(!conflict.finish(true).containsKey("available"));
        require("phone-changed".equals(all.finish(false).get("reason")));require(!all.finish(false).containsKey("available"));
        SmsDispatcherWindow duplicate=window();duplicate.accept(log("123",1,"1791334817.173",true,true,true));duplicate.accept(log("123",1,"1791334817.174",true,true,true));
        require("observed".equals(duplicate.finish(true).get("status")));
        SmsDispatcherWindow full=window();for(int i=0;i<65;i++)full.accept(log("123",1,"1791334817.173",true,true,true));
        require("sample-limit".equals(full.finish(true).get("reason")));require(!full.finish(true).containsKey("available"));
        invalid("123;id",1,BEGIN,BEGIN+2,2);invalid("123",8,BEGIN,BEGIN+2,2);invalid("123",1,BEGIN,BEGIN-1,2);
        invalid("123",1,BEGIN,BEGIN+16000,16000);invalid("123",1,BEGIN,BEGIN+1000,2);invalid("123",1,BEGIN,BEGIN+2,-1);
        Set<String> allowed=new HashSet<>(Arrays.asList("schema","status","query_duration_ms","sample_count","phone_identity_stable","reason","service_up","registered","sms_capable","available"));
        require(allowed.containsAll(result.keySet()));require(!result.toString().contains("123"));
        require(!result.toString().contains("1791334817"));
        SmsDispatcherWindow immediate=new SmsDispatcherWindow("123",1,BEGIN,BEGIN,1);
        immediate.accept(log("123",1,"1791334817.173",true,true,true));
        require(Boolean.TRUE.equals(immediate.finish(true).get("available")));
        SmsDispatcherWindow boundary=window();boundary.accept(log("123",1,"1791334817.175999",true,true,true));
        require(Boolean.TRUE.equals(boundary.finish(true).get("available")));
        // A true native Binder result is intentionally absent from the parser;
        // it must never fabricate a software-dispatcher sample.
        require(!window().finish(true).containsKey("available"));
        System.out.println("sms-dispatcher-window-contracts="+checks);
    }
}
