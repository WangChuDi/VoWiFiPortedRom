// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.content.Context;
import android.os.*;
import android.telephony.CarrierConfigManager;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;

/** Query existing selected service, with PID+start-time continuity and fresh nonce. */
final class RootServiceAbi {
    static JSONObject observe(Context context,String role,String pkg,String selectionKey,int slot,int sub,boolean owner){
        JSONObject result=new JSONObject();
        try{
            if(!selected(context,sub,selectionKey,pkg))return state("NOT_SELECTED");
            if(!owner)return state("NO_ACTIVE_OWNER");RootDiagnostics.requireSmsOwner(context,slot,sub);
            PlatformHealth.Sample before=PlatformHealth.readProcess(pkg);
            if(!Boolean.TRUE.equals(before.present)||before.identity==null)return state("PROCESS_UNAVAILABLE");
            int pid=Integer.parseInt(before.identity.substring(0,before.identity.indexOf(':')));
            String bootValue=RootDiagnostics.command(2,"settings","get","global","boot_count").trim();if(!bootValue.matches("[0-9]{1,9}"))throw new IllegalArgumentException("abi-boot");int boot=Integer.parseInt(bootValue);
            String nonce=UUID.randomUUID().toString().replace("-","").substring(0,16);long began=SystemClock.elapsedRealtime();
            String raw=RootDiagnostics.command(3,"content","call","--uri","content://"+pkg+".status","--method","runtime-abi","--arg",role+":"+slot+":"+sub+":"+nonce);
            if(raw.length()>16384)throw new IllegalArgumentException("abi-too-large");Matcher m=Pattern.compile("snapshot=(\\{[^\\r\\n]*\\})\\}\\]").matcher(raw);if(!m.find())throw new IllegalArgumentException("abi-no-snapshot");
            JSONObject observed=ServiceAbiSnapshot.validate(new JSONObject(m.group(1)),role,slot,sub,nonce,pid,boot,Build.VERSION.SDK_INT,began,SystemClock.elapsedRealtime());
            PlatformHealth.Sample after=PlatformHealth.readProcess(pkg);RootDiagnostics.requireSmsOwner(context,slot,sub);
            if(!Boolean.TRUE.equals(after.present)||!before.identity.equals(after.identity)||!selected(context,sub,selectionKey,pkg))return state("INSTANCE_CHANGED");
            observed.put("status",observed.getBoolean("observed")?"OBSERVED":"NO_ACTIVE_OWNER");
            for(String key:new String[]{"pid","nonce","boot"})observed.remove(key);return observed;
        }catch(Exception unavailable){try{result.put("status","UNAVAILABLE").put("sample_elapsed",SystemClock.elapsedRealtime());}catch(Exception ignored){}return result;}
    }
    private static boolean selected(Context context,int sub,String key,String pkg)throws Exception{
        if(sub<0)return false;CarrierConfigManager manager=context.getSystemService(CarrierConfigManager.class);if(manager==null)throw new IllegalStateException("abi-config-unavailable");
        android.os.PersistableBundle c=manager.getConfigForSubId(sub);if(c==null)throw new IllegalStateException("abi-config-unavailable");return pkg.equals(c.getString(key));
    }
    private static JSONObject state(String state)throws Exception{return new JSONObject().put("status",state).put("sample_elapsed",SystemClock.elapsedRealtime());}
}
