// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

/** Production CLI has fixed paths; emulator fixture entry remains separate. */
public final class ModernInstallationController {
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean success=false;
        try{
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length!=1||!Arrays.asList("check","prepare","restore","status").contains(args[0]))throw new SecurityException("modern-installation-command-refused");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();initializeTelephony();
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("action",args[0]);
            try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,new File("/data/adb/modules/codex_vowifi_stack_modern"),new File("/data/adb/codex_vowifi_stack_modern/installation"),false)){
                switch(args[0]){
                case "prepare":transaction.prepare();result.put("installation_permissions_ready",true);break;
                case "restore":transaction.restore();result.put("original_permission_policy_restored",true);break;
                case "check":if(!transaction.ready())throw new IllegalStateException("installation-permissions-not-ready");result.put("installation_permissions_ready",true);break;
                }
                result.put("phase",transaction.phase());success=true;
            }
            result.put("carrier_selection_changed",false).put("magisk_mount_verified",false).put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false);
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success&&!result.has("error")?0:1);
    }
    static void initializeTelephony()throws Exception {
        Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer"),manager=Class.forName("android.os.TelephonyServiceManager");
        if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
    }
}
