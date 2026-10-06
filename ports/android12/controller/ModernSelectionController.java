// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Root production owner CLI. Tokens are private worker data, not UI telemetry. */
public final class ModernSelectionController {
    static final File MODULE=new File("/data/adb/modules/codex_vowifi_stack_modern");
    static final File INSTALLATION=new File("/data/adb/codex_vowifi_stack_modern/installation");
    static final File COORDINATION=new File("/data/adb/codex_vowifi_stack_modern/coordination");
    static final File CARRIERS=new File("/data/adb/codex_vowifi_stack_modern/transactions");
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("owner-check","trial","retain","renew","verify","restore","recover","status"));
    static Context context()throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-controller-required");
        Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();return context;
    }
    static void owner(Context context,int slot,int sub)throws Exception {
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot&&info.getSubscriptionId()==sub) {
            TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            if(phone.getSimState(slot)==TelephonyManager.SIM_STATE_READY&&"23415".equals(phone.createForSubscriptionId(sub).getSimOperator()))return;
        }
        throw new SecurityException("ready-VOXI-owner-required");
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length<3||!ACTIONS.contains(args[0]))throw new SecurityException("modern-selection-command-refused");
            String action=args[0];boolean mask="trial".equals(action),token=Arrays.asList("retain","renew","verify","restore").contains(action);
            if(args.length!=((mask||token)?4:3)||!args[1].matches("[0-7]")||!args[2].matches("0|[1-9][0-9]{0,9}")||(mask&&!args[3].matches("[1-7]"))||(token&&!args[3].matches("[0-9a-f]{32}")))throw new IllegalArgumentException("fixed-selection-arguments-required");
            int slot=Integer.parseInt(args[1]),sub=Integer.parseInt(args[2]);Context context=context();boolean recovery=Arrays.asList("restore","recover","status").contains(action);
            if(!recovery)owner(context,slot,sub);
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("action",action).put("owner_profile_confirmed",!recovery);
            String name="slot-"+slot+"-sub-"+sub;File ownerRecord=new File(COORDINATION,"owners/"+name+"/selection.properties");ModernStateFiles.canonical(ownerRecord);
            if("owner-check".equals(action))success=true;
            else if("status".equals(action)&&!ownerRecord.exists()){owner(context,slot,sub);output.put("owner_profile_confirmed",true).put("selection_present",false);success=true;}
            else {
                if(!"trial".equals(action)&&!ownerRecord.isFile())throw new IOException("selection-record-unavailable");
                try(ModernSelectionTransaction transaction=recovery?ModernSelectionTransaction.recovery(context,slot,sub,MODULE,INSTALLATION,COORDINATION,new File(CARRIERS,name),false,null):new ModernSelectionTransaction(context,slot,sub,MODULE,INSTALLATION,COORDINATION,new File(CARRIERS,name),false)) {
                    output.put("owner_profile_confirmed",transaction.liveOwner()).put("recorded_owner_confirmed",true);
                    switch(action) {
                    case "trial":output.put("owner_token",transaction.trial(Integer.parseInt(args[3])));break;
                    case "retain":transaction.retain(args[3]);break;
                    case "renew":output.put("lease_renewed",transaction.renew(args[3]));break;
                    case "verify":if(!transaction.verify(args[3]))throw new IOException("selection-lease-unconfirmed");output.put("selected_live_disk_and_lease",true);break;
                    case "restore":transaction.restore(args[3]);break;
                    case "recover":if("ARCHIVING".equals(transaction.status().getProperty("phase")))transaction.finishArchive(transaction.currentToken());else transaction.restore(transaction.currentToken());break;
                    }
                    if(ownerRecord.isFile()) {
                        Properties state=transaction.status();output.put("selection_present",true);
                        for(String key:state.stringPropertyNames())output.put(key,"recovery_pending_owner".equals(key)?Boolean.parseBoolean(state.getProperty(key)):state.getProperty(key));
                        if("RESTORED".equals(state.getProperty("phase")))output.put("carrier_config_restored",transaction.originalCarrierRestored());
                    }else output.put("selection_present",false);
                    success=true;
                }
            }
            output.put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false).put("modern_device_lifecycle_verified",false);
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
