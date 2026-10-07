// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Root requests metadata; the actual ISms transaction runs in the IMS application's UID. */
public final class RootNativeSmsQuery {
    public static JSONObject observe(Context context,int slot,int sub)throws Exception{
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
        RootDiagnostics.requireSmsOwner(context,slot,sub);
        PlatformHealth.Sample before=PlatformHealth.readProcess("me.phh.ims");
        if(!Boolean.TRUE.equals(before.present)||before.identity==null)throw new IOException("native-query-process-unavailable");
        int pid=Integer.parseInt(before.identity.split(":",-1)[0]);
        String boot=RootDiagnostics.command(2,"settings","get","global","boot_count").trim();if(!boot.matches("[0-9]{1,9}"))throw new IOException("native-query-boot-unavailable");
        String nonce=UUID.randomUUID().toString().replace("-","").substring(0,16);long begin=SystemClock.elapsedRealtime();
        String output=RootDiagnostics.command(5,"content","call","--uri","content://me.phh.ims.status","--method","native-sms","--arg","ims:"+slot+":"+sub+":"+nonce);
        if(output.length()>8192)throw new IOException("native-query-result-too-large");
        Matcher found=Pattern.compile("snapshot=(\\{[^\\r\\n]*\\})\\}\\]").matcher(output);if(!found.find())throw new IOException("native-query-result-unavailable");
        JSONObject raw=new JSONObject(found.group(1));Map<String,Object> fields=new LinkedHashMap<>();for(Iterator<String> keys=raw.keys();keys.hasNext();){String key=keys.next();fields.put(key,raw.get(key));}
        Map<String,Object> clean=NativeSmsQuerySnapshot.validate(fields,slot,sub,nonce,pid,Integer.parseInt(boot),begin,SystemClock.elapsedRealtime());
        RootDiagnostics.requireSmsOwner(context,slot,sub);
        if(!"stable".equals(PlatformHealth.compare(before,PlatformHealth.readProcess("me.phh.ims")).optString("state")))throw new IOException("native-query-process-changed");
        return new JSONObject(clean);
    }
    public static void main(String[] args){JSONObject out=new JSONObject();boolean success=false;
        try{
            if(android.os.Process.myUid()!=0||args.length!=2)throw new SecurityException("root-native-query-required");
            int slot=Integer.parseInt(args[0]),sub=Integer.parseInt(args[1]);if(slot<0||slot>7||sub<0)throw new IllegalArgumentException("native-query-selection");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();RootDiagnostics.initializeTelephony();out=observe(context,slot,sub);success=true;
        }catch(Throwable failure){try{out.put("status","unavailable").put("error",failure.getClass().getSimpleName()).put("read_only",true);}catch(Exception ignored){}}
        System.out.println(out);System.exit(success?0:1);
    }
}
