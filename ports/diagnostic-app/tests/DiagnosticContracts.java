// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicInteger;

public final class DiagnosticContracts {
    private static void check(boolean value){if(!value)throw new AssertionError("diagnostic-contract-failed");}
    private static String stat(String pid,String start){
        return pid+" (phone (test)) S "+String.join(" ",java.util.Collections.nCopies(18,"1"))+" "+start+" 0\n";
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&("timeout-child".equals(args[0])||"return-child".equals(args[0]))){
            PlatformHealth health=new PlatformHealth(name->new PlatformHealth.Sample(true,"private-process-identity"));health.begin();
            DiagnosticProgress progress=new DiagnosticProgress();
            progress.checkpoint(new JSONObject().put("sdk",33).put("slot",0).put("wifi","observed").put("engine_supported",true),"telephony");
            progress.startWatchdog(health);if("return-child".equals(args[0]))return;
            Thread.sleep(60000);throw new AssertionError("watchdog-did-not-terminate");
        }
        PlatformHealth.Sample original=PlatformHealth.parse(0,"123\n",stat("123","100"));
        check(Boolean.TRUE.equals(original.present)&&"123:100".equals(original.identity));
        PlatformHealth.Sample reused=PlatformHealth.parse(0,"123",stat("123","101"));
        check("changed".equals(PlatformHealth.compare(original,reused).getString("state")));
        check("stable".equals(PlatformHealth.compare(original,original).getString("state")));
        PlatformHealth.Sample absent=PlatformHealth.parse(1,"",null),unknown=PlatformHealth.Sample.unknown();
        check("absent".equals(PlatformHealth.compare(absent,absent).getString("state")));
        check("changed".equals(PlatformHealth.compare(absent,original).getString("state")));
        check("changed".equals(PlatformHealth.compare(original,absent).getString("state")));
        check("unknown".equals(PlatformHealth.compare(unknown,absent).getString("state")));
        check(PlatformHealth.compare(unknown,unknown).isNull("first_present"));
        check(PlatformHealth.parse(0,"123 456",stat("123","100")).present==null);
        check(PlatformHealth.parse(1,"permission denied",null).present==null);
        check(PlatformHealth.parse(0,"123",stat("124","100")).present==null);
        check(PlatformHealth.parse(0,"123","malformed").present==null);
        check(!PlatformHealth.stable(new JSONObject()));
        check(!DiagnosticPolicy.complete(null)&&!DiagnosticPolicy.complete(new JSONObject()));
        check(DiagnosticPolicy.complete(new JSONObject().put("diagnostic_complete",true)));
        check(!DiagnosticPolicy.complete(new JSONObject().put("diagnostic_complete",true).put("error","IOException")));
        check(!DiagnosticPolicy.complete(new JSONObject().put("diagnostic_complete",true).put("diagnostic_error","TimeoutException")));
        JSONObject registered=new JSONObject().put("diagnostic_complete",true).put("ims_transport",2)
            .put("platform_health",new JSONObject().put("phone",PlatformHealth.compare(original,original)).put("system_server",PlatformHealth.compare(original,original)));
        check(DiagnosticPolicy.wlanConfirmed(registered));
        registered.put("diagnostic_complete",false);check(!DiagnosticPolicy.wlanConfirmed(registered));
        registered.put("diagnostic_complete",true).getJSONObject("platform_health").put("phone",PlatformHealth.compare(original,reused));
        check(!DiagnosticPolicy.wlanConfirmed(registered));
        DiagnosticProgress progress=new DiagnosticProgress();JSONObject out=new JSONObject().put("wifi","observed").put("engine_supported",true);
        progress.checkpoint(out,"subscription");out.put("wifi","later");
        JSONObject partial=progress.timedOutSnapshot();
        check("observed".equals(partial.getString("wifi"))&&"subscription".equals(partial.getString("diagnostic_stage")));
        check(!partial.getBoolean("engine_supported")&&!DiagnosticPolicy.complete(partial)&&!partial.has("error"));
        AtomicInteger winners=new AtomicInteger();Thread[] threads=new Thread[16];
        for(int i=0;i<threads.length;i++){threads[i]=new Thread(()->{if(progress.claimTerminal())winners.incrementAndGet();});threads[i].start();}
        for(Thread thread:threads)thread.join();check(winners.get()==1);
        System.out.println("diagnostic-contracts-passed");
    }
}
