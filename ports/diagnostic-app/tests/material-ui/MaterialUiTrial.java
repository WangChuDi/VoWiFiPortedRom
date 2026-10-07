// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.materialtest;
import android.app.*;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Actual app UID checks for navigation, SIM isolation and guarded actions. */
public final class MaterialUiTrial extends Instrumentation {
    private static final String TARGET="dev.codex.vowifi.tool";
    private final Bundle report=new Bundle();private Activity activity;private Throwable uiFailure;private Bundle args;
    private static void require(boolean value,String code)throws IOException{if(!value)throw new IOException(code);}
    private Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void ui(Runnable action)throws Exception{uiFailure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){uiFailure=e;}});require(uiFailure==null,"ui-operation-unavailable");}
    private JSONObject inspect(int slot)throws Exception{
        ui(()->{try{List<?> slots=(List<?>)field("slots");int index=slots.indexOf(slot);if(index<0)throw new IllegalStateException();((Spinner)field("sims")).setSelection(index);}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();
        ui(()->{try{Method m=activity.getClass().getDeclaredMethod("diagnose");m.setAccessible(true);m.invoke(activity);require((Boolean)field("busy")&&!((Button)field("check")).isEnabled()&&!((Spinner)field("sims")).isEnabled(),"busy-guard-required");for(String name:new String[]{"trial","enable","reload","rebind","rollback","install"})require(!((Button)field(name)).isEnabled(),"busy-mutation-disabled-required");}catch(Exception e){throw new IllegalStateException(e);}});
        long deadline=SystemClock.elapsedRealtime()+65000;
        do{final boolean[] busy={true};final Object[] last={null};ui(()->{try{busy[0]=(Boolean)field("busy");last[0]=field("last");}catch(Exception e){throw new IllegalStateException(e);}});if(!busy[0]){require(last[0] instanceof JSONObject,"fresh-result-required");JSONObject data=(JSONObject)last[0];require(data.optInt("slot",-1)==slot&&data.optBoolean("diagnostic_complete"),"selected-result-required");return data;}Thread.sleep(250);}while(SystemClock.elapsedRealtime()<deadline);throw new IOException("ui-check-deadline");
    }
    private void page(int index,String capture)throws Exception{
        ui(()->{try{Button[] nav=(Button[])field("navigation");nav[index].performClick();require(((Integer)field("selectedPage"))==index,"page-selection-required");String[] fields={"overview","results","operations"};for(int n=0;n<3;n++)require(((View)field(fields[n])).getVisibility()==(n==index?View.VISIBLE:View.GONE),"page-isolation-required");}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();Thread.sleep(350);
        ui(()->{try{ScrollView scroll=(ScrollView)field("scroll");scroll.scrollTo(0,0);View pane=(View)field(new String[]{"overview","results","operations"}[index]);android.graphics.Rect visible=new android.graphics.Rect();report.putInt("page_"+index+"_height",pane.getHeight());report.putInt("page_"+index+"_top",pane.getTop());require(pane.getHeight()>0&&pane.getGlobalVisibleRect(visible),"page-content-visible-required");View decor=activity.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));try(OutputStream out=new FileOutputStream(new File(activity.getFilesDir(),capture+".png"))){require(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"capture-required");}finally{bitmap.recycle();}}catch(Exception e){throw new IllegalStateException(e);}});
    }
    private boolean contains(View view,String match){if(view instanceof TextView&&((TextView)view).getText().toString().contains(match))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),match))return true;}return false;}
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    public void onStart(){boolean success=false;report.putInt("schema",1);report.putInt("sdk",Build.VERSION.SDK_INT);report.putBoolean("new_call_or_sms",false);report.putBoolean("engine_mutation_requested",false);
        try{
            ApplicationInfo target=getTargetContext().getPackageManager().getApplicationInfo(TARGET,0);require(android.os.Process.myUid()==target.uid&&target.uid>=10000,"target-own-uid-required");report.putBoolean("target_own_uid_verified",true);
            String expected=args.getString("target_sha256","");require(expected.matches("[a-f0-9]{64}"),"expected-digest-required");MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(target.sourceDir)){byte[] bytes=new byte[8192];for(int n;(n=in.read(bytes))>=0;)if(n>0)hash.update(bytes,0,n);}StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));require(expected.equals(digest.toString()),"tested-tool-required");report.putBoolean("tested_tool_bytes_verified",true);
            activity=startActivitySync(new Intent().setClassName(TARGET,TARGET+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            page(0,"material-overview-initial");JSONObject empty=inspect(0);require(!empty.has("sub_id")&&!empty.has("ims_status"),"empty-slot-isolation-required");
            ui(()->{try{for(String name:new String[]{"trial","enable","reload","rebind","install"})require(!((Button)field(name)).isEnabled(),"empty-slot-mutation-disabled-required");}catch(Exception e){throw new IllegalStateException(e);}});report.putBoolean("empty_slot_isolated",true);
            JSONObject active=inspect(1);require(active.optInt("ims_transport")==2&&active.optBoolean("voice")&&active.optBoolean("sms"),"wlan-registration-required");
            JSONObject owner=active.optJSONObject("providers");require(owner!=null&&"ACTIVE".equals(owner.optString("transaction"))&&owner.optInt("components")==7&&"ENABLED".equals(owner.optString("persistent")),"full-owner-preserved-required");report.putBoolean("full_owner_preserved",true);
            ui(()->{try{require(contains(activity.getWindow().getDecorView(),"Wi-Fi Calling 已注册"),"registered-overview-required");require(contains(activity.getWindow().getDecorView(),"最近一次短信发送链路")&&contains(activity.getWindow().getDecorView(),"不能据此确认 Wi-Fi 短信分发器就绪"),"diagnostic-scope-preserved-required");}catch(Exception e){throw new IllegalStateException(e);}});report.putBoolean("diagnostic_scope_text_preserved",true);
            report.putBoolean("native_sms_support_observed",active.optBoolean("native_sms_ims_supported"));JSONObject dispatcher=active.optJSONObject("sms_dispatcher_window");report.putBoolean("native_dispatcher_available",dispatcher!=null&&dispatcher.optBoolean("available"));
            page(0,"material-overview");page(1,"material-diagnostics");page(2,"material-operations");page(0,"material-overview");report.putBoolean("three_pages_verified",true);report.putBoolean("busy_guards_verified",true);report.putBoolean("own_view_captures_saved",true);report.putString("status","passed");success=true;
        }catch(Throwable e){report.putString("status","failed");report.putString("error",e.getClass().getSimpleName());String code=e.getMessage();report.putString("reason",code!=null&&code.matches("[a-z-]{1,64}")?code:"unclassified");}
        finally{if(activity!=null)try{runOnMainSync(()->activity.finish());}catch(Exception ignored){}finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,report);}
    }
}
