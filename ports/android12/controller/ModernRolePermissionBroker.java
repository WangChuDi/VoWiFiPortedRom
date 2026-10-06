// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** System UID is required to update role-owned SYSTEM_FIXED flags on Android13. */
public final class ModernRolePermissionBroker {
    private static final String[] PACKAGES={"dev.codex.vowifi.iwlan","me.phh.ims","me.phh.ims"};
    private static final String[] PERMISSIONS={"android.permission.READ_PHONE_STATE","android.permission.READ_PHONE_STATE","android.permission.RECORD_AUDIO"};
    private static int mask(int index){return PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT|(index==0?PackageManager.FLAG_PERMISSION_SYSTEM_FIXED:0);}
    static void restore(int index,int uid,String apk,int flags,boolean grant)throws Exception {
        if(android.os.Process.myUid()!=0||index<0||index>2||uid<10000||uid>=100000||!apk.matches("[0-9a-f]{64}"))throw new SecurityException("fixed-root-role-delegation-required");
        String classpath=System.getenv("CLASSPATH");
        boolean production="/data/adb/modules/codex_vowifi_stack_modern/controller.zip".equals(classpath)||"/data/adb/codex_vowifi_stack_modern/installation/recovery/controller.zip".equals(classpath);
        boolean fixture="1".equals(SystemProperties.get("ro.kernel.qemu"))&&("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))&&Arrays.asList("/data/local/tmp/codex-modern-runtime-check.zip","/data/local/tmp/codex-modern-owner-audit.zip").contains(classpath);
        if(!production&&!fixture)throw new SecurityException("fixed-role-helper-required");
        File source=new File(classpath);ModernStateFiles.canonical(source);String hash=ModernInstallationTransaction.digest(source);
        File directory=new File("/data/local/tmp/codex-modern-role-brokers");ModernStateFiles.canonical(directory);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("role-broker-directory-unavailable");
        if(android.system.Os.stat(directory.getPath()).st_uid!=0)throw new IOException("root-owned-role-broker-required");android.system.Os.chmod(directory.getPath(),0755);
        File helper=new File(directory,hash+".zip");ModernStateFiles.canonical(helper);
        if(!helper.exists()) {
            File temporary=Files.createTempFile(directory.toPath(),"role-",".new").toFile();
            try{Files.copy(source.toPath(),temporary.toPath(),StandardCopyOption.REPLACE_EXISTING);android.system.Os.chmod(temporary.getPath(),0644);if(!hash.equals(ModernInstallationTransaction.digest(temporary)))throw new IOException("role-broker-copy-mismatch");Files.move(temporary.toPath(),helper.toPath(),StandardCopyOption.ATOMIC_MOVE);}
            finally{if(temporary.exists())Files.delete(temporary.toPath());}
        }
        if(android.system.Os.stat(helper.getPath()).st_uid!=0||!hash.equals(ModernInstallationTransaction.digest(helper)))throw new IOException("root-role-broker-payload-changed");
        java.lang.Process child=new ProcessBuilder("su","1000","env","CLASSPATH="+helper.getPath(),"app_process","/system/bin","ModernRolePermissionBroker",Integer.toString(index),Integer.toString(uid),apk,Integer.toString(flags),Boolean.toString(grant)).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();boolean[] failed={false};
        Thread reader=new Thread(()->{try(InputStream input=child.getInputStream()){int value;while((value=input.read())!=-1){if(bytes.size()>=8192){failed[0]=true;break;}bytes.write(value);}}catch(IOException error){failed[0]=true;}});reader.setDaemon(true);reader.start();
        if(!child.waitFor(12,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("role-broker-timeout");}reader.join(500);
        if(child.exitValue()!=0||failed[0]||reader.isAlive())throw new IOException("role-broker-restore-failed");
        String[] lines=bytes.toString("UTF-8").trim().split("\\r?\\n");int successes=0;for(String line:lines)if(line.startsWith("{")&&line.endsWith("}")&&"restored".equals(new JSONObject(line).optString("status")))successes++;
        if(successes!=1)throw new IOException("role-broker-result-unconfirmed");
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(android.os.Process.myUid()!=android.os.Process.SYSTEM_UID||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length!=5||!args[0].matches("[0-2]")||!args[1].matches("[0-9]{5}")||!args[2].matches("[0-9a-f]{64}")||!Arrays.asList("true","false").contains(args[4]))throw new SecurityException("fixed-system-role-broker-required");
            int index=Integer.parseInt(args[0]),uid=Integer.parseInt(args[1]),before=Integer.parseInt(args[3]);boolean wanted=Boolean.parseBoolean(args[4]);
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();PackageManager pm=context.getPackageManager();ApplicationInfo app=pm.getApplicationInfo(PACKAGES[index],0);
            if(app.uid!=uid||(app.flags&ApplicationInfo.FLAG_SYSTEM)==0||(app.privateFlags&ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)==0||!args[2].equals(ModernInstallationTransaction.digest(new File(app.sourceDir))))throw new SecurityException("role-broker-package-owner-changed");
            int allowed=mask(index),actual=pm.getPermissionFlags(PERMISSIONS[index],PACKAGES[index],UserHandle.SYSTEM);
            if(((actual^before)&~allowed)!=0)throw new IOException("foreign-role-permission-flags-refused");
            boolean granted=pm.checkPermission(PERMISSIONS[index],PACKAGES[index])==PackageManager.PERMISSION_GRANTED;
            int temporaryFixed=0;
            if(granted!=wanted&&(actual&PackageManager.FLAG_PERMISSION_SYSTEM_FIXED)!=0) {
                temporaryFixed=actual&PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;pm.updatePermissionFlags(PERMISSIONS[index],PACKAGES[index],PackageManager.FLAG_PERMISSION_SYSTEM_FIXED,0,UserHandle.SYSTEM);
            }
            try{if(granted!=wanted){if(wanted)pm.grantRuntimePermission(PACKAGES[index],PERMISSIONS[index],UserHandle.SYSTEM);else pm.revokeRuntimePermission(PACKAGES[index],PERMISSIONS[index],UserHandle.SYSTEM);}}
            finally{int completeMask=allowed|temporaryFixed;int completeValue=(before&allowed)|((allowed&PackageManager.FLAG_PERMISSION_SYSTEM_FIXED)==0?temporaryFixed:0);pm.updatePermissionFlags(PERMISSIONS[index],PACKAGES[index],completeMask,completeValue,UserHandle.SYSTEM);}
            if(pm.getPermissionFlags(PERMISSIONS[index],PACKAGES[index],UserHandle.SYSTEM)!=before||(pm.checkPermission(PERMISSIONS[index],PACKAGES[index])==PackageManager.PERMISSION_GRANTED)!=wanted)throw new IOException("role-broker-original-policy-unconfirmed");
            output.put("status","restored");success=true;
        }catch(Throwable error){try{output.put("status","failed").put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
