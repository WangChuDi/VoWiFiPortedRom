// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.smsstagetest;
import android.app.*;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Actual diagnostic app UID and rendering; no replacement/test-message action. */
public final class OwnUidSmsStagesTrial extends Instrumentation {
    private static final String TARGET="dev.codex.vowifi.tool",SHA="298555389712f5af59b360480dc5ae621e3ee61f38e23990d9b8a355c734c99f";
    private final Bundle report=new Bundle();private Activity activity;private Throwable uiFailure;private boolean busy,heading,scope;private Object last;
    private static void require(boolean value,String code)throws IOException{if(!value)throw new IOException(code);}
    private Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void ui(Runnable action)throws Exception{uiFailure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){uiFailure=e;}});require(uiFailure==null,"ui-operation-unavailable");}
    private JSONObject inspect(int slot)throws Exception{
        ui(()->{try{List<?>slots=(List<?>)field("slots");int index=slots.indexOf(slot);if(index<0)throw new IllegalStateException();((Spinner)field("sims")).setSelection(index);}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();
        ui(()->{try{Method m=activity.getClass().getDeclaredMethod("diagnose");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new IllegalStateException(e);}});
        long deadline=SystemClock.elapsedRealtime()+65000;
        do{ui(()->{try{busy=(Boolean)field("busy");last=field("last");}catch(Exception e){throw new IllegalStateException(e);}});if(!busy){require(last instanceof JSONObject,"ui-result-unavailable");JSONObject data=(JSONObject)last;require(data.optInt("slot",-1)==slot&&data.optBoolean("diagnostic_complete"),"fresh-selected-result-required");return data;}Thread.sleep(250);}while(SystemClock.elapsedRealtime()<deadline);
        throw new IOException("ui-observation-deadline");
    }
    private void text(View view){
        if(view instanceof TextView){String value=((TextView)view).getText().toString();if(value.equals("最近一次短信发送链路"))heading=true;if(value.contains("本代 IMS 尚未观测到短信发送请求")||value.contains("最近一次发送的历史观测"))scope=true;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)text(group.getChildAt(i));}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){boolean success=false;report.putInt("schema",1);report.putInt("sdk",Build.VERSION.SDK_INT);report.putBoolean("new_call_or_sms",false);report.putBoolean("engine_mutation_requested",false);
        try{
            require(Build.VERSION.SDK_INT==30&&"raphael".equals(Build.DEVICE),"calibrated-device-required");ApplicationInfo target=getTargetContext().getPackageManager().getApplicationInfo(TARGET,0);
            require(android.os.Process.myUid()==target.uid&&target.uid>=10000,"target-application-uid-required");report.putBoolean("target_own_uid_verified",true);
            MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(target.sourceDir)){byte[]bytes=new byte[8192];for(int n;(n=input.read(bytes))>=0;)if(n>0)hash.update(bytes,0,n);}StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));require(SHA.equals(digest.toString()),"tested-tool-required");report.putBoolean("tested_tool_bytes_verified",true);
            activity=startActivitySync(new Intent().setClassName(TARGET,TARGET+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            JSONObject empty=inspect(0);require(!empty.has("sub_id")&&!empty.has("ims_status"),"empty-slot-attribution-required");report.putBoolean("empty_slot_does_not_inherit_active_query",true);
            JSONObject active=inspect(1),sms=active.optJSONObject("ims_status"),window=active.optJSONObject("sms_dispatcher_window"),owner=active.optJSONObject("providers");
            require(sms!=null&&sms.optBoolean("observed")&&sms.optInt("sms_send_schema")==1&&sms.has("sms_send_rp_cause")&&sms.has("sms_send_updated_elapsed"),"sms-stage-schema-required");report.putBoolean("sms_stage_schema_verified",true);
            require(Arrays.asList("UNOBSERVED","PREPARING","WAITING_NETWORK_ACK","ACCEPTED","FAILED").contains(sms.optString("sms_send_state")),"sms-stage-enum-required");
            require(active.optInt("ims_transport",-1)==2&&active.optBoolean("voice")&&active.optBoolean("sms")&&active.optBoolean("native_sms_ims_supported")&&window!=null&&window.optBoolean("available"),"native-readiness-required");report.putBoolean("active_native_dispatcher_ready",true);
            require(owner!=null&&"ACTIVE".equals(owner.optString("transaction"))&&owner.optInt("components")==7&&"ENABLED".equals(owner.optString("persistent")),"full-owner-required");report.putBoolean("full_replacement_still_active",true);
            ui(()->text(activity.getWindow().getDecorView()));require(heading&&scope,"sms-stage-card-required");report.putBoolean("actual_sms_stage_card_present",true);report.putBoolean("actual_sms_scope_text_present",true);success=true;report.putString("status","passed");
        }catch(Throwable e){report.putString("status","failed");report.putString("error",e.getClass().getSimpleName());String code=e.getMessage();report.putString("reason",code!=null&&code.matches("[a-z-]{1,64}")?code:"unclassified");}
        finally{if(activity!=null)try{runOnMainSync(()->activity.finish());}catch(Exception ignored){}report.putBoolean("dual_active_sim_tested",false);report.putBoolean("modern_app_own_uid_tested",false);finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,report);}
    }
}
