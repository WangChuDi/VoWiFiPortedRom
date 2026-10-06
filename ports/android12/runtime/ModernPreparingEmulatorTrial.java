// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Actual snapshot SIGKILL boundaries; never accepts a host-selected PID or phone. */
public final class ModernPreparingEmulatorTrial {
    private static Context context;private static File root,module,installation,coordination,carrier;private static int sub;private static String nonce;
    private static ModernSelectionTransaction selection()throws Exception{return new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true);}
    private static ModernStateFiles fixture()throws Exception{return new ModernStateFiles(new File(root,"preparing-fixture"));}
    private static ModernStateFiles owner()throws Exception{return new ModernStateFiles(new File(coordination,"owners/slot-0-sub-"+sub));}
    private static String corePhase()throws Exception {File phase=new File(carrier,"phase");ModernStateFiles.canonical(phase);if(!phase.isFile()||phase.length()>64)throw new IOException("fixture-preparation-phase-unavailable");return new String(Files.readAllBytes(phase.toPath()),"UTF-8");}
    private static Properties identity()throws Exception {
        Properties value=fixture().read("process.properties");
        if(!"1".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!Integer.toString(ModernPhoneRefresh.boot(context)).equals(value.getProperty("boot"))||!value.getProperty("pid","").matches("[1-9][0-9]{0,8}")||!value.getProperty("start_ticks","").matches("[0-9]+")||!Arrays.asList("bundle-staged","snapshot-complete").contains(value.getProperty("checkpoint")))throw new IOException("fixture-preparation-process-refused");
        return value;
    }
    private static int liveIdentity()throws Exception {
        Properties value=identity();int pid=Integer.parseInt(value.getProperty("pid"));File process=new File("/proc/"+pid);
        if(android.system.Os.stat(process.getPath()).st_uid!=0||!ModernSelectionSupervisor.processStartTicks(pid).equals(value.getProperty("start_ticks")))throw new IOException("fixture-preparation-process-refused");
        byte[] bytes=Files.readAllBytes(new File(process,"cmdline").toPath());if(bytes.length>4096)throw new IOException("fixture-preparation-process-refused");String name=new String(bytes,"UTF-8");int end=name.indexOf(0);if(end>=0)name=name.substring(0,end);
        if(!name.equals("CodexVoWiFiPreparingFixture-"+nonce))throw new IOException("fixture-preparation-process-refused");
        if(!"PREPARING".equals(corePhase())||new File(carrier,"components").exists()||!"PREPARING".equals(owner().read("selection.properties").getProperty("phase")))throw new IOException("fixture-preparation-boundary-refused");
        return pid;
    }
    private static void requireDead()throws Exception {
        Properties value=identity();int pid=Integer.parseInt(value.getProperty("pid"));File process=new File("/proc/"+pid);
        if(process.exists()&&ModernSelectionSupervisor.processStartTicks(pid).equals(value.getProperty("start_ticks")))throw new IOException("fixture-preparation-process-still-live");
    }
    private static void checkpoint(String stage,String wanted)throws Exception {
        if(!wanted.equals(stage))return;
        Properties value=new Properties();value.setProperty("schema","1");value.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);value.setProperty("boot",Integer.toString(ModernPhoneRefresh.boot(context)));value.setProperty("pid",Integer.toString(android.os.Process.myPid()));value.setProperty("start_ticks",ModernSelectionSupervisor.processStartTicks(android.os.Process.myPid()));value.setProperty("checkpoint",stage);fixture().write("process.properties",value);
        // The host observes this actual process, then targets only this journal.
        Thread.sleep(120000);throw new IOException("fixture-preparation-kill-window-expired");
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!args[1].matches("[0-9a-f]{32}")||!Arrays.asList("prepare","run-staged","run-complete","ready","kill","tamper","recover","cleanup").contains(args[0])||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-preparation-fixture-required");
            nonce=args[1];Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0||phone.getSimState(0)!=TelephonyManager.SIM_STATE_READY)throw new SecurityException("single-ready-fake-SIM-required");sub=active.get(0).getSubscriptionId();String operator=phone.createForSubscriptionId(sub).getSimOperator();if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-preparation-fixture-required");
            root=new File("/data/local/tmp/codex-modern-installation-tests/"+nonce);ModernStateFiles.canonical(root);if(!root.isDirectory())throw new IOException("fixture-root-unavailable");module=new File(root,"module");installation=new File(root,"state");coordination=new File(root,"selection");
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((nonce+":selection").getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(Locale.ROOT,"%02x",b&255));carrier=new File("/data/local/tmp/codex-modern-persistence-tests/slot-0-sub-"+sub+"-"+hash.substring(0,32));
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage","preparing-"+args[0]);
            switch(args[0]) {
            case "prepare":
                File marker=new File(module,"disable");ModernStateFiles.canonical(marker);if(marker.exists()){if(!marker.isFile())throw new IOException("fixture-disable-marker-refused");Files.delete(marker.toPath());}
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.restore();transaction.archiveRestored();transaction.prepare();}
                output.put("fresh_preparation_installation_ready",true);break;
            case "run-staged":case "run-complete":
                android.os.Process.setArgV0("CodexVoWiFiPreparingFixture-"+nonce);final String wanted="run-staged".equals(args[0])?"bundle-staged":"snapshot-complete";
                try(ModernSelectionTransaction transaction=selection()){transaction.trialForEmulator(7,new ModernProviderTransaction.SnapshotObserver(){public void checkpoint(String stage)throws Exception{ModernPreparingEmulatorTrial.checkpoint(stage,wanted);}});}
                throw new IOException("fixture-preparation-boundary-not-reached");
            case "ready":
                if(!fixture().file("process.properties").isFile()){output.put("ready",false);break;}
                Properties observed=identity();
                if(!new File("/proc/"+observed.getProperty("pid")).exists()){output.put("ready",false);break;}
                try {liveIdentity();}
                catch(android.system.ErrnoException missing){if(missing.errno!=android.system.OsConstants.ENOENT)throw missing;output.put("ready",false);break;}
                catch(NoSuchFileException missing){String base="/proc/"+observed.getProperty("pid")+"/";if(!(base+"stat").equals(missing.getFile())&&!(base+"cmdline").equals(missing.getFile()))throw missing;output.put("ready",false);break;}
                output.put("ready",true).put("actual_preparing_process_and_start_time_verified",true).put("before_carrier_mutation",true).put("checkpoint",identity().getProperty("checkpoint"));break;
            case "kill":int pid=liveIdentity();android.system.Os.kill(pid,android.system.OsConstants.SIGKILL);output.put("owned_preparing_process_sigkill_sent",true).put("process_start_time_identity_verified",true);break;
            case "tamper":
                requireDead();if(!"snapshot-complete".equals(identity().getProperty("checkpoint")))throw new IOException("fixture-complete-snapshot-required");
                try(ModernSelectionTransaction transaction=selection()) {
                    File saved=new File(carrier,"config-before.bin.saved"),original=new File(carrier,"config-before.bin");ModernStateFiles.canonical(saved);ModernStateFiles.canonical(original);if(saved.exists()||!original.isFile())throw new IOException("fixture-private-baseline-refused");String digest=ModernInstallationTransaction.digest(original);
                    Files.move(original.toPath(),saved.toPath(),StandardCopyOption.ATOMIC_MOVE);
                    try {
                        boolean refused=false;try{transaction.restore(transaction.currentToken());}catch(IOException expected){refused="snapshot-unavailable".equals(expected.getMessage());}
                        if(!refused||!"PREPARING".equals(corePhase())||!digest.equals(ModernInstallationTransaction.digest(saved))||original.exists())throw new IOException("fixture-incomplete-snapshot-not-preserved");
                        output.put("missing_original_bundle_refused_without_resampling",true).put("exact_saved_original_preserved",true);
                    } finally {Files.move(saved.toPath(),original.toPath(),StandardCopyOption.ATOMIC_MOVE);}
                }break;
            case "recover":
                requireDead();try(ModernSelectionTransaction transaction=selection()){transaction.restore(transaction.currentToken());transaction.restore(transaction.currentToken());transaction.confirmRestoredOwner();if(!"RESTORED".equals(corePhase())||new File(carrier,"config-before.bin.new").exists())throw new IOException("fixture-preparation-recovery-unconfirmed");}
                output.put("new_process_recovers_actual_preparing_interruption",true).put("full_original_carrier_lease_and_roles_verified",true).put("repeat_recovery_verified",true).put("checkpoint",identity().getProperty("checkpoint"));break;
            case "cleanup":
                if(fixture().file("process.properties").exists())requireDead();
                if(owner().file("selection.properties").exists())try(ModernSelectionTransaction transaction=selection()){transaction.restore(transaction.currentToken());transaction.confirmRestoredOwner();}
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.restore();if(!"RESTORED".equals(transaction.phase()))throw new IOException("fixture-preparation-installation-not-original");}
                output.put("preparation_fixture_cleanup_completed",true);break;
            }
            output.put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false).put("magisk_mount_verified",false).put("os_reboot_verified",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
