// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Private owned-guest contracts. No carrier selection or system-property mutation. */
public final class ModernCoordinationEmulatorCheck {
    private static final File LOCK_ROOT=new File("/data/local/tmp/codex-modern-persistence-tests");
    private static void guard()throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-test-emulator-required");
    }
    private static void contender()throws Exception {
        java.lang.Process child=new ProcessBuilder("app_process","/system/bin","ModernCoordinationEmulatorCheck","probe").redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();boolean[] failed={false};
        Thread reader=new Thread(()->{try(InputStream in=child.getInputStream()){int c;while((c=in.read())!=-1){if(bytes.size()>=8192){failed[0]=true;break;}bytes.write(c);}}catch(IOException error){failed[0]=true;}});reader.setDaemon(true);reader.start();
        if(!child.waitFor(10,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("fixture-lock-probe-timeout");}reader.join(1000);
        if(reader.isAlive()||failed[0]||child.exitValue()!=0||!"locked".equals(bytes.toString("UTF-8").trim()))throw new IOException("fixture-shared-lock-not-held");
    }
    private static String property()throws Exception {
        return (SystemProperties.class.getMethod("find",String.class).invoke(null,ModernSharedIwlan.KEY)!=null)+":"+SystemProperties.get(ModernSharedIwlan.KEY);
    }
    public static void main(String[] args) {
        JSONObject report=new JSONObject();boolean success=false;
        try {
            guard();
            if(args.length==1&&"probe".equals(args[0])) {
                try(ModernControllerLock unexpected=new ModernControllerLock(LOCK_ROOT,true)){throw new IOException("fixture-lock-acquired-unexpectedly");}
                catch(IOException busy){if(!"modern-controller-busy".equals(busy.getMessage()))throw busy;System.out.println("locked");System.exit(0);return;}
            }
            if(args.length!=1||!args[0].matches("[0-9a-f]{32}"))throw new IllegalArgumentException("fixture-nonce-required");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1)throw new SecurityException("single-fake-subscription-required");
            SubscriptionInfo selected=active.get(0);TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            String operator=phone.createForSubscriptionId(selected.getSubscriptionId()).getSimOperator();
            if(selected.getSimSlotIndex()!=0||phone.getSimState(0)!=TelephonyManager.SIM_STATE_READY||operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("ready-fake-SIM-required");
            ModernPhoneIdle.requireIdle(context);
            File root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[0]);
            if(!root.equals(root.getCanonicalFile())||!root.isDirectory())throw new IOException("fixture-root-unavailable");
            File carrierState=new File(LOCK_ROOT,"slot-0-sub-"+selected.getSubscriptionId()+"-"+args[0]);
            if(carrierState.exists())throw new IOException("fresh-coordination-fixture-required");
            report.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage","coordination");
            try(ModernControllerLock held=new ModernControllerLock(LOCK_ROOT,true)) {
                contender();
                ModernProviderTransaction closedCarrier;
                try(ModernProviderTransaction carrier=new ModernProviderTransaction(context,0,selected.getSubscriptionId(),carrierState,true,held)) {
                    closedCarrier=carrier;
                    carrier.snapshot();carrier.restore();
                    if(!carrier.confirmRestored())throw new IOException("fixture-unused-carrier-baseline-unconfirmed");
                }
                boolean carrierRefused=false;try{closedCarrier.statePhase();}catch(IOException expected){carrierRefused="closed-carrier-transaction".equals(expected.getMessage());}
                if(!carrierRefused)throw new IOException("fixture-closed-carrier-use-accepted");
                report.put("closed_carrier_handle_refused",true);
                held.requireHeld(LOCK_ROOT);contender();report.put("borrowed_carrier_close_keeps_global_lock",true);
                ModernInstallationTransaction closedInstallation;
                try(ModernInstallationTransaction installation=new ModernInstallationTransaction(context,new File(root,"module"),new File(root,"state"),true,held)) {
                    closedInstallation=installation;
                    if(!installation.ready()||!"ABSENT".equals(installation.phase()))throw new IOException("fresh-ready-installation-required");
                }
                boolean installationRefused=false;try{closedInstallation.ready();}catch(IOException expected){installationRefused="closed-installation-transaction".equals(expected.getMessage());}
                if(!installationRefused)throw new IOException("fixture-closed-installation-use-accepted");
                report.put("closed_installation_handle_refused",true);
                held.requireHeld(LOCK_ROOT);contender();report.put("borrowed_installation_close_keeps_global_lock",true);
                boolean[] wrongThread={false};Thread test=new Thread(()->{try{held.requireHeld(LOCK_ROOT);}catch(IOException expected){wrongThread[0]=true;}});test.start();test.join(2000);
                if(test.isAlive()||!wrongThread[0])throw new IOException("fixture-thread-owner-not-enforced");report.put("cross_thread_lock_use_refused",true);
                ModernIwlanObservation.Result mode=ModernIwlanObservation.read(ModernSystemObservation.telephonyDebug(),0);
                if(Boolean.TRUE.equals(mode.legacy)||(mode.legacy==null&&Build.VERSION.SDK_INT<=33))throw new IOException("fixture-already-ap-assisted-required");
                String before=property();ModernSharedIwlan shared=new ModernSharedIwlan(context,new File(root,"shared-mode"),held,true);
                if(shared.acquire(0)||shared.release(true)||shared.release(false))throw new IOException("fixture-no-mode-reset-required");
                shared.finishCycle(new File(root,"mode-before-archive.properties"));
                if(!before.equals(property()))throw new IOException("fixture-original-mode-changed");
                report.put("shared_mode_no_reset_cycle_preserves_presence_and_value",true);
                report.put("other_owner_flag_contract_only",true);
            }
            report.put("carrier_selection_verified",false).put("phone_cache_refresh_verified",false).put("dual_active_sim_verified",false).put("carrier_call_sms_verified",false);
            success=true;
        }catch(Throwable error){try{report.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(report.toString());System.exit(success?0:1);
    }
}
