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

/** Real permission policy + synthetic pending peer journal; never a second SIM proof. */
public final class ModernSharedRolesEmulatorTrial {
    private static Context context;private static File root,module,installation,coordination,carrier,peerCore;
    private static ModernStateFiles owner,peer,resources;private static int sub;
    private static ModernSelectionTransaction selection(ModernControllerLock held)throws Exception{return new ModernSelectionTransaction(context,0,sub,module,installation,coordination,carrier,true,held);}
    private static ModernSharedSelectedRoles roles(ModernControllerLock held)throws Exception{return new ModernSharedSelectedRoles(context,coordination,held,true);}
    private static Properties synthetic()throws Exception {
        Properties identity=new ModernStateFiles(root).read("role-peer.properties"),value=peer.read("selection.properties");
        if(!"1".equals(value.getProperty("slot"))||!"2147483646".equals(value.getProperty("sub"))||!"5".equals(value.getProperty("mask"))||!"false".equals(value.getProperty("mode_owned"))||!identity.getProperty("token","").matches("[0-9a-f]{32}")||!identity.getProperty("token").equals(value.getProperty("token"))||peerCore.exists())throw new IOException("fixture-role-peer-refused");
        return value;
    }
    private static void finishPeer(ModernControllerLock held)throws Exception {
        if(!peer.file("selection.properties").exists())return;
        Properties value=synthetic();value.setProperty("phase","RESTORING");peer.write("selection.properties",value);roles(held).release(value);value.setProperty("phase","RESTORED");peer.write("selection.properties",value);
        for(int group:new int[]{1,4})if(!"true".equals(value.getProperty("role.shared."+group+".released"))||!"RESTORED".equals(resources.read("roles-"+group+".properties").getProperty("phase")))throw new IOException("fixture-shared-role-restoration-unconfirmed");
        File[] children=peer.root.listFiles();if(children==null||children.length!=1||!children[0].equals(peer.file("selection.properties")))throw new IOException("fixture-role-peer-refused");Files.delete(children[0].toPath());Files.delete(peer.root.toPath());
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=2||!args[1].matches("[0-9a-f]{32}")||!Arrays.asList("roles-prepare","roles-trial","roles-share","roles-release-first","roles-foreign-policy","roles-release-last","roles-cleanup").contains(args[0])||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-shared-role-fixture-required");
            Looper.prepareMainLooper();context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0||phone.getSimState(0)!=TelephonyManager.SIM_STATE_READY)throw new SecurityException("single-ready-fake-SIM-required");sub=active.get(0).getSubscriptionId();String operator=phone.createForSubscriptionId(sub).getSimOperator();if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-shared-role-fixture-required");
            root=new File("/data/local/tmp/codex-modern-installation-tests/"+args[1]);ModernStateFiles.canonical(root);if(!root.isDirectory())throw new IOException("fixture-root-unavailable");module=new File(root,"module");installation=new File(root,"state");coordination=new File(root,"selection");
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest((args[1]+":selection").getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(Locale.ROOT,"%02x",b&255));String suffix="-"+hash.substring(0,32);
            carrier=new File("/data/local/tmp/codex-modern-persistence-tests/slot-0-sub-"+sub+suffix);peerCore=new File(carrier.getParentFile(),"slot-1-sub-2147483646"+suffix);ModernStateFiles.canonical(peerCore);
            owner=new ModernStateFiles(new File(coordination,"owners/slot-0-sub-"+sub));peer=new ModernStateFiles(new File(coordination,"owners/slot-1-sub-2147483646"));resources=new ModernStateFiles(new File(coordination,"resources"));
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]);
            try(ModernControllerLock held=new ModernControllerLock(carrier.getParentFile(),true)) {
                switch(args[0]) {
                case "roles-prepare":
                    // A completed selection batch is a different installation
                    // cycle. Native unused-service callbacks may revoke prepared
                    // policy after its last owner exits. Preserve the old baseline
                    // and validate its restoration before preparing a fresh one.
                    if(peer.file("selection.properties").exists())throw new IOException("fixture-role-peer-refused");
                    try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true,held)) {
                        output.put("previous_prepared_profile_ready",transaction.ready());
                        transaction.restore();transaction.archiveRestored();transaction.prepare();
                        if(!"PREPARED".equals(transaction.phase())||!transaction.ready())throw new IOException("fixture-shared-role-preparation-unconfirmed");
                    }
                    output.put("previous_installation_original_restored_and_archived",true).put("new_shared_role_installation_cycle_prepared",true);break;
                case "roles-trial":
                    if(peer.file("selection.properties").exists())throw new IOException("fixture-role-peer-refused");
                    try(ModernSelectionTransaction transaction=selection(held)){String token=transaction.trial(7);if(!transaction.verify(token))throw new IOException("fixture-shared-role-trial-unconfirmed");}
                    output.put("shared_role_originals_created_before_selection",true);break;
                case "roles-share":
                    if(peer.file("selection.properties").exists())throw new IOException("fixture-role-peer-refused");
                    Properties first=owner.read("selection.properties"),second=new Properties();second.putAll(first);second.setProperty("slot","1");second.setProperty("sub","2147483646");second.setProperty("token",UUID.randomUUID().toString().replace("-",""));second.setProperty("mask","5");second.setProperty("mode_owned","false");second.setProperty("persistent","false");second.setProperty("phase","PREPARING");
                    for(String key:new ArrayList<String>(second.stringPropertyNames()))if(key.contains(".observed")||key.endsWith(".apply_requested"))second.remove(key);
                    roles(held).snapshot(second);
                    for(int group:new int[]{1,4})if(!first.getProperty("role.shared."+group+".id").equals(second.getProperty("role.shared."+group+".id")))throw new IOException("fixture-shared-role-baseline-resampled");
                    for(int index=0;index<3;index++)for(String field:Arrays.asList("uid","apk","flags","grant"))if(!Objects.equals(first.getProperty("role."+index+"."+field),second.getProperty("role."+index+"."+field)))throw new IOException("fixture-shared-role-baseline-resampled");
                    Properties identity=new Properties();identity.setProperty("token",second.getProperty("token"));new ModernStateFiles(root).write("role-peer.properties",identity);peer.write("selection.properties",second);
                    output.put("pending_peer_reuses_first_original_role_baselines",true);break;
                case "roles-release-first":
                    synthetic();try(ModernSelectionTransaction transaction=selection(held)){transaction.restore(transaction.currentToken());if(!transaction.originalCarrierRestored())throw new IOException("fixture-shared-role-first-carrier-unconfirmed");}
                    for(int group:new int[]{1,4})if(!"OWNED".equals(resources.read("roles-"+group+".properties").getProperty("phase")))throw new IOException("fixture-shared-role-released-too-early");
                    boolean refused=false;try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,true,held)){try{transaction.restore();}catch(IOException expected){refused="owner-recovery-must-finish-first".equals(expected.getMessage());}}
                    if(!refused)throw new IOException("fixture-shared-role-installation-not-protected");output.put("first_owner_restores_carrier_while_shared_role_baseline_retained",true).put("pending_peer_blocks_installation_policy_restore",true);break;
                case "roles-foreign-policy":
                    Properties pending=synthetic();pending.setProperty("phase","RESTORING");peer.write("selection.properties",pending);android.app.AppOpsManager ops=context.getSystemService(android.app.AppOpsManager.class);int uid=context.getPackageManager().getApplicationInfo("dev.codex.vowifi.iwlan",0).uid;String op="android:manage_ipsec_tunnels";int before=ops.unsafeCheckOpNoThrow(op,uid,"dev.codex.vowifi.iwlan");
                    try {
                        ops.setMode(op,uid,"dev.codex.vowifi.iwlan",android.app.AppOpsManager.MODE_IGNORED);boolean blocked=false;
                        try{roles(held).release(pending);}catch(IOException expected){blocked="external-selected-data-appop-change".equals(expected.getMessage());}
                        if(!blocked||ops.unsafeCheckOpNoThrow(op,uid,"dev.codex.vowifi.iwlan")!=android.app.AppOpsManager.MODE_IGNORED||!"false".equals(peer.read("selection.properties").getProperty("role.shared.1.released")))throw new IOException("fixture-shared-role-foreign-policy-not-preserved");
                        output.put("foreign_appop_preserved_and_shared_recovery_left_pending",true);
                    }finally{ops.setMode(op,uid,"dev.codex.vowifi.iwlan",before);}break;
                case "roles-release-last":
                    synthetic();if(!"RESTORING".equals(resources.read("roles-1.properties").getProperty("phase")))throw new IOException("fixture-shared-role-recovery-phase-unavailable");
                    finishPeer(held);output.put("last_pending_peer_restores_shared_originals",true).put("synthetic_peer_removed_only_after_original_restoration",true).put("interrupted_shared_resource_release_resumed",true);break;
                case "roles-cleanup":
                    if(owner.file("selection.properties").exists())try(ModernSelectionTransaction transaction=selection(held)){if(!"RESTORED".equals(transaction.status().getProperty("phase")))transaction.restore(transaction.currentToken());}
                    finishPeer(held);if(peer.root.exists()){File[] remaining=peer.root.listFiles();if(remaining==null||remaining.length!=0)throw new IOException("fixture-role-peer-refused");Files.delete(peer.root.toPath());}
                    output.put("roles_fixture_cleanup_completed",true);break;
                }
            }
            output.put("synthetic_pending_peer_journal_only",true).put("second_sim_provider_selected",false).put("dual_active_sim_verified",false).put("carrier_call_sms_verified",false).put("magisk_mount_verified",false);success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
