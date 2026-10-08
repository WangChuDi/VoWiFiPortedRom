// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.networktest;
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

/** Real tool UID, real root IPC and network button; never changes provider selection. */
public final class NetworkUiTrial extends Instrumentation {
    private Activity activity;private Throwable failure;private Bundle args;private final Bundle report=new Bundle();
    private void require(boolean v,String code)throws IOException{if(!v)throw new IOException(code);}
    private Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void ui(Runnable action)throws Exception{failure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});require(failure==null,"ui-operation-unavailable");}
    private TextView find(View v,String prefix){if(v instanceof TextView&&((TextView)v).getText().toString().contains(prefix))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=find(((ViewGroup)v).getChildAt(i),prefix);if(t!=null)return t;}return null;}
    private JSONObject check(int slot,boolean network)throws Exception{
        ui(()->{try{int index=((List<?>)field("slots")).indexOf(slot);require(index>=0,"slot-required");((Spinner)field("sims")).setSelection(index);}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();
        ui(()->{try{((Button)field(network?"networkCheck":"check")).performClick();require((Boolean)field("busy"),"busy-required");for(String name:new String[]{"check","networkCheck","trial","enable","reload","rebind","rollback","install"})require(!((Button)field(name)).isEnabled(),"busy-guard-required");}catch(Exception e){throw new IllegalStateException(e);}});
        long deadline=SystemClock.elapsedRealtime()+65000;
        while(SystemClock.elapsedRealtime()<deadline){final boolean[] busy={true};final Object[] value={null};ui(()->{try{busy[0]=(Boolean)field("busy");value[0]=field("last");}catch(Exception e){throw new IllegalStateException(e);}});if(!busy[0]){require(value[0] instanceof JSONObject,"fresh-result-required");JSONObject result=(JSONObject)value[0];require(result.optInt("slot",-1)==slot&&result.optBoolean("diagnostic_complete"),"complete-owner-result-required");return result;}Thread.sleep(200);}
        throw new IOException("diagnostic-deadline");
    }
    private void capture(String name,View anchor)throws Exception{
        ui(()->{try{require(!activity.getSystemService(KeyguardManager.class).isKeyguardLocked()&&activity.getSystemService(PowerManager.class).isInteractive(),"unlocked-visible-screen-required");ScrollView scroll=(ScrollView)field("scroll");if(anchor==null)scroll.scrollTo(0,0);else{android.graphics.Rect r=new android.graphics.Rect();anchor.getDrawingRect(r);scroll.offsetDescendantRectToMyCoords(anchor,r);scroll.smoothScrollTo(0,Math.max(0,r.top-20));}}catch(Exception e){throw new IllegalStateException(e);}});waitForIdleSync();Thread.sleep(400);
        ui(()->{try{View decor=activity.getWindow().getDecorView();require(decor.getWidth()>0&&decor.getHeight()>0,"measured-window-required");Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));try(OutputStream out=new FileOutputStream(new File(activity.getFilesDir(),name+".png"))){require(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"capture-required");}finally{bitmap.recycle();}}catch(Exception e){throw new IllegalStateException(e);}});
    }
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    public void onStart(){boolean passed=false;report.putInt("schema",1);report.putBoolean("engine_mutation_requested",false);report.putBoolean("new_call_or_sms",false);report.putBoolean("sim_authentication_requested",false);report.putBoolean("active_ike_sa_init_requested",true);
        try{
            ApplicationInfo info=getTargetContext().getPackageManager().getApplicationInfo("dev.codex.vowifi.tool",0);require(android.os.Process.myUid()==info.uid&&info.uid>=10000,"target-own-uid-required");
            MessageDigest h=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(info.sourceDir)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)h.update(b,0,n);}StringBuilder sha=new StringBuilder();for(byte b:h.digest())sha.append(String.format(Locale.ROOT,"%02x",b&255));require(sha.toString().equals(args.getString("target_sha256","")),"exact-target-required");report.putBoolean("target_own_uid_verified",true);report.putBoolean("tested_tool_bytes_verified",true);
            activity=startActivitySync(new Intent().setClassName("dev.codex.vowifi.tool","dev.codex.vowifi.tool.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(!activity.getSystemService(KeyguardManager.class).isKeyguardLocked(),"unlocked-screen-required");
            JSONObject empty=check(0,false);require(!empty.has("sub_id")&&!empty.has("apn_details"),"empty-slot-isolation-required");report.putBoolean("empty_slot_isolated",true);
            JSONObject active=check(1,true);JSONObject net=active.optJSONObject("network_detection");require(net!=null&&net.optBoolean("active_probe")&&"COMPLETED".equals(net.optString("status"))&&net.optJSONArray("ports").length()>0,"network-result-required");report.putBoolean("network_button_and_root_ipc_verified",true);
            JSONObject apns=active.optJSONObject("apn_details"),row=apns==null?null:apns.optJSONObject("preferred");require(row!=null&&row.has("password")&&row.has("mmsc")&&row.has("mmsport")&&row.has("type"),"detailed-apn-required");
            JSONObject owner=active.optJSONObject("providers");require(owner!=null&&"ACTIVE".equals(owner.optString("transaction"))&&"ENABLED".equals(owner.optString("persistent"))&&owner.optInt("components")==7,"owner-preserved-required");report.putBoolean("full_owner_preserved",true);
            final TextView[] apnToggle={null};
            ui(()->{try{View decor=activity.getWindow().getDecorView();require(find(decor,"网络检测")!=null&&find(decor,"SIM 运营商与归属网")!=null&&find(decor,"23 项是跨版本")!=null&&find(decor,"这一组检查什么")!=null,"explanations-required");apnToggle[0]=find(decor,"APN 与运营商配置");require(apnToggle[0]!=null,"apn-section-required");apnToggle[0].performClick();TextView detail=find(decor,"名称:");require(detail!=null&&detail.getText().toString().contains("MMS Port:"),"full-apn-text-required");if(!row.optString("password").isEmpty()){require(detail.getText().toString().contains("密码: ••••"),"password-masked-required");TextView reveal=find(decor,"查看 APN 密码");require(reveal instanceof Button,"password-button-required");reveal.performClick();require(detail.getText().toString().contains("密码: "+row.optString("password")),"password-reveal-required");reveal.performClick();require(detail.getText().toString().contains("密码: ••••"),"password-remasked-required");}}catch(Exception e){throw new IllegalStateException(e);}});report.putBoolean("apn_fields_and_password_toggle_verified",true);report.putBoolean("busy_guards_verified",true);
            capture("network-detection",null);capture("apn-details",apnToggle[0]);
            ui(()->{try{((Button[])field("navigation"))[2].performClick();require(find(activity.getWindow().getDecorView(),"首次替换需要安装并启用配套 Magisk")!=null,"module-requirement-explained");}catch(Exception e){throw new IllegalStateException(e);}});capture("component-actions",null);
            ui(()->{try{((Button[])field("navigation"))[0].performClick();}catch(Exception e){throw new IllegalStateException(e);}});capture("network-overview",null);
            report.putBoolean("own_view_captures_saved",true);report.putString("status","passed");passed=true;
        }catch(Throwable e){report.putString("status","failed");report.putString("error",e.getClass().getSimpleName());String code=e.getMessage();report.putString("reason",code!=null&&code.matches("[a-z-]{1,64}")?code:"unclassified");}
        finally{if(activity!=null)try{runOnMainSync(()->activity.finish());}catch(Exception ignored){}finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,report);}
    }
}
