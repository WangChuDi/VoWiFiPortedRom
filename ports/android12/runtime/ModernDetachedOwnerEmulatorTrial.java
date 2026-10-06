// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Uses an injected absence observation, never claims a removed SIM or second live SIM. */
public final class ModernDetachedOwnerEmulatorTrial {
    private static Map<String,String> tree(File directory)throws Exception {
        ModernStateFiles.canonical(directory);Map<String,String> result=new TreeMap<>();
        if(!directory.exists())return result;if(!directory.isDirectory())throw new IOException("fixture-tree-refused");
        File[] children=directory.listFiles();if(children==null||children.length>64)throw new IOException("fixture-tree-unavailable");
        for(File child:children){ModernStateFiles.canonical(child);if(child.isDirectory())for(Map.Entry<String,String> entry:tree(child).entrySet())result.put(child.getName()+"/"+entry.getKey(),entry.getValue());else result.put(child.getName(),ModernInstallationTransaction.digest(child));}
        return result;
    }
    private static String[] lease(int slot)throws Exception {String[] result=new String[3];int i=0;for(String field:new String[]{"sub","boot","until"})result[i++]=ModernRootSettings.get("codex_wfc_stack_slot_"+slot+"_"+field);return result;}
    private static Properties rolePolicy(Context context)throws Exception {Properties value=new Properties();value.setProperty("mask","7");ModernSelectedPermissions.snapshot(context,value);return value;}
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!"detached-recovery".equals(args[0])||!args[1].matches("[0-9a-f]{32}")||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-detached-fixture-required");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0)throw new SecurityException("single-fake-subscription-required");int sub=active.get(0).getSubscriptionId();
            ModernOwnerPresence.requireLive(ModernOwnerPresence.observe(context,0,sub,true));
            File root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]),module=new File(root,"module"),installation=new File(root,"state"),coordination=new File(root,"selection");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((args[1]+":selection").getBytes("UTF-8"));StringBuilder suffix=new StringBuilder();for(byte b:hash)suffix.append(String.format(Locale.ROOT,"%02x",b&255));
            File carriers=new File("/data/local/tmp/codex-modern-persistence-tests"),carrier=new File(carriers,"slot-0-sub-"+sub+"-"+suffix.substring(0,32));
            ModernStateFiles ownerFiles=new ModernStateFiles(new File(coordination,"owners/slot-0-sub-"+sub));Properties before=ownerFiles.read("selection.properties");String token=before.getProperty("token");
            if(!"ACTIVE".equals(before.getProperty("phase"))||!"7".equals(before.getProperty("mask")))throw new IOException("active-full-fixture-required");
            String[] peerLease=lease(1);Properties policyBefore=rolePolicy(context);Map<String,String> carrierBefore=tree(carrier),resourcesBefore=tree(new File(coordination,"resources")),installationBefore=tree(installation);
            Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("carrier_config"));
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context,active.get(0),api,loader,carrier);String overrideBefore=ModernInstallationTransaction.digest(target.file);
            AtomicBoolean unknown=new AtomicBoolean(false);ModernOwnerPresence.Source absent=()->{if(unknown.get())throw new SecurityException("owner-inventory-unavailable");return ModernOwnerPresence.State.ABSENT;};
            try(ModernControllerLock held=new ModernControllerLock(carriers,true);ModernSelectionTransaction transaction=ModernSelectionTransaction.recoveryForEmulator(context,0,sub,module,installation,coordination,carrier,held,absent)) {
                int refused=0;try{transaction.trial(7);}catch(SecurityException expected){refused++;}try{transaction.retain(token);}catch(SecurityException expected){refused++;}try{transaction.renew(token);}catch(SecurityException expected){refused++;}try{transaction.verify(token);}catch(SecurityException expected){refused++;}
                if(refused!=4)throw new IOException("detached-enable-refusal-unconfirmed");
                String recordBefore=ModernInstallationTransaction.digest(ownerFiles.file("selection.properties"));String[] ownBefore=lease(0);unknown.set(true);
                boolean unknownRefused=false;try{transaction.restore(token);}catch(SecurityException expected){unknownRefused=true;}finally{unknown.set(false);}
                if(!unknownRefused||!recordBefore.equals(ModernInstallationTransaction.digest(ownerFiles.file("selection.properties")))||!Arrays.equals(ownBefore,lease(0)))throw new IOException("unknown-owner-mutation-refused-unconfirmed");
                transaction.restore(token);transaction.restore(token);
                Properties pending=ownerFiles.read("selection.properties");
                if(!"RESTORING".equals(pending.getProperty("phase"))||!"true".equals(transaction.status().getProperty("recovery_pending_owner")))throw new IOException("detached-pending-state-unconfirmed");
                for(String field:new String[]{"sub","boot","until"}){String original=Boolean.parseBoolean(before.getProperty("before."+field+".present"))?before.getProperty("before."+field+".value"):null;if(!Objects.equals(original,ModernRootSettings.get("codex_wfc_stack_slot_0_"+field)))throw new IOException("detached-lease-restore-unconfirmed");}
                Properties policyAfter=rolePolicy(context);
                boolean carrierUnchanged=carrierBefore.equals(tree(carrier))&&overrideBefore.equals(ModernInstallationTransaction.digest(target.file)),resourcesUnchanged=resourcesBefore.equals(tree(new File(coordination,"resources"))),installationUnchanged=installationBefore.equals(tree(installation)),peerUnchanged=Arrays.equals(peerLease,lease(1)),authorizationUnchanged=ModernPermissionFlags.sameRecordedPolicy(policyBefore,policyAfter);
                org.json.JSONArray changes=new org.json.JSONArray();
                for(int index=0;index<3;index++)for(String field:new String[]{"flags","grant"}){String key="role."+index+"."+field;if(!Objects.equals(policyBefore.getProperty(key),policyAfter.getProperty(key)))changes.put(new JSONObject().put("field",key).put("before",policyBefore.getProperty(key)).put("after",policyAfter.getProperty(key)));}
                output.put("absent_carrier_journal_and_file_unchanged",carrierUnchanged).put("absent_shared_journals_unchanged",resourcesUnchanged).put("absent_installation_journal_unchanged",installationUnchanged).put("absent_peer_lease_unchanged",peerUnchanged).put("native_authorization_policy_unchanged_while_absent",authorizationUnchanged).put("native_raw_role_flags_and_grants_unchanged_while_absent",policyBefore.equals(policyAfter)).put("native_role_observation_changes",changes).put("permission_policy_excluded_informational_mask",ModernPermissionFlags.INFORMATIONAL);
                if(!carrierUnchanged||!resourcesUnchanged||!installationUnchanged||!authorizationUnchanged||!peerUnchanged)throw new IOException("detached-shared-or-carrier-policy-changed");
            }
            // A fresh process-style handle with real identity resumes the saved
            // originals. No snapshot, replacement original, or fixture deletion.
            try(ModernSelectionTransaction returned=ModernSelectionTransaction.recovery(context,0,sub,module,installation,coordination,carrier,true,null)) {
                returned.restore(token);returned.restore(token);returned.confirmRestoredOwner();
                if(!"RESTORED".equals(returned.status().getProperty("phase"))||!"false".equals(returned.status().getProperty("recovery_pending_owner")))throw new IOException("returned-owner-full-restore-unconfirmed");
            }
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]).put("absent_owner_enable_retain_renew_verify_refused",true).put("unknown_owner_refused_before_mutation",true).put("own_lease_restored_and_pending_explicit",true).put("carrier_files_and_shared_resources_unchanged_while_absent",true).put("peer_slot_lease_unchanged",true).put("returned_original_owner_full_config_and_roles_restored",true).put("repeat_recovery_verified",true).put("simulated_owner_absence",true).put("actual_sim_removal_verified",false).put("dual_active_sim_verified",false).put("carrier_call_sms_verified",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
