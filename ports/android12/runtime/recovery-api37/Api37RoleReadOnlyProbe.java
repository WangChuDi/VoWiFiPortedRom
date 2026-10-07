// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Observe legacy role-policy compatibility; never restore or edit a record. */
public final class Api37RoleReadOnlyProbe {
    static Properties read(File file)throws Exception {
        if(!file.getAbsoluteFile().equals(file.getCanonicalFile())||Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()>16384)throw new IOException("record-refused");
        Properties result=new Properties();try(InputStream in=new FileInputStream(file)){result.load(in);}return result;
    }
    public static void main(String[] args){JSONObject out=new JSONObject();boolean success=false;
        try{
            if(args.length!=2||!"permissions".equals(args[0])||!"0".equals(args[1])||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT!=37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!"CodexVoWiFiApi37".equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-original-api37-required");
            out.put("schema",1).put("sdk",37).put("action","permissions").put("read_only",true).put("settings_written",false).put("physical_phone_modified",false).put("new_baseline_created",false);
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            TelephonyManager phone=context.getSystemService(TelephonyManager.class);
            if(active==null||active.size()!=1||active.get(0).getSimSlotIndex()!=0||phone.getSimState(0)!=TelephonyManager.SIM_STATE_READY)throw new SecurityException("single-ready-owner-required");
            int sub=active.get(0).getSubscriptionId();String operator=phone.createForSubscriptionId(sub).getSimOperator();
            if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("fake-original-owner-required");
            File base=new File("/data/local/tmp/codex-modern-installation-tests");File[] entries=base.listFiles();if(entries==null||entries.length>64)throw new IOException("bounded-inventory-required");
            ArrayList<File> fixtures=new ArrayList<>();for(File root:entries){if(!root.getName().matches("[a-f0-9]{32}")||!root.getAbsoluteFile().equals(root.getCanonicalFile())||Files.isSymbolicLink(root.toPath()))throw new IOException("fixture-refused");if(new File(root,"outer.properties").isFile())fixtures.add(root);}
            if(fixtures.size()!=1)throw new IOException("single-original-fixture-required");File root=fixtures.get(0),record=new File(root,"selection/owners/slot-0-sub-"+sub+"/selection.properties");
            Properties installation=read(new File(root,"state/baseline.properties")),roles=read(record);
            if(!"1".equals(installation.getProperty("schema"))||!(37+":"+Build.FINGERPRINT).equals(installation.getProperty("build"))||!"3".equals(roles.getProperty("schema"))||!(37+":"+Build.FINGERPRINT).equals(roles.getProperty("build"))||!"0".equals(roles.getProperty("slot"))||!Integer.toString(sub).equals(roles.getProperty("sub"))||!"7".equals(roles.getProperty("mask")))throw new IOException("original-schema-owner-required");
            String before=ModernInstallationTransaction.digest(record);PackageManager pm=context.getPackageManager();
            String[] packages={"dev.codex.vowifi.iwlan","me.phh.ims","me.phh.ims"},permissions={"android.permission.READ_PHONE_STATE","android.permission.READ_PHONE_STATE","android.permission.RECORD_AUDIO"};JSONArray observations=new JSONArray();
            int informational=PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED|PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;
            for(int i=0;i<3;i++){
                String prefix="role."+i+".";ApplicationInfo app=pm.getApplicationInfo(packages[i],0);
                if(!Integer.toString(app.uid).equals(roles.getProperty(prefix+"uid"))||!ModernInstallationTransaction.digest(new File(app.sourceDir)).equals(roles.getProperty(prefix+"apk"))||(app.flags&ApplicationInfo.FLAG_SYSTEM)==0||(app.privateFlags&ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)==0||!Arrays.asList("true","false").contains(roles.getProperty(prefix+"grant")))throw new SecurityException("original-package-owner-required");
                int original=Integer.parseInt(roles.getProperty(prefix+"flags")),actual=pm.getPermissionFlags(permissions[i],packages[i],UserHandle.SYSTEM),diff=original^actual,roleOwned=PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT|(i==0?PackageManager.FLAG_PERMISSION_SYSTEM_FIXED:0);
                boolean grant=pm.checkPermission(permissions[i],packages[i])==PackageManager.PERMISSION_GRANTED;
                observations.put(new JSONObject().put("role_index",i).put("package_owner_verified",true).put("grant_matches_original",grant==Boolean.parseBoolean(roles.getProperty(prefix+"grant"))).put("legacy_foreign_change",(diff&~roleOwned)!=0).put("informational_change",(diff&informational)!=0).put("authorization_foreign_change",(diff&~(roleOwned|informational))!=0).put("policy_equal_ignoring_information",(diff&~informational)==0));
            }
            if(!before.equals(ModernInstallationTransaction.digest(record)))throw new IOException("concurrent-record-change-refused");out.put("roles",observations).put("original_role_record_unchanged",true);success=true;
        }catch(Throwable error){try{out.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        try{out.put("status",success?"passed":"failed");}catch(Exception ignored){}System.out.println(out);System.exit(success?0:1);
    }
}
