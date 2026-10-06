// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.File;
import java.util.List;
import org.json.JSONObject;

/** Read-only modern controller preflight; no identities, paths or dump values emitted. */
public final class ModernPersistenceCheck {
    public static void main(String[] args){
        JSONObject result=new JSONObject();
        try {
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length!=2)
                throw new SecurityException("modern-root-explicit-owner-required");
            int slot=Integer.parseInt(args[0]),sub=Integer.parseInt(args[1]);
            if(slot<0||slot>7||sub<0)throw new IllegalArgumentException("invalid-owner");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();
            Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer");
            Class<?> manager=Class.forName("android.os.TelephonyServiceManager");
            if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)
                init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            SubscriptionInfo selected=null;
            if(active!=null)for(SubscriptionInfo item:active)if(item.getSimSlotIndex()==slot&&item.getSubscriptionId()==sub)selected=item;
            if(selected==null)throw new IllegalStateException("selected-subscription-changed");
            Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
            Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("carrier_config"));
            File state=new File("/data/adb/codex_vowifi_stack_modern/transactions/slot-"+slot+"-sub-"+sub);
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context,selected,api,loader,state);
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("selected_target_confirmed",true);
            result.put("selected_override_present",target.file.isFile());
            if(target.file.isFile())result.put("selected_stream_read",ModernCarrierOverrideFiles.readBundle(target.file)!=null);
            String name=target.file.getName().replace("-override-","-");
            File cache=new File(target.directory,name);
            result.put("platform_cache_present",cache.isFile());
            if(cache.isFile())result.put("platform_cache_stream_read",ModernCarrierOverrideFiles.readBundle(cache)!=null);
            result.put("transient_override_empty",ModernCarrierBaseline.hasEmptyTransientOverride(slot));
            result.put("modern_production_controller_verified",false);
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(result.has("error")?1:0);
    }
}
