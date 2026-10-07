// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import java.util.*;
import org.json.*;
/** Read-only context boundaries. Exception messages and raw stack traces stay private. */
public final class Api37ContextStages {
    static void stage(String value)throws Exception{System.out.println(new JSONObject().put("schema",1).put("sdk",37).put("checkpoint",value));System.out.flush();}
    public static void main(String[] args){JSONObject result=new JSONObject();boolean ok=false;
        try{
            if(args.length!=0||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT!=37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!"CodexVoWiFiApi37".equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-api37-required");
            result.put("schema",1).put("sdk",37).put("read_only",true).put("physical_phone_modified",false).put("raw_messages_exported",false);
            result.put("initial_thread_looper_present",Looper.myLooper()!=null).put("initial_main_looper_present",Looper.getMainLooper()!=null);
            stage("looper-prepare");Looper.prepareMainLooper();stage("looper-prepared");
            stage("activity-thread-main");ActivityThread thread=ActivityThread.systemMain();stage("activity-thread-main-created");
            stage("system-context");Context context=thread.getSystemContext();stage("system-context-created");
            stage("telephony-bootstrap");Class<?> initializer=Class.forName("android.telephony.TelephonyFrameworkInitializer"),manager=Class.forName("android.os.TelephonyServiceManager");if(initializer.getMethod("getTelephonyServiceManager").invoke(null)==null)initializer.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());stage("telephony-bootstrap-ready");
            stage("settings-content-query");String value=Settings.Global.getString(context.getContentResolver(),"boot_count");result.put("settings_integer_observed",value!=null&&value.matches("[0-9]+"));stage("settings-content-query-completed");ok=true;
        }catch(Throwable error){try{
            JSONArray chain=new JSONArray(),frames=new JSONArray();Set<String> seen=new HashSet<>();Throwable next=error;int depth=0;
            while(next!=null&&depth++<4){String type=next.getClass().getName();chain.put(type.matches("(?:java\\.(?:lang|io)|android\\.(?:os|content\\.res))\\.[A-Za-z0-9_$]+")?type:"other-exception-class");
                for(StackTraceElement frame:next.getStackTrace()){String cls=frame.getClassName(),method=frame.getMethodName();if(frames.length()<16&&cls.matches("(?:android\\.(?:app|os|content|telephony|provider)|com\\.android\\.internal)[A-Za-z0-9_.$]*")&&method.matches("[A-Za-z0-9_$<>]+")&&frame.getLineNumber()>=0){String key=cls+"."+method+":"+frame.getLineNumber();if(seen.add(key))frames.put(new JSONObject().put("class",cls).put("method",method).put("line",frame.getLineNumber()));}}
                next=next.getCause();
            }result.put("exception_chain",chain).put("fixed_framework_frames",frames);
        }catch(Exception ignored){}}
        try{result.put("status",ok?"observed":"failed");}catch(Exception ignored){}System.out.println(result);System.out.flush();System.exit(ok?0:1);
    }
}
