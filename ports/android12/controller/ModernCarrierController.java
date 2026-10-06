// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.File;
import java.util.*;
import org.json.JSONObject;

/** Root production command backend; lifecycle/resource supervision is a separate caller. */
public final class ModernCarrierController {
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("owner-check","snapshot","apply","verify","restore","confirm-restored","status"));
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean success=false;
        try{
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length<3||!ACTIONS.contains(args[0]))throw new SecurityException("modern-root-command-required");
            String action=args[0];boolean maskAction="apply".equals(action)||"verify".equals(action);
            if(args.length!=(maskAction?4:3))throw new IllegalArgumentException("explicit-command-owner-required");
            int slot=Integer.parseInt(args[1]),sub=Integer.parseInt(args[2]),mask=maskAction?Integer.parseInt(args[3]):0;
            if(slot<0||slot>7||sub<0||(maskAction&&(mask<1||mask>7)))throw new IllegalArgumentException("command-owner-or-mask-invalid");
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("action",action);
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();
            Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer"),manager=Class.forName("android.os.TelephonyServiceManager");
            if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            boolean owner=false;
            if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot&&info.getSubscriptionId()==sub){
                TelephonyManager phone=context.getSystemService(TelephonyManager.class);
                owner=phone.getSimState(slot)==TelephonyManager.SIM_STATE_READY&&"23415".equals(phone.createForSubscriptionId(sub).getSimOperator());
            }
            if(!owner)throw new SecurityException("ready-VOXI-owner-required");
            result.put("owner_profile_confirmed",true);
            File state=new File("/data/adb/codex_vowifi_stack_modern/transactions/slot-"+slot+"-sub-"+sub);
            if("owner-check".equals(action)){success=true;}
            else if("status".equals(action)&&!state.exists()){result.put("transaction_present",false);success=true;}
            else{
                // Inspecting an existing transaction must not prepare a new one.
                if(!"snapshot".equals(action)&&!state.isDirectory())throw new IllegalStateException("transaction-unavailable");
                try(ModernProviderTransaction transaction=new ModernProviderTransaction(context,slot,sub,state,false)){
                    switch(action){
                    case "snapshot":transaction.snapshot();result.put("snapshot_verified",true);break;
                    case "apply":ModernPhoneIdle.requireIdle(context);transaction.apply(mask);result.put("selected_live_and_persisted",transaction.verifySelection(mask));if(!result.getBoolean("selected_live_and_persisted"))throw new IllegalStateException("selection-unconfirmed");break;
                    case "verify":if(!transaction.verifySelection(mask))throw new IllegalStateException("selection-unconfirmed");result.put("selected_live_and_persisted",true);break;
                    case "restore":
                        ModernPhoneIdle.requireIdle(context);transaction.restore();
                        if(!transaction.confirmRestored())transaction.reloadRestored();
                        if(!transaction.confirmRestored())throw new IllegalStateException("restoration-unconfirmed");
                        result.put("entire_original_config_restored",true);break;
                    case "confirm-restored":if(!transaction.confirmRestored())throw new IllegalStateException("restoration-unconfirmed");result.put("entire_original_config_restored",true);break;
                    case "status":result.put("transaction_present",true);break;
                    }
                    result.put("phase",transaction.statePhase());success=true;
                }
            }
            result.put("production_installer_supervisor_verified",false).put("carrier_call_sms_verified",false).put("dual_sim_verified",false);
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success&&!result.has("error")?0:1);
    }
}
