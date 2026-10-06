// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.File;
import java.util.*;
import org.json.JSONObject;

/** Exercise the production carrier transaction only on a disposable non-VOXI emulator. */
public final class ModernPersistentFrameworkTrial {
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean success=false;
        try {
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length!=0||!"1".equals(SystemProperties.get("ro.kernel.qemu")))throw new SecurityException("modern-test-emulator-required");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();
            Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer");
            Class<?> manager=Class.forName("android.os.TelephonyServiceManager");
            if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)
                init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1)throw new IllegalStateException("single-test-subscription-required");
            SubscriptionInfo selected=active.get(0);int slot=selected.getSimSlotIndex(),sub=selected.getSubscriptionId();
            String operator=context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub).getSimOperator();
            if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-test-owner-required");
            Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
            Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("carrier_config"));
            File state=new File("/data/local/tmp/codex-modern-persistence-tests/slot-"+slot+"-sub-"+sub+"-"+UUID.randomUUID().toString().replace("-",""));
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context,selected,api,loader,state);
            if(target.file.exists())throw new IllegalStateException("fresh-test-persistence-baseline-required");
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("mask",2).put("persistent",true);
            boolean attempted=false,applied=false,restored=false;
            try(ModernProviderTransaction transaction=new ModernProviderTransaction(context,slot,sub,state,true)){
                transaction.snapshot();result.put("production_snapshot",true);
                attempted=true;
                try{transaction.apply(2);applied=true;result.put("selected_live_and_native_stream_file",true);}
                finally{
                    // Release the first instance even after ordinary failure.
                    // Restoration below must reconstruct from private disk state.
                    result.put("transaction_reopen_required",true);
                }
            } finally {
                if(attempted){
                    try(ModernProviderTransaction recovery=new ModernProviderTransaction(context,slot,sub,state,true)){
                        recovery.restore();result.put("reopened_transaction_restored",true);
                        for(int i=0;i<20;i++){if(recovery.confirmRestored()){restored=true;break;}Thread.sleep(500);}
                        result.put("entire_original_config_restored",restored).put("selected_override_absent",!target.file.exists());
                        recovery.restore();
                        result.put("restoration_retry_safe",recovery.confirmRestored()&&!target.file.exists());
                    }
                }
            }
            success=applied&&restored&&!target.file.exists()&&result.optBoolean("restoration_retry_safe");
            result.put("carrier_registration_verified",false).put("voice_sms_verified",false).put("dual_sim_verified",false);
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success?0:1);
    }
}
