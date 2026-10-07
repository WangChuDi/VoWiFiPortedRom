// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tooltest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.widget.Button;
import android.widget.Spinner;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Exact installed 0.9.11 UI trial. No exported automation endpoint in the tool. */
public final class OwnUidTrial extends Instrumentation {
    private final Bundle report=new Bundle();
    private Activity activity;
    private Object observation;
    private boolean busy;
    private Throwable uiFailure;
    private static final String TARGET="dev.codex.vowifi.tool";
    private static final String SHA="de22787f736555d5efe9260fab3559f04d5f03ffaec3728c0b06564cc0205c3c";
    private static void require(boolean condition,String code)throws IOException{if(!condition)throw new IOException(code);}
    private Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void ui(Runnable action)throws Exception{
        uiFailure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){uiFailure=e;}});
        if(uiFailure!=null)throw new IOException("ui-operation-unavailable");
    }
    private void sample()throws Exception{ui(()->{try{busy=(Boolean)field("busy");observation=field("last");}catch(Exception e){throw new IllegalStateException(e);}});}
    private JSONObject await(long seconds)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+seconds*1000;
        do {sample();if(!busy){require(observation instanceof JSONObject,"ui-result-unavailable");return (JSONObject)observation;}Thread.sleep(250);}while(SystemClock.elapsedRealtime()<deadline);
        throw new IOException("ui-observation-deadline");
    }
    private void describe(String label,JSONObject data)throws Exception{
        JSONObject providers=data.optJSONObject("providers"),window=data.optJSONObject("sms_dispatcher_window");
        report.putBoolean(label+"_diagnostic_complete",data.optBoolean("diagnostic_complete"));
        report.putBoolean(label+"_wlan_registered",data.optInt("ims_transport",-1)==2);
        report.putBoolean(label+"_voice_capable",data.optBoolean("voice"));
        report.putBoolean(label+"_sms_capable",data.optBoolean("sms"));
        report.putBoolean(label+"_native_sms_supported",data.optBoolean("native_sms_ims_supported"));
        report.putBoolean(label+"_dispatcher_observed",window!=null&&"observed".equals(window.optString("status")));
        report.putBoolean(label+"_dispatcher_available",window!=null&&window.optBoolean("available"));
        report.putBoolean(label+"_full_selection_active",providers!=null&&"ACTIVE".equals(providers.optString("transaction"))&&providers.optInt("components")==7);
        report.putBoolean(label+"_persistent_enabled",providers!=null&&"ENABLED".equals(providers.optString("persistent")));
        JSONObject health=data.optJSONObject("platform_health");
        report.putBoolean(label+"_platform_stable",health!=null&&health.optJSONObject("phone")!=null&&health.optJSONObject("system_server")!=null&&"stable".equals(health.getJSONObject("phone").optString("state"))&&"stable".equals(health.getJSONObject("system_server").optString("state")));
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){boolean success=false;report.putString("status","starting");
        report.putBoolean("new_call_or_sms",false);report.putBoolean("phone_reload_requested",false);
        report.putBoolean("production_apk_modified",false);report.putInt("schema",1);report.putInt("sdk",Build.VERSION.SDK_INT);
        try{
            require(Build.VERSION.SDK_INT==30&&"raphael".equals(Build.DEVICE),"calibrated-device-required");
            ApplicationInfo target=getTargetContext().getPackageManager().getApplicationInfo(TARGET,0);
            require(android.os.Process.myUid()==target.uid&&target.uid>=10000,"target-application-uid-required");report.putBoolean("target_own_uid_verified",true);
            MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(target.sourceDir)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)hash.update(bytes,0,n);}
            StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));require(SHA.equals(digest.toString()),"tested-tool-required");report.putBoolean("tested_tool_bytes_verified",true);
            activity=startActivitySync(new Intent().setClassName(TARGET,TARGET+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(activity.getClass().getName().equals(TARGET+".MainActivity"),"target-activity-required");
            ui(()->{try{List<?> slots=(List<?>)field("slots");int index=slots.indexOf(Integer.valueOf(1));if(index<0)throw new IllegalStateException();((Spinner)field("sims")).setSelection(index);}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();
            ui(()->{try{Method m=activity.getClass().getDeclaredMethod("diagnose");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new IllegalStateException(e);}});
            JSONObject before=await(60);describe("before",before);
            require(before.optBoolean("diagnostic_complete")&&before.optInt("slot",-1)==1&&before.optBoolean("engine_supported"),"complete-selected-diagnostic-required");
            JSONObject provider=before.optJSONObject("providers");require(provider!=null&&provider.optInt("owner_slot",-1)==1&&provider.optInt("owner_sub",-1)==before.optInt("sub_id",-2)&&"ACTIVE".equals(provider.optString("transaction"))&&provider.optInt("components")==7,"recorded-full-owner-required");
            ui(()->{try{Button button=(Button)field("rebind");if(!button.isEnabled())throw new IllegalStateException();if(!button.performClick())throw new IllegalStateException();busy=(Boolean)field("busy");if(!busy)throw new IllegalStateException();}catch(Exception e){throw new IllegalStateException(e);}});
            report.putBoolean("actual_rebind_button_clicked",true);
            JSONObject after=await(100);describe("after",after);JSONObject action=after.optJSONObject("client_rebind_result");require(action!=null,"ui-action-result-required");
            report.putBoolean("action_accepted",action.optBoolean("action_accepted"));report.putBoolean("action_completed",action.optBoolean("action_completed"));report.putBoolean("worker_phone_reloaded",action.optBoolean("phone_reloaded",true));
            require(action.optBoolean("action_accepted")&&action.optBoolean("action_completed")&&!action.optBoolean("phone_reloaded",true),"completed-idle-rebind-required");
            for(String name:new String[]{"diagnostic_complete","wlan_registered","voice_capable","sms_capable","native_sms_supported","dispatcher_observed","dispatcher_available","full_selection_active","persistent_enabled","platform_stable"})require(report.getBoolean("after_"+name),"post-action-state-unconfirmed");
            report.putString("status","passed");success=true;
        }catch(Throwable e){report.putString("status","failed");report.putString("error",e.getClass().getSimpleName());String code=e.getMessage();String[] allowed={"calibrated-device-required","target-application-uid-required","tested-tool-required","target-activity-required","ui-operation-unavailable","ui-result-unavailable","ui-observation-deadline","complete-selected-diagnostic-required","recorded-full-owner-required","ui-action-result-required","completed-idle-rebind-required","post-action-state-unconfirmed"};boolean known=false;for(String s:allowed)if(s.equals(code))known=true;report.putString("reason",known?code:"unclassified");}
        finally{report.putBoolean("carrier_call_sms_tested",false);report.putBoolean("dual_active_sim_tested",false);finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,report);}
    }
}
