// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Disposable one-SIM lifecycle fixture. Never mounts Magisk or changes a real SIM. */
public final class ModernSupervisorEmulatorTrial {
    private static Context context;private static File root,module,installation,coordination,carrier;private static int sub;
    private static ModernSelectionTransaction selection()throws Exception {return new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true);}
    private static ModernSelectionSupervisor supervisor()throws Exception {return new ModernSelectionSupervisor(context,module,installation,coordination,true);}
    private static ModernStateFiles owner()throws Exception {return new ModernStateFiles(new File(coordination,"owners/slot-0-sub-"+sub));}
    private static File alternativeGeneration()throws Exception {
        File current=ModernRecoveryPublication.current(installation);byte[] bytes=java.nio.file.Files.readAllBytes(current.toPath());byte[] alternate=Arrays.copyOf(bytes,bytes.length+1);File script=new File(current.getParentFile(),"recovery-boot.sh");
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(alternate);StringBuilder sha=new StringBuilder();for(byte b:digest)sha.append(String.format(Locale.ROOT,"%02x",b&255));
        String scriptHash=ModernInstallationTransaction.digest(script),version=ModernRecoveryPublication.generation(sha.toString(),scriptHash);
        ModernStateFiles files=new ModernStateFiles(new File(installation,"recovery/generations/"+version));java.nio.file.Files.write(files.file("controller.zip").toPath(),alternate);android.system.Os.chmod(files.file("controller.zip").getPath(),0600);
        java.nio.file.Files.copy(script.toPath(),files.file("recovery-boot.sh").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);android.system.Os.chmod(files.file("recovery-boot.sh").getPath(),0700);
        java.nio.file.Files.write(files.file("recovery.sha256").toPath(),(sha+"  controller.zip\n"+scriptHash+"  recovery-boot.sh\n").getBytes("UTF-8"));return files.root;
    }
    private static void assertTick(JSONObject value,int renewed,int restored,boolean policy)throws Exception {
        if(value.getInt("pending")!=0||value.getInt("renewed")!=renewed||value.getInt("restored")!=restored||value.getBoolean("installation_policy_restored")!=policy)throw new IOException("fixture-supervisor-tick-unconfirmed");
    }
    private static void marker(boolean present)throws Exception {
        File marker=new File(module,"disable");ModernStateFiles.canonical(marker);
        if(present){if(marker.exists())throw new IOException("fixture-new-disable-marker-required");try(FileOutputStream stream=new FileOutputStream(marker)){android.system.Os.chmod(marker.getPath(),0600);stream.getFD().sync();}}
        else if(marker.exists()){if(!marker.isFile())throw new IOException("fixture-disable-marker-refused");java.nio.file.Files.delete(marker.toPath());}
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!args[1].matches("[0-9a-f]{32}")||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-supervisor-fixture-required");
            if(!Arrays.asList("supervisor-publish","supervisor-publication-handoff","supervisor-trial","supervisor-renew","supervisor-upgrade-refusal","supervisor-payload-loss","supervisor-reprepare","supervisor-inventory","supervisor-expire","supervisor-persistent","supervisor-role-failure","supervisor-disable","supervisor-repeat","supervisor-generation","supervisor-cleanup").contains(args[0]))throw new IllegalArgumentException("fixed-supervisor-stage-required");
            Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0)throw new SecurityException("single-slot-zero-fake-SIM-required");sub=active.get(0).getSubscriptionId();
            root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]);ModernStateFiles.canonical(root);if(!root.isDirectory())throw new IOException("fixture-root-unavailable");module=new File(root,"module");installation=new File(root,"state");coordination=new File(root,"selection");
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((args[1]+":selection").getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(Locale.ROOT,"%02x",b&255));carrier=new File("/data/local/tmp/codex-modern-persistence-tests/slot-0-sub-"+sub+"-"+hash.substring(0,32));
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]);
            switch(args[0]) {
            case "supervisor-publish":
                JSONObject published=new ModernRecoveryPublication(context,module,installation,true).publish();
                File recovery=ModernRecoveryPublication.current(installation).getParentFile();
                if(!published.getBoolean("independent_recovery_published")||!ModernInstallationTransaction.digest(new File(module,"controller.zip")).equals(ModernInstallationTransaction.digest(new File(recovery,"controller.zip")))||!ModernInstallationTransaction.digest(new File(module,"recovery-boot.sh")).equals(ModernInstallationTransaction.digest(new File(recovery,"recovery-boot.sh")))||!new File(root,"hooks/codex-modern-installation-recovery.sh").isFile())throw new IOException("fixture-recovery-publication-unconfirmed");
                if(!"unclassified".equals(ModernSafeFailure.reason(new IOException("/private/example/subscription-123456: arbitrary message")))||!"unclassified".equals(ModernSafeFailure.reason(new IOException()))||!"selected-subscription-changed".equals(ModernSafeFailure.reason(new IOException("selected-subscription-changed"))))throw new IOException("fixture-error-privacy-unconfirmed");
                output.put("independent_helper_script_and_hook_published",true).put("arbitrary_failure_messages_not_exported",true);break;
            case "supervisor-publication-handoff":
                File source=new File(module,"controller.zip"),inventory=new File(module,"payload.sha256"),commit=new File(installation,"recovery/current");byte[] oldSource=java.nio.file.Files.readAllBytes(source.toPath()),oldInventory=java.nio.file.Files.readAllBytes(inventory.toPath()),oldCommit=java.nio.file.Files.readAllBytes(commit.toPath());File prior=ModernRecoveryPublication.current(installation);String priorPolicy=ModernInstallationTransaction.digest(new File(installation,"baseline.properties"));
                try {
                    try(FileOutputStream stream=new FileOutputStream(source,true)){stream.write(0);stream.getFD().sync();}
                    String candidateHash=ModernInstallationTransaction.digest(source);String contents=new String(oldInventory,"UTF-8").replaceAll("(?m)^[0-9a-f]{64}  controller\\.zip$",candidateHash+"  controller.zip");java.nio.file.Files.write(inventory.toPath(),contents.getBytes("UTF-8"));
                    JSONObject staged=new ModernRecoveryPublication(context,module,installation,true).stageForTest();
                    if(!staged.getBoolean("unselected_complete_generation_staged")||!Arrays.equals(oldCommit,java.nio.file.Files.readAllBytes(commit.toPath()))||!prior.equals(ModernRecoveryPublication.current(installation))||!priorPolicy.equals(ModernInstallationTransaction.digest(new File(installation,"baseline.properties"))))throw new IOException("fixture-publication-handoff-not-preserved");
                }finally{java.nio.file.Files.write(source.toPath(),oldSource);java.nio.file.Files.write(inventory.toPath(),oldInventory);}
                if(!new ModernRecoveryPublication(context,module,installation,true).publish().getBoolean("helper_generation_unchanged"))throw new IOException("fixture-publication-resume-unconfirmed");
                output.put("uncommitted_generation_leaves_previous_recovery_usable",true).put("publication_reopen_preserves_selected_generation",true).put("simulated_publication_handoff",true);break;
            case "supervisor-trial":
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.archiveRestored();transaction.prepare();if(!transaction.ready())throw new IOException("fixture-new-installation-cycle-unconfirmed");}
                File[] history=new File(installation,"history").listFiles();if(history==null||history.length!=1)throw new IOException("fixture-installation-history-unconfirmed");
                try(ModernSelectionTransaction transaction=selection()){String token=transaction.trial(7);if(!transaction.verify(token))throw new IOException("fixture-supervised-trial-unconfirmed");}
                output.put("fresh_installation_cycle_archives_original",true).put("supervised_full_mask_trial_ready",true);break;
            case "supervisor-renew":
                assertTick(supervisor().tick(false),1,0,false);
                try(ModernSelectionTransaction transaction=selection()){if(!transaction.verify(transaction.currentToken()))throw new IOException("fixture-supervised-renew-unconfirmed");}
                output.put("inventory_renews_trial_owner",true);break;
            case "supervisor-upgrade-refusal":
                File pointer=new File(installation,"recovery/current");byte[] originalPointer=java.nio.file.Files.readAllBytes(pointer.toPath());String before=ModernInstallationTransaction.digest(new File(installation,"baseline.properties"));File alternative=alternativeGeneration();
                try {
                    java.nio.file.Files.write(pointer.toPath(),(alternative.getName()+"\n").getBytes("UTF-8"));String differing=ModernInstallationTransaction.digest(pointer);boolean refused=false;
                    try{new ModernRecoveryPublication(context,module,installation,true).publish();}catch(IOException expected){refused="original-recovery-required-before-helper-upgrade".equals(expected.getMessage());}
                    if(!refused||!differing.equals(ModernInstallationTransaction.digest(pointer))||!before.equals(ModernInstallationTransaction.digest(new File(installation,"baseline.properties"))))throw new IOException("fixture-helper-upgrade-not-refused");
                }finally{java.nio.file.Files.write(pointer.toPath(),originalPointer);}
                output.put("pending_owner_prevents_recovery_helper_replacement",true).put("simulated_helper_generation_difference",true);break;
            case "supervisor-payload-loss":
                File apk=new File(module,ModernInstallationTransaction.APK_PATHS[0]),hidden=new File(apk.getPath()+".fixture-missing");ModernStateFiles.canonical(hidden);
                if(hidden.exists())throw new IOException("fixture-payload-loss-path-refused");
                java.nio.file.Files.move(apk.toPath(),hidden.toPath());
                try {
                    assertTick(supervisor().tick(false),0,1,true);
                    try(ModernSelectionTransaction transaction=selection()){if(!transaction.originalCarrierRestored()||!"RESTORED".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-payload-loss-carrier-unconfirmed");}
                    try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){if(!"RESTORED".equals(transaction.phase()))throw new IOException("fixture-payload-loss-policy-unconfirmed");}
                }finally{java.nio.file.Files.move(hidden.toPath(),apk.toPath());}
                output.put("missing_module_apk_does_not_block_carrier_recovery",true).put("installed_app_original_policy_restored_without_payload",true);break;
            case "supervisor-reprepare":
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.archiveRestored();transaction.prepare();}
                try(ModernSelectionTransaction transaction=selection()){if(!transaction.verify(transaction.trial(7)))throw new IOException("fixture-supervisor-reprepare-unconfirmed");}
                output.put("second_installation_cycle_reprepared_after_recovery",true);break;
            case "supervisor-inventory":
                File recordPath=owner().file("selection.properties"),hiddenRecord=owner().file("selection.fixture-hidden");String originalRecord=ModernInstallationTransaction.digest(recordPath),originalPolicy=ModernInstallationTransaction.digest(new File(installation,"baseline.properties"));
                if(hiddenRecord.exists())throw new IOException("fixture-hidden-owner-path-refused");java.nio.file.Files.move(recordPath.toPath(),hiddenRecord.toPath());
                try {boolean refused=false;try{supervisor().tick(true);}catch(IOException expected){refused="supervisor-untracked-owner-state".equals(expected.getMessage());}
                    if(!refused||!originalRecord.equals(ModernInstallationTransaction.digest(hiddenRecord))||!originalPolicy.equals(ModernInstallationTransaction.digest(new File(installation,"baseline.properties"))))throw new IOException("fixture-owner-inventory-refusal-unconfirmed");
                }finally{java.nio.file.Files.move(hiddenRecord.toPath(),recordPath.toPath());}
                File empty=new File(coordination,"owners/slot-1-sub-2147483647");ModernStateFiles.canonical(empty);if(empty.exists())throw new IOException("fixture-empty-owner-path-refused");new ModernStateFiles(empty);
                try {assertTick(supervisor().tick(false),1,0,false);}finally{java.nio.file.Files.delete(empty.toPath());}
                output.put("missing_owner_record_refused_before_any_mutation",true).put("empty_archived_owner_directory_skipped",true);break;
            case "supervisor-expire":
                Properties record=owner().read("selection.properties");record.setProperty("trial.until",Long.toString(Math.max(1,SystemClock.elapsedRealtime()-1)));owner().write("selection.properties",record);
                assertTick(supervisor().tick(false),0,1,false);
                try(ModernSelectionTransaction transaction=selection()){if(!transaction.originalCarrierRestored()||!"RESTORED".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-supervised-expiry-unconfirmed");}
                output.put("inventory_restores_expired_trial",true).put("simulated_trial_deadline",true);break;
            case "supervisor-persistent":
                try(ModernSelectionTransaction transaction=selection()){String token=transaction.trial(7);transaction.retain(token);}
                assertTick(supervisor().tick(false),1,0,false);output.put("inventory_renews_retained_owner",true);break;
            case "supervisor-role-failure":
                Properties saved=owner().read("selection.properties");String original=saved.getProperty("role.0.apk");saved.setProperty("role.0.apk",String.join("",Collections.nCopies(64,"0")));owner().write("selection.properties",saved);
                try {
                    String baseline=ModernInstallationTransaction.digest(new File(installation,"baseline.properties"));JSONObject failed=supervisor().tick(true);
                    if(failed.getInt("pending")!=1||failed.getBoolean("installation_policy_restored")||!baseline.equals(ModernInstallationTransaction.digest(new File(installation,"baseline.properties"))))throw new IOException("fixture-pending-owner-policy-not-preserved");
                    try(ModernSelectionTransaction transaction=selection()){if(!transaction.originalCarrierRestored()||!"RESTORING".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-carrier-before-role-recovery-unconfirmed");}
                    boolean refused=false;try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){try{transaction.restore();}catch(IOException expected){refused="owner-recovery-must-finish-first".equals(expected.getMessage());}}
                    if(!refused)throw new IOException("fixture-incomplete-owner-installation-restore-not-refused");
                    output.put("carrier_restores_before_role_identity_refusal",true).put("pending_owner_blocks_installation_restore",true).put("simulated_role_identity_mismatch",true);
                }finally{Properties retry=owner().read("selection.properties");retry.setProperty("role.0.apk",original);owner().write("selection.properties",retry);}
                break;
            case "supervisor-disable":
                marker(true);assertTick(supervisor().tick(false),0,1,true);
                try(ModernSelectionTransaction transaction=selection()){if(!transaction.originalCarrierRestored()||!"RESTORED".equals(transaction.status().getProperty("phase")))throw new IOException("fixture-disabled-owner-recovery-unconfirmed");}
                output.put("disabled_module_restores_owner_before_permissions",true);break;
            case "supervisor-repeat":
                assertTick(supervisor().tick(false),0,0,true);
                try(ModernSelectionTransaction transaction=selection()){transaction.restore(transaction.currentToken());}
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.restore();if(transaction.ready())throw new IOException("fixture-restored-policy-was-regranted");}
                output.put("repeat_recovery_keeps_original_installation_policy",true);break;
            case "supervisor-generation":
                ModernSelectionSupervisor older=supervisor();File generationFile=new File(installation,"recovery/current");byte[] oldCode=java.nio.file.Files.readAllBytes(generationFile.toPath());String oldOwner=ModernInstallationTransaction.digest(owner().file("selection.properties")),oldPolicy=ModernInstallationTransaction.digest(new File(installation,"baseline.properties"));File alternate=alternativeGeneration();
                try {
                    java.nio.file.Files.write(generationFile.toPath(),(alternate.getName()+"\n").getBytes("UTF-8"));
                    JSONObject stopped=older.tick(false);
                    if(!stopped.optBoolean("generation_changed")||!stopped.optBoolean("stop_supervisor")||!oldOwner.equals(ModernInstallationTransaction.digest(owner().file("selection.properties")))||!oldPolicy.equals(ModernInstallationTransaction.digest(new File(installation,"baseline.properties"))))throw new IOException("fixture-old-supervisor-generation-not-stopped");
                }finally{java.nio.file.Files.write(generationFile.toPath(),oldCode);}
                output.put("older_supervisor_stops_before_changing_new_generation",true).put("simulated_helper_generation_difference",true);break;
            case "supervisor-cleanup":
                marker(false);
                // The earlier fixture may fail before this continuation begins.
                if(new File(coordination,"owners/slot-0-sub-"+sub+"/selection.properties").isFile()) {
                    JSONObject result=supervisor().tick(true);if(result.getInt("pending")!=0)throw new IOException("fixture-supervisor-cleanup-pending");
                }
                output.put("supervisor_fixture_cleanup_completed",true);break;
            }
            output.put("magisk_mount_verified",false).put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false).put("actual_os_reboot_or_forced_kill_test",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
