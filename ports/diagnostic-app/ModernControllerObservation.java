// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.content.Context;
import android.content.pm.*;
import android.os.*;
import android.telephony.CarrierConfigManager;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Fixed-path read-only modern inventory; no controller/lock/state construction. */
final class ModernControllerObservation {
    static final File MODULE=new File("/data/adb/modules/codex_vowifi_stack_modern");
    static final File STATE=new File("/data/adb/codex_vowifi_stack_modern");
    private static final String[] PACKAGES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    private static final String[] APK_PATHS={"system/priv-app/Api31Iwlan/Api31Iwlan.apk","system/priv-app/Api31Qns/Api31Qns.apk","system/priv-app/Api31Ims/Api31Ims.apk"};
    static void canonical(File file)throws Exception {
        if(!file.getAbsoluteFile().equals(file.getCanonicalFile())||Files.isSymbolicLink(file.toPath()))throw new IOException("modern-path-refused");
    }
    static Properties read(File file)throws Exception {
        canonical(file);if(!file.isFile()||file.length()>32768)throw new IOException("modern-record-unavailable");
        Properties value=new Properties();try(InputStream stream=new FileInputStream(file)){value.load(stream);}return value;
    }
    static String digest(File file)throws Exception {
        canonical(file);if(!file.isFile()||file.length()>67108864)throw new IOException("modern-payload-unavailable");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream stream=new FileInputStream(file)){byte[] bytes=new byte[8192];int count;while((count=stream.read(bytes))!=-1)hash.update(bytes,0,count);}
        StringBuilder result=new StringBuilder();for(byte value:hash.digest())result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();
    }
    static boolean moduleMatches(Context context)throws Exception {
        canonical(MODULE);
        // Expected bytes come from the separately signed APKs/module embedded in
        // this tool. A module-provided manifest alone cannot attest to its code.
        if(!EngineAssets.MODERN_CONTROLLER_SHA256.equals(digest(new File(MODULE,"controller.zip")))||!EngineAssets.MODERN_CONTROL_SHA256.equals(digest(new File(MODULE,"control.sh"))))return false;
        Properties profile=read(new File(MODULE,"installation.properties"));if(!"1".equals(profile.getProperty("schema")))return false;
        PackageManager pm=context.getPackageManager();
        for(int index=0;index<PACKAGES.length;index++) {
            String expected=EngineAssets.MODERN_APK_SHA256[index];ApplicationInfo app=pm.getApplicationInfo(PACKAGES[index],0);
            if(!expected.equals(profile.getProperty("apk."+index))||!expected.equals(digest(new File(MODULE,APK_PATHS[index])))||!expected.equals(digest(new File(app.sourceDir)))||app.uid<10000||app.uid>=100000||app.splitSourceDirs!=null||(app.flags&ApplicationInfo.FLAG_SYSTEM)==0||(app.privateFlags&ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)==0)return false;
        }
        return true;
    }
    static Properties owner(File file,int slot,int sub)throws Exception {
        Properties value=read(file);
        if(!"3".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!Integer.toString(slot).equals(value.getProperty("slot"))||!Integer.toString(sub).equals(value.getProperty("sub"))||!value.getProperty("mask","").matches("[1-7]")||!value.getProperty("token","").matches("[0-9a-f]{32}")||!Arrays.asList("true","false").contains(value.getProperty("persistent"))||!Arrays.asList("true","false").contains(value.getProperty("mode_owned"))||!Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(value.getProperty("phase")))throw new IOException("modern-owner-record-refused");
        if(!Arrays.asList("true","false").contains(value.getProperty("recovery.pending_owner","false"))||Boolean.parseBoolean(value.getProperty("recovery.pending_owner"))&&!"RESTORING".equals(value.getProperty("phase")))throw new IOException("modern-recovery-record-refused");
        return value;
    }
    static JSONObject inspect(Context context,int slot,int sub)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||slot<0||slot>7||sub< -1)throw new SecurityException("modern-observation-profile-refused");
        canonical(MODULE);canonical(STATE);File control=new File(MODULE,"control.sh");canonical(control);if(!control.isFile()&&!STATE.exists())return null;
        JSONObject result=new JSONObject().put("engine","modern").put("mode",SystemProperties.get("ro.telephony.iwlan_operation_mode","default")).put("persistent","DISABLED").put("transaction","INACTIVE").put("components",0).put("owner_slot",slot).put("owner_sub",sub);
        boolean matches=false;
        if(control.isFile())try{matches=moduleMatches(context);}catch(Exception unavailable){result.put("module_observation_error",unavailable.getClass().getSimpleName());}
        boolean enabled=control.isFile()&&!new File(MODULE,"disable").exists()&&!new File(MODULE,"remove").exists();
        result.put("module_payload_matches_tool",matches).put("module_enabled",enabled).put("identity_selection",matches&&enabled?1:0).put("component_selection",matches&&enabled?1:0).put("persistent_component_selection",matches&&enabled?1:0);
        File baseline=new File(STATE,"installation/baseline.properties");canonical(baseline);String installationPhase="ABSENT";
        if(baseline.exists()){
            Properties value=read(baseline);installationPhase=value.getProperty("phase");
            if(!"1".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!Arrays.asList("PREPARING","PREPARED","RESTORING","RESTORED").contains(installationPhase))throw new IOException("modern-installation-record-refused");
        }
        result.put("installation_phase",installationPhase);
        org.json.JSONArray active=new org.json.JSONArray();File directory=new File(STATE,"coordination/owners");canonical(directory);
        if(directory.exists()) {
            File[] entries=directory.listFiles();if(!directory.isDirectory()||entries==null||entries.length>64)throw new IOException("modern-owner-inventory-refused");Arrays.sort(entries,Comparator.comparing(File::getName));Set<Integer> slots=new HashSet<>();
            for(File entry:entries) {
                canonical(entry);if(!entry.isDirectory()||!entry.getName().matches("slot-[0-7]-sub-(0|[1-9][0-9]{0,9})"))throw new IOException("modern-owner-inventory-refused");
                File file=new File(entry,"selection.properties");canonical(file);
                if(!file.exists()){File[] contents=entry.listFiles();File core=new File(STATE,"transactions/"+entry.getName());canonical(core);if(contents!=null&&contents.length==0&&!core.exists())continue;throw new IOException("modern-owner-inventory-refused");}
                String[] tuple=entry.getName().split("-");int ownerSlot=Integer.parseInt(tuple[1]),ownerSub=Integer.parseInt(tuple[3]);Properties value=owner(file,ownerSlot,ownerSub);String phase=value.getProperty("phase");
                if(!Arrays.asList("RESTORED","ARCHIVING").contains(phase)){if(!slots.add(ownerSlot))throw new IOException("modern-owner-inventory-refused");active.put(new JSONObject().put("slot",ownerSlot).put("sub",ownerSub).put("recovery_pending_owner",Boolean.parseBoolean(value.getProperty("recovery.pending_owner"))));}
                if(ownerSlot==slot&&ownerSub==sub)result.put("transaction",phase).put("components",Integer.parseInt(value.getProperty("mask"))).put("persistent",Boolean.parseBoolean(value.getProperty("persistent"))?"ENABLED":"TRIAL").put("recovery_pending_owner",Boolean.parseBoolean(value.getProperty("recovery.pending_owner")));
            }
        }
        result.put("active_owners",active);
        if(sub>=0){PersistableBundle config=context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sub);for(String key:new String[]{"carrier_data_service_wlan_package_override_string","carrier_network_service_wlan_package_override_string","carrier_qualified_networks_service_package_override_string","config_ims_mmtel_package_override_string"})if(config!=null&&config.containsKey(key))result.put(key,config.getString(key));}
        result.put("selection_verified",false).put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false);
        return result;
    }
}
