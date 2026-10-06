// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Actual per-owner provider selection in fixed fake-SIM guests, not carrier proof. */
public final class ModernSelectionEmulatorTrial {
    private static File root,coordination,carrier,module,installation;private static Context context;private static int sub;
    private static ModernSelectionTransaction open()throws Exception {return new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true);}
    private static ModernStateFiles privateFiles()throws Exception {return new ModernStateFiles(new File(root,"selection-fixture"));}
    private static String token()throws Exception {String token=privateFiles().read("owner.properties").getProperty("token","");if(!token.matches("[0-9a-f]{32}"))throw new IOException("fixture-token-unavailable");return token;}
    private static void saveToken(String token)throws Exception {Properties value=new Properties();value.setProperty("token",token);privateFiles().write("owner.properties",value);}
    private static File record(){return new File(coordination,"owners/slot-0-sub-"+sub+"/selection.properties");}
    private static String digest(File path)throws Exception {return ModernInstallationTransaction.digest(path);}
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!args[1].matches("[0-9a-f]{32}")||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-test-emulator-required");
            if(!Arrays.asList("selection-trial","selection-reopen","selection-partial-lease","selection-foreign-lease","selection-role-handoff","selection-restore","selection-repeat","selection-expire","selection-cleanup").contains(args[0]))throw new IllegalArgumentException("selection-fixture-stage-refused");
            Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0)throw new SecurityException("single-slot-zero-fake-SIM-required");sub=active.get(0).getSubscriptionId();
            root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]);ModernStateFiles.canonical(root);if(!root.isDirectory())throw new IOException("fixture-root-unavailable");
            module=new File(root,"module");installation=new File(root,"state");coordination=new File(root,"selection");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((args[1]+":selection").getBytes("UTF-8"));StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));
            carrier=new File("/data/local/tmp/codex-modern-persistence-tests/slot-0-sub-"+sub+"-"+name.substring(0,32));
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]);
            try(ModernSelectionTransaction transaction=open()) {
                switch(args[0]) {
                case "selection-trial":
                    saveToken(transaction.trial(7));if(!transaction.verify(token()))throw new IOException("fixture-full-selection-unconfirmed");
                    output.put("full_mask_live_disk_and_lease_verified",true);break;
                case "selection-reopen":
                    if(!transaction.verify(token()))throw new IOException("fixture-reopened-selection-unconfirmed");transaction.retain(token());
                    if(!transaction.renew(token())||!"true".equals(transaction.status().getProperty("persistent")))throw new IOException("fixture-retention-unconfirmed");
                    boolean stale=false;String wrong=token().charAt(0)=='0'?"1"+token().substring(1):"0"+token().substring(1);
                    try{transaction.renew(wrong);}catch(SecurityException expected){stale=true;}
                    if(!stale||!transaction.verify(token()))throw new IOException("fixture-stale-token-refusal-unconfirmed");
                    output.put("reopened_retention_and_renewal_verified",true).put("stale_token_refused_without_change",true);break;
                case "selection-foreign-lease":
                    String key="codex_wfc_stack_slot_0_until",before=ModernRootSettings.get(key),stateDigest=digest(record());
                    String foreign=Long.toString(Long.parseLong(before)+700000);ModernRootSettings.put(key,foreign);
                    try {
                        boolean renewRefused=false,restoreRefused=false;
                        try{transaction.renew(token());}catch(IOException expected){renewRefused="external-slot-lease-change-refused".equals(expected.getMessage());}
                        try{transaction.restore(token());}catch(IOException expected){restoreRefused="external-slot-lease-change-refused".equals(expected.getMessage());}
                        if(!renewRefused||!restoreRefused||!foreign.equals(ModernRootSettings.get(key))||!stateDigest.equals(digest(record())))throw new IOException("fixture-foreign-lease-policy-not-preserved");
                        output.put("foreign_lease_renew_and_restore_refused",true);
                    } finally {ModernRootSettings.put(key,before);}
                    break;
                case "selection-partial-lease":
                    ModernStateFiles leaseState=new ModernStateFiles(record().getParentFile());Properties lease=leaseState.read("selection.properties");
                    lease.setProperty("previous.until",lease.getProperty("intent.until"));lease.setProperty("intent.until",Long.toString(SystemClock.elapsedRealtime()+90000));lease.setProperty("lease.phase","PUBLISHING");leaseState.write("selection.properties",lease);
                    ModernRootSettings.put("codex_wfc_stack_slot_0_until","0");
                    if(!transaction.renew(token())||!transaction.verify(token())||!"PUBLISHED".equals(leaseState.read("selection.properties").getProperty("lease.phase")))throw new IOException("fixture-partial-lease-resume-unconfirmed");
                    output.put("partial_lease_publication_resumed",true).put("simulated_lease_handoff",true);break;
                case "selection-role-handoff":
                    ModernStateFiles roleState=new ModernStateFiles(record().getParentFile());Properties role=roleState.read("selection.properties");
                    for(int i:new int[]{0,1,2}) {
                        if(!"true".equals(role.getProperty("role."+i+".apply_requested")))throw new IOException("fixture-role-intent-unavailable");
                        role.remove("role."+i+".observed");
                    }
                    roleState.write("selection.properties",role);
                    if(!transaction.verify(token()))throw new IOException("fixture-role-handoff-selection-changed");
                    output.put("pre_observation_role_handoff_armed",true).put("simulated_role_handoff",true);break;
                case "selection-restore":
                    transaction.restore(token());transaction.restore(token());
                    if(!"RESTORED".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-original-selection-unconfirmed");
                    output.put("original_config_lease_and_mode_restored",true).put("repeat_restore_verified",true);break;
                case "selection-repeat":
                    android.app.AppOpsManager ops=context.getSystemService(android.app.AppOpsManager.class);
                    android.content.pm.ApplicationInfo iwlan=context.getPackageManager().getApplicationInfo("dev.codex.vowifi.iwlan",0);
                    String ipsec="android:manage_ipsec_tunnels",previous=token(),recordBefore=digest(record());
                    int originalOp=ops.unsafeCheckOpNoThrow(ipsec,iwlan.uid,iwlan.packageName);
                    try {
                        ops.setMode(ipsec,iwlan.uid,iwlan.packageName,android.app.AppOpsManager.MODE_ERRORED);
                        boolean fullRefused=false;
                        try{transaction.trial(7);}catch(IOException expected){fullRefused="prepared-installation-required".equals(expected.getMessage());}
                        if(!fullRefused||!recordBefore.equals(digest(record()))||ops.unsafeCheckOpNoThrow(ipsec,iwlan.uid,iwlan.packageName)!=android.app.AppOpsManager.MODE_ERRORED)throw new IOException("fixture-full-mask-ipsec-refusal-unconfirmed");
                        String next=transaction.trial(2);saveToken(next);
                        boolean oldRefused=false;try{transaction.renew(previous);}catch(SecurityException expected){oldRefused=true;}
                        if(previous.equals(next)||!oldRefused||!transaction.verify(next)||!"2".equals(transaction.status().getProperty("mask")))throw new IOException("fixture-new-cycle-unconfirmed");
                        if(ops.unsafeCheckOpNoThrow(ipsec,iwlan.uid,iwlan.packageName)!=android.app.AppOpsManager.MODE_ERRORED)throw new IOException("fixture-unselected-ipsec-policy-changed");
                        File history=new File(coordination,"history/slot-0-sub-"+sub+"-"+previous+"/carrier/phase");ModernStateFiles.canonical(history);
                        if(!history.isFile()||!"RESTORED".equals(new String(Files.readAllBytes(history.toPath()),"UTF-8")))throw new IOException("fixture-original-history-unavailable");
                        output.put("new_qns_only_cycle_preserves_previous_snapshot",true).put("previous_cycle_token_refused",true)
                            .put("full_selection_refuses_denied_ipsec",true).put("qns_only_selection_preserves_denied_ipsec",true);
                    } finally {ops.setMode(ipsec,iwlan.uid,iwlan.packageName,originalOp);}
                    break;
                case "selection-expire":
                    ModernStateFiles state=new ModernStateFiles(record().getParentFile());Properties value=state.read("selection.properties");
                    value.setProperty("trial.until",Long.toString(Math.max(1,SystemClock.elapsedRealtime()-1)));state.write("selection.properties",value);
                    if(transaction.renew(token())||!"RESTORED".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-expiry-recovery-unconfirmed");
                    transaction.restore(token());output.put("expired_trial_restores_entire_owner",true).put("simulated_trial_deadline",true);break;
                case "selection-cleanup":
                    ModernStateFiles cleanup=new ModernStateFiles(record().getParentFile());
                    if(cleanup.file("selection.properties").exists()) {
                        Properties recovery=cleanup.read("selection.properties");String recoveryToken=recovery.getProperty("token");
                        if("ARCHIVING".equals(recovery.getProperty("phase")))transaction.finishArchive(recoveryToken);else transaction.restore(recoveryToken);
                    }
                    output.put("selection_outer_cleanup_completed",true);break;
                }
            }
            output.put("carrier_registration_verified",false).put("call_sms_verified",false).put("dual_active_sim_verified",false).put("phone_cache_refresh_verified",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
