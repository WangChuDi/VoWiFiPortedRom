// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Runs the actual resident loop only on a named, root, nonce-scoped fake-SIM guest. */
public final class ModernResidentEmulatorTrial {
    private static Context context;private static File root,module,installation,coordination,carrier;private static int sub;
    private static ModernSelectionSupervisor supervisor()throws Exception{return new ModernSelectionSupervisor(context,module,installation,coordination,true);}
    private static ModernSelectionTransaction selection()throws Exception{return new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true);}
    private static void disable()throws Exception {
        File file=new File(module,"disable");ModernStateFiles.canonical(file);if(file.exists()){if(!file.isFile())throw new IOException("fixture-disable-marker-refused");return;}
        try(FileOutputStream stream=new FileOutputStream(file)){android.system.Os.chmod(file.getPath(),0600);stream.getFD().sync();}
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!args[1].matches("[0-9a-f]{32}")||!Arrays.asList("prepare","run","alive","audit","kill","disable","recovered").contains(args[0])||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-resident-fixture-required");
            Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0||phone.getSimState(0)!=TelephonyManager.SIM_STATE_READY)throw new SecurityException("single-ready-fake-SIM-required");sub=active.get(0).getSubscriptionId();String operator=phone.createForSubscriptionId(sub).getSimOperator();if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-resident-fixture-required");
            root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]);ModernStateFiles.canonical(root);if(!root.isDirectory())throw new IOException("fixture-root-unavailable");module=new File(root,"module");installation=new File(root,"state");coordination=new File(root,"selection");
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((args[1]+":selection").getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(Locale.ROOT,"%02x",b&255));carrier=new File("/data/local/tmp/codex-modern-persistence-tests/slot-0-sub-"+sub+"-"+hash.substring(0,32));
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage","resident-"+args[0]);
            switch(args[0]) {
            case "prepare":
                if(supervisor().alive())throw new IOException("fixture-resident-already-live");File marker=new File(module,"disable");ModernStateFiles.canonical(marker);if(marker.exists())Files.delete(marker.toPath());
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.archiveRestored();transaction.prepare();}
                try(ModernSelectionTransaction transaction=selection()){String token=transaction.trial(7);transaction.retain(token);if(!transaction.verify(token))throw new IOException("fixture-resident-trial-unconfirmed");}
                output.put("retained_owner_ready_for_real_resident",true);break;
            case "run":
                android.os.Process.setArgV0("CodexVoWiFiResidentFixture-"+args[1]);JSONObject ended=supervisor().runResident();output.put("resident_exit_confirmed",ended.optBoolean("stop_supervisor")).put("installation_policy_restored",ended.optBoolean("installation_policy_restored"));break;
            case "alive":output.put("supervisor_alive",supervisor().alive());break;
            case "audit":
                try(ModernControllerLock held=new ModernControllerLock(new File("/data/local/tmp/codex-modern-persistence-tests"),true);ModernSelectionTransaction transaction=new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true,held)) {
                    Properties record=new ModernStateFiles(new File(coordination,"owners/slot-0-sub-"+sub)).read("selection.properties");long until=Long.parseLong(ModernRootSettings.get("codex_wfc_stack_slot_0_until"));
                    if(!"ACTIVE".equals(record.getProperty("phase"))||!Boolean.parseBoolean(record.getProperty("persistent"))||!transaction.verify(transaction.currentToken())||until<=SystemClock.elapsedRealtime())throw new IOException("fixture-resident-lease-unconfirmed");
                    output.put("retained_selection_verified",true).put("lease_until",until).put("elapsed",SystemClock.elapsedRealtime()).put("supervisor_alive",supervisor().alive());
                }break;
            case "kill":
                if(!supervisor().alive())throw new IOException("fixture-resident-process-owner-refused");Properties identity=new ModernStateFiles(coordination).read("resident.properties");File helper=ModernRecoveryPublication.current(installation);
                if(!"1".equals(identity.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(identity.getProperty("build"))||!helper.getParentFile().getName().equals(identity.getProperty("generation"))||!Integer.toString(ModernPhoneRefresh.boot(context)).equals(identity.getProperty("boot"))||!identity.getProperty("pid","").matches("[1-9][0-9]{0,8}"))throw new IOException("fixture-resident-process-owner-refused");
                int pid=Integer.parseInt(identity.getProperty("pid"));File process=new File("/proc/"+pid);if(android.system.Os.stat(process.getPath()).st_uid!=0)throw new IOException("fixture-resident-process-owner-refused");byte[] command=Files.readAllBytes(new File(process,"cmdline").toPath());if(command.length>4096)throw new IOException("fixture-resident-process-owner-refused");String name=new String(command,"UTF-8");int terminator=name.indexOf(0);if(terminator>=0)name=name.substring(0,terminator);if(!name.equals("CodexVoWiFiResidentFixture-"+args[1]))throw new IOException("fixture-resident-process-owner-refused");
                if(!ModernSelectionSupervisor.processStartTicks(pid).equals(identity.getProperty("start_ticks")))throw new IOException("fixture-resident-process-owner-refused");
                android.system.Os.kill(pid,android.system.OsConstants.SIGKILL);output.put("owned_fixture_resident_sigkill_sent",true).put("resident_start_time_identity_verified",true);break;
            case "disable":disable();output.put("fixture_disable_published",true);break;
            case "recovered":
                if(supervisor().alive())throw new IOException("fixture-resident-still-live");
                try(ModernSelectionTransaction transaction=selection()){transaction.confirmRestoredOwner();}
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true)){transaction.restore();if(!"RESTORED".equals(transaction.phase()))throw new IOException("fixture-resident-policy-unconfirmed");}
                output.put("resident_restored_owner_and_installation_before_exit",true);break;
            }
            output.put("magisk_mount_verified",false).put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false).put("os_reboot_verified",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
