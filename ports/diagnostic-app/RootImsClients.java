// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Explicit, idle-only framework client rebind. No phone kill, call or SMS. */
public final class RootImsClients {
    public static JSONObject observe(int slot,int sub)throws Exception{return request(slot,sub,false);}
    private static JSONObject request(int slot,int sub,boolean rebind)throws Exception{
        PlatformHealth.Sample before=PlatformHealth.readProcess("me.phh.ims");
        if(!Boolean.TRUE.equals(before.present)||before.identity==null)throw new IOException("client-process-unavailable");
        int pid=Integer.parseInt(before.identity.split(":",-1)[0]);
        String bootValue=RootDiagnostics.command(2,"settings","get","global","boot_count").trim();
        if(!bootValue.matches("[0-9]{1,9}"))throw new IOException("client-boot-unavailable");
        String nonce=UUID.randomUUID().toString().replace("-","").substring(0,16);
        long begin=SystemClock.elapsedRealtime();
        String output=RootDiagnostics.command(4,"content","call","--uri","content://me.phh.ims.status","--method",rebind?"client-rebind":"capabilities","--arg","ims:"+slot+":"+sub+":"+nonce);
        if(output.length()>16384)throw new IOException("client-result-too-large");
        Matcher m=Pattern.compile("snapshot=(\\{[^\\r\\n]*\\})\\}\\]").matcher(output);if(!m.find())throw new IOException("client-result-unavailable");
        JSONObject raw=new JSONObject(m.group(1));Map<String,Object> fields=new LinkedHashMap<>();
        for(Iterator<String> keys=raw.keys();keys.hasNext();){String key=keys.next();fields.put(key,raw.get(key));}
        Map<String,Object> clean=ImsClientSnapshot.validate(fields,slot,sub,nonce,pid,Integer.parseInt(bootValue),begin,SystemClock.elapsedRealtime(),rebind);
        if(!"stable".equals(PlatformHealth.compare(before,PlatformHealth.readProcess("me.phh.ims")).optString("state")))throw new IOException("client-process-changed");
        return new JSONObject(clean);
    }
    private static void requireOwner(Context context,int slot,int sub)throws Exception{
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        TelephonyManager tm=context.getSystemService(TelephonyManager.class);int matches=0;
        if(active==null||tm==null)throw new IOException("client-subscriptions-unavailable");
        for(SubscriptionInfo info:active){
            if(tm.createForSubscriptionId(info.getSubscriptionId()).getCallState()!=TelephonyManager.CALL_STATE_IDLE)throw new IOException("client-active-call-refused");
            if(info.getSimSlotIndex()==slot){
                if(info.getSubscriptionId()!=sub||tm.getSimState(slot)!=TelephonyManager.SIM_STATE_READY||!"23415".equals(tm.createForSubscriptionId(sub).getSimOperator()))throw new IOException("client-owner-changed");matches++;
            }
        }
        if(matches!=1)throw new IOException("client-owner-unavailable");
        String control=RootDiagnostics.command(8,"sh","/data/adb/modules/codex_vowifi_stack_api30/control.sh","status",Integer.toString(slot),Integer.toString(sub));
        Map<String,String> status=new HashMap<>();
        for(String line:control.split("[\\r\\n]+")){int equal=line.indexOf('=');if(equal>0)status.put(line.substring(0,equal),line.substring(equal+1));}
        if(!"ACTIVE".equals(status.get("transaction"))||!Integer.toString(slot).equals(status.get("owner_slot"))||!Integer.toString(sub).equals(status.get("owner_sub"))||!"me.phh.ims".equals(status.get("config_ims_mmtel_package_override_string")))throw new IOException("client-controller-owner-refused");
        String mask=status.get("components");if(mask==null||!mask.matches("[1-7]")||(Integer.parseInt(mask)&4)==0)throw new IOException("client-ims-not-selected");
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean accepted=false;
        try{
            if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
            if(args.length!=3||!"rebind".equals(args[0]))throw new IllegalArgumentException("client-action-arguments");
            int slot=Integer.parseInt(args[1]),sub=Integer.parseInt(args[2]);
            if(slot<0||slot>7||sub<0)throw new IllegalArgumentException("client-selection");
            if(!ImsClientSnapshot.profileEligible(Build.VERSION.SDK_INT,Build.DEVICE,RootDiagnostics.pinnedSmsFramework()))throw new IOException("client-rom-not-calibrated");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();RootDiagnostics.initializeTelephony();
            PlatformHealth health=new PlatformHealth();health.begin();requireOwner(context,slot,sub);
            JSONObject before=observe(slot,sub);result.put("before",before);
            if(!before.optBoolean("registered")||!before.optBoolean("initialized")||before.optBoolean("removed")||!before.optBoolean("sms_session_idle"))throw new IOException("client-registration-or-idle-refused");
            JSONObject request=request(slot,sub,true);accepted=request.optBoolean("rebind_accepted");result.put("request",request).put("action_accepted",accepted);
            if(request.optLong("generation")!=before.optLong("generation"))throw new IOException("client-generation-changed");
            if(accepted){
                long deadline=SystemClock.elapsedRealtime()+9000;
                while(SystemClock.elapsedRealtime()<deadline){
                    Thread.sleep(1000);requireOwner(context,slot,sub);JSONObject after=observe(slot,sub);result.put("after",after);
                    if(after.optLong("generation")!=before.optLong("generation"))throw new IOException("client-generation-changed");
                    if(!after.optBoolean("client_rebind_pending")&&after.optLong("client_rebind_returns")>before.optLong("client_rebind_returns"))break;
                }
            }
            JSONObject after=result.optJSONObject("after"),platform=health.finish();result.put("platform_health",platform);
            result.put("action_completed",accepted&&PlatformHealth.stable(platform)&&after!=null&&!after.optBoolean("client_rebind_pending")&&after.optLong("client_rebind_returns")>before.optLong("client_rebind_returns"));
            result.put("action","client-rebind").put("slot",slot).put("sub",sub).put("phone_reloaded",false);
        }catch(Throwable failure){try{result.put("action","client-rebind").put("action_accepted",accepted).put("action_completed",false).put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result);System.exit(0);
    }
}
