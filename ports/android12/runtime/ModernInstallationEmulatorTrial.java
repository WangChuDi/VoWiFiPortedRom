// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Owned fake-SIM fixture only. A separate process executes each recovery stage. */
public final class ModernInstallationEmulatorTrial {
    private static final String SMS="android.permission.SEND_SMS",AUDIO="android.permission.RECORD_AUDIO",IPSEC="android:manage_ipsec_tunnels";
    private static final String[][] RUNTIME={{"READ_PHONE_STATE"},{"READ_PHONE_STATE"},{"READ_PHONE_STATE","RECORD_AUDIO","SEND_SMS"}};
    private static File root,module,state;
    private static Context context;private static PackageManager pm;private static AppOpsManager ops;
    private static Properties read(File path)throws Exception {
        if(!path.getAbsoluteFile().equals(path.getCanonicalFile())||Files.isSymbolicLink(path.toPath())||!path.isFile()||path.length()>16384)throw new IOException("fixture-file-refused");
        Properties value=new Properties();try(InputStream stream=new FileInputStream(path)){value.load(stream);}return value;
    }
    private static void write(File path,Properties value)throws Exception {
        if(!path.getAbsoluteFile().equals(path.getCanonicalFile())||Files.isSymbolicLink(path.toPath()))throw new IOException("fixture-file-refused");
        File temporary=new File(path.getPath()+".new");
        if(!temporary.getAbsoluteFile().equals(temporary.getCanonicalFile())||Files.isSymbolicLink(temporary.toPath()))throw new IOException("fixture-temp-refused");
        try(FileOutputStream stream=new FileOutputStream(temporary)){android.system.Os.chmod(temporary.getPath(),0600);value.store(stream,"private fixture baseline");stream.getFD().sync();}
        Files.move(temporary.toPath(),path.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static boolean exemption(){Set<String> names=pm.getWhitelistedRestrictedPermissions("me.phh.ims",PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);return names!=null&&names.contains(SMS);}
    private static void exemption(boolean wanted)throws Exception {
        if(exemption()!=wanted){boolean changed=wanted?pm.addWhitelistedRestrictedPermission("me.phh.ims",SMS,PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM):pm.removeWhitelistedRestrictedPermission("me.phh.ims",SMS,PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);if(!changed||exemption()!=wanted)throw new IOException("fixture-exemption-unconfirmed");}
    }
    private static Properties observe()throws Exception {
        Properties result=new Properties();result.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);
        for(int i=0;i<ModernInstallationTransaction.PACKAGES.length;i++){
            String name=ModernInstallationTransaction.PACKAGES[i];ApplicationInfo app=pm.getApplicationInfo(name,0);
            result.setProperty("uid."+i,Integer.toString(app.uid));result.setProperty("apk."+i,ModernInstallationTransaction.digest(new File(app.sourceDir)));
            for(String permission:RUNTIME[i]){
                String full="android.permission."+permission;
                result.setProperty("grant."+i+"."+permission,Boolean.toString(pm.checkPermission(full,name)==PackageManager.PERMISSION_GRANTED));
                result.setProperty("flags."+i+"."+permission,Integer.toString(pm.getPermissionFlags(full,name,UserHandle.SYSTEM)));
            }
        }
        result.setProperty("sms.exemption",Boolean.toString(exemption()));
        result.setProperty("ipsec.mode",Integer.toString(ops.unsafeCheckOpNoThrow(IPSEC,pm.getApplicationInfo(ModernInstallationTransaction.PACKAGES[0],0).uid,ModernInstallationTransaction.PACKAGES[0])));return result;
    }
    private static void owner(Properties original)throws Exception {
        Properties current=observe();
        for(String key:Arrays.asList("build","uid.0","uid.1","uid.2","apk.0","apk.1","apk.2"))if(!Objects.equals(original.getProperty(key),current.getProperty(key)))throw new SecurityException("fixture-owner-changed");
    }
    private static void cleanup(Properties original)throws Exception {
        owner(original);ModernPhoneIdle.requireIdle(context);exemption(Boolean.parseBoolean(original.getProperty("sms.exemption")));
        for(int i=0;i<ModernInstallationTransaction.PACKAGES.length;i++)for(String permission:RUNTIME[i]){
            String name=ModernInstallationTransaction.PACKAGES[i],full="android.permission."+permission;
            boolean wanted=Boolean.parseBoolean(original.getProperty("grant."+i+"."+permission)),actual=pm.checkPermission(full,name)==PackageManager.PERMISSION_GRANTED;
            if(wanted!=actual){if(wanted)pm.grantRuntimePermission(name,full,UserHandle.SYSTEM);else pm.revokeRuntimePermission(name,full,UserHandle.SYSTEM);}
        }
        ops.setMode(IPSEC,pm.getApplicationInfo(ModernInstallationTransaction.PACKAGES[0],0).uid,ModernInstallationTransaction.PACKAGES[0],Integer.parseInt(original.getProperty("ipsec.mode")));
        if(!original.equals(observe()))throw new IOException("fixture-outer-restoration-unconfirmed");
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean success=false;
        try{
            if(android.os.Process.myUid()!=0||args.length!=2||!args[1].matches("[0-9a-f]{32}")||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-test-emulator-required");
            if(!Arrays.asList("seed","prepare","resume","external-policy","restore","arm-restore-resume","recover","cleanup","audit","repair-data-role").contains(args[0]))throw new IllegalArgumentException("fixture-stage-invalid");
            Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();pm=context.getPackageManager();ops=context.getSystemService(AppOpsManager.class);
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1)throw new SecurityException("single-fake-subscription-required");
            SubscriptionInfo selected=active.get(0);String operator=context.getSystemService(TelephonyManager.class).createForSubscriptionId(selected.getSubscriptionId()).getSimOperator();
            if(selected.getSimSlotIndex()!=0||context.getSystemService(TelephonyManager.class).getSimState(0)!=TelephonyManager.SIM_STATE_READY||operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("ready-fake-SIM-required");
            ModernPhoneIdle.requireIdle(context);root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]);module=new File(root,"module");state=new File(root,"state");
            if(!root.equals(root.getCanonicalFile())||!root.isDirectory())throw new IOException("fixture-root-unavailable");
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]);File outer=new File(root,"outer.properties");
            try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,state,true)){
                if("seed".equals(args[0])){
                    if(outer.exists()||!"ABSENT".equals(transaction.phase()))throw new IOException("fresh-ready-fixture-required");
                    write(outer,observe());
                    if(pm.checkPermission(AUDIO,"me.phh.ims")==PackageManager.PERMISSION_GRANTED)pm.revokeRuntimePermission("me.phh.ims",AUDIO,UserHandle.SYSTEM);exemption(false);
                    ops.setMode(IPSEC,pm.getApplicationInfo(ModernInstallationTransaction.PACKAGES[0],0).uid,ModernInstallationTransaction.PACKAGES[0],AppOpsManager.MODE_ERRORED);
                    if(pm.checkPermission(AUDIO,"me.phh.ims")==PackageManager.PERMISSION_GRANTED||exemption()||transaction.ready())throw new IOException("fixture-seed-unconfirmed");
                    result.put("missing_runtime_grant_and_exemption_seeded",true).put("denied_ipsec_seeded",true);
                }else{
                    Properties original=read(outer);owner(original);
                    switch(args[0]){
                    case "repair-data-role":
                        Properties oldSelection=read(new File(root,"selection/owners/slot-0-sub-"+selected.getSubscriptionId()+"/selection.properties"));
                        if(!"RESTORED".equals(oldSelection.getProperty("phase"))||!"7".equals(oldSelection.getProperty("mask"))||!"false".equals(oldSelection.getProperty("mode_owned")))throw new IOException("known-restored-data-role-fixture-required");
                        int originalRoleFlags=Integer.parseInt(original.getProperty("flags.0.READ_PHONE_STATE")),actualFlags=pm.getPermissionFlags("android.permission.READ_PHONE_STATE",ModernInstallationTransaction.PACKAGES[0],UserHandle.SYSTEM);
                        int roleMask=PackageManager.FLAG_PERMISSION_SYSTEM_FIXED|PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
                        if(((originalRoleFlags^actualFlags)&~roleMask)!=0)throw new IOException("unknown-fixture-data-role-policy");
                        ModernRolePermissionBroker.restore(0,Integer.parseInt(original.getProperty("uid.0")),original.getProperty("apk.0"),originalRoleFlags,Boolean.parseBoolean(original.getProperty("grant.0.READ_PHONE_STATE")));
                        if(!original.equals(observe()))throw new IOException("fixture-role-repair-unconfirmed");
                        result.put("known_data_role_original_policy_recovered",true);break;
                    case "audit":
                        Properties actual=observe();org.json.JSONArray changes=new org.json.JSONArray();
                        for(int i=0;i<ModernInstallationTransaction.PACKAGES.length;i++)for(String permission:RUNTIME[i])for(String kind:Arrays.asList("grant","flags")){
                            String key=kind+"."+i+"."+permission;
                            if(!Objects.equals(original.getProperty(key),actual.getProperty(key)))changes.put(new JSONObject().put("field",key).put("before",original.getProperty(key)).put("after",actual.getProperty(key)));
                        }
                        for(String key:Arrays.asList("sms.exemption","ipsec.mode"))if(!Objects.equals(original.getProperty(key),actual.getProperty(key)))changes.put(new JSONObject().put("field",key).put("before",original.getProperty(key)).put("after",actual.getProperty(key)));
                        org.json.JSONArray missing=new org.json.JSONArray();
                        for(int i=0;i<ModernInstallationTransaction.PACKAGES.length;i++)for(String permission:RUNTIME[i])if(pm.checkPermission("android.permission."+permission,ModernInstallationTransaction.PACKAGES[i])!=PackageManager.PERMISSION_GRANTED)missing.put("runtime."+i+"."+permission);
                        result.put("fixed_policy_changes",changes).put("missing_runtime_permissions",missing).put("sms_exemption_ready",exemption()).put("ipsec_allowed",ops.unsafeCheckOpNoThrow(IPSEC,pm.getApplicationInfo(ModernInstallationTransaction.PACKAGES[0],0).uid,ModernInstallationTransaction.PACKAGES[0])==AppOpsManager.MODE_ALLOWED).put("permission_profile_ready",transaction.ready()).put("installation_phase",transaction.phase());break;
                    case "prepare":transaction.prepare();if(!transaction.ready()||!"PREPARED".equals(transaction.phase()))throw new IOException("fixture-preparation-unconfirmed");result.put("installed_permissions_prepared",true);break;
                    case "resume":transaction.prepare();if(!transaction.ready())throw new IOException("fixture-retention-unconfirmed");result.put("new_process_retention_verified",true);break;
                    case "external-policy":
                        int uid=pm.getApplicationInfo(ModernInstallationTransaction.PACKAGES[0],0).uid;
                        ops.setMode(IPSEC,uid,ModernInstallationTransaction.PACKAGES[0],AppOpsManager.MODE_IGNORED);
                        try{
                            boolean refused=false;
                            try{transaction.restore();}catch(IOException expected){refused="external-ipsec-policy-change-refused".equals(expected.getMessage());}
                            if(!refused||ops.unsafeCheckOpNoThrow(IPSEC,uid,ModernInstallationTransaction.PACKAGES[0])!=AppOpsManager.MODE_IGNORED||!"PREPARED".equals(transaction.phase())||!exemption()||pm.checkPermission(AUDIO,"me.phh.ims")!=PackageManager.PERMISSION_GRANTED)throw new IOException("fixture-external-policy-refusal-unconfirmed");
                            result.put("external_policy_preserved_on_restore_refusal",true);
                        }finally{ops.setMode(IPSEC,uid,ModernInstallationTransaction.PACKAGES[0],AppOpsManager.MODE_ALLOWED);}
                        break;
                    case "restore":transaction.restore();if(transaction.ready()||!"RESTORED".equals(transaction.phase()))throw new IOException("fixture-restore-unconfirmed");result.put("original_seeded_policy_restored",true);break;
                    case "arm-restore-resume":File baseline=new File(state,"baseline.properties");Properties record=read(baseline);if(!"RESTORED".equals(record.getProperty("phase")))throw new IOException("fixture-restored-phase-required");record.setProperty("phase","RESTORING");write(baseline,record);result.put("simulated_restore_handoff_armed",true);break;
                    case "recover":transaction.restore();transaction.restore();if(!"RESTORED".equals(transaction.phase())||transaction.ready())throw new IOException("fixture-resume-unconfirmed");result.put("restore_resumed_and_repeated",true);break;
                    case "cleanup":cleanup(original);result.put("entire_outer_permission_observation_restored",true);break;
                    }
                }
                success=true;
            }
            result.put("actual_os_reboot_or_forced_kill_test",false).put("magisk_mount_verified",false).put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false);
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(failure)).put("origin",ModernSafeFailure.origin(failure));}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success&&!result.has("error")?0:1);
    }
}
