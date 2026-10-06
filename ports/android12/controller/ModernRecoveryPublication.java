// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Publishes fixed recovery code under the same lock as every owner mutation. */
public final class ModernRecoveryPublication {
    private final Context context;private final File module,installation,carriers,hook;private final boolean test;
    private static final String[] SCRIPTS={"module.prop","customize.sh","control.sh","service.sh","uninstall.sh","recovery-boot.sh"};
    ModernRecoveryPublication(Context context,File module,File installation,boolean test)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-publication-required");
        this.context=context;this.module=module.getAbsoluteFile();this.installation=installation.getAbsoluteFile();this.test=test;
        if(test) {
            if(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))||!this.installation.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/state")||!this.module.equals(new File(this.installation.getParentFile(),"module")))throw new SecurityException("fixed-publication-fixture-required");
            hook=new File(this.installation.getParentFile(),"hooks/codex-modern-installation-recovery.sh");
        }else {
            if(!this.module.equals(ModernSelectionController.MODULE)||!this.installation.equals(ModernSelectionController.INSTALLATION))throw new SecurityException("fixed-publication-path-required");
            hook=new File("/data/adb/service.d/codex-modern-installation-recovery.sh");
        }
        carriers=new File(test?"/data/local/tmp/codex-modern-persistence-tests":ModernSelectionController.CARRIERS.getPath());
        for(File path:Arrays.asList(this.module,this.installation,hook,carriers))ModernStateFiles.canonical(path);
    }
    private Map<String,String> payload()throws Exception {
        File inventory=new File(module,"payload.sha256");ModernStateFiles.canonical(inventory);
        if(!inventory.isFile()||inventory.length()>16384)throw new IOException("publication-inventory-unavailable");
        Set<String> expected=new HashSet<>(Arrays.asList("controller.zip","installation.properties","module-profile.json","THIRD_PARTY.md","LICENSE-phhusson-ims","LICENSE-upstream","system/etc/permissions/privapp-permissions-codex-vowifi-modern.xml"));
        expected.addAll(Arrays.asList(SCRIPTS));expected.addAll(Arrays.asList(ModernInstallationTransaction.APK_PATHS));
        Map<String,String> result=new HashMap<>();
        for(String line:new String(Files.readAllBytes(inventory.toPath()),"UTF-8").split("\\n")) {
            if(line.length()<67||!line.substring(0,64).matches("[0-9a-f]{64}")||!line.substring(64,66).equals("  "))throw new IOException("publication-inventory-refused");
            String name=line.substring(66);if(!expected.contains(name)||result.put(name,line.substring(0,64))!=null)throw new IOException("publication-inventory-refused");
            File path=new File(module,name);ModernStateFiles.canonical(path);if(!line.substring(0,64).equals(ModernInstallationTransaction.digest(path)))throw new IOException("publication-payload-changed");
        }
        if(!result.keySet().equals(expected))throw new IOException("publication-inventory-refused");return result;
    }
    static boolean payloadMatches(Context context,File module,File installation,boolean test) {
        try{new ModernRecoveryPublication(context,module,installation,test).payload();return true;}catch(Exception failure){return false;}
    }
    static String generation(String helper,String script)throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((helper+":"+script).getBytes("UTF-8"));StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();
    }
    static File current(File installation)throws Exception {
        File pointer=new File(installation,"recovery/current");ModernStateFiles.canonical(pointer);if(!pointer.exists())return null;
        if(!pointer.isFile()||pointer.length()!=65)throw new IOException("publication-pointer-refused");
        String value=new String(Files.readAllBytes(pointer.toPath()),"UTF-8");if(!value.matches("[0-9a-f]{64}\\n"))throw new IOException("publication-pointer-refused");
        File directory=new File(installation,"recovery/generations/"+value.trim()),helper=new File(directory,"controller.zip"),script=new File(directory,"recovery-boot.sh");
        for(File file:Arrays.asList(directory,helper,script))ModernStateFiles.canonical(file);
        if(!value.trim().equals(generation(ModernInstallationTransaction.digest(helper),ModernInstallationTransaction.digest(script))))throw new IOException("publication-generation-changed");return helper;
    }
    private static void replace(File target,byte[] bytes,int mode)throws Exception {
        ModernStateFiles.canonical(target);if(target.exists()&&!target.isFile())throw new IOException("publication-target-refused");
        File temporary=new File(target.getPath()+".new");ModernStateFiles.canonical(temporary);if(temporary.exists()&&!temporary.isFile())throw new IOException("publication-target-refused");
        try(FileOutputStream stream=new FileOutputStream(temporary)){android.system.Os.chmod(temporary.getPath(),mode);stream.write(bytes);stream.getFD().sync();}
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    JSONObject stageForTest()throws Exception {if(!test)throw new SecurityException("fixed-publication-fixture-required");return publish(false);}
    JSONObject publish()throws Exception {return publish(true);}
    private JSONObject publish(boolean activate)throws Exception {
        try(ModernControllerLock held=new ModernControllerLock(carriers,test)) {
            Map<String,String> inventory=payload();File recovery=new File(installation,"recovery");ModernStateFiles.canonical(recovery);
            String candidate=inventory.get("controller.zip"),version=generation(candidate,inventory.get("recovery-boot.sh"));File active=current(installation);boolean same=active!=null&&version.equals(active.getParentFile().getName());
            try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,test,held)) {
                transaction.validatePayload();String phase=transaction.phase();
                if(!same&&!"ABSENT".equals(phase)) {
                    if(!"RESTORED".equals(phase))throw new IOException("original-recovery-required-before-helper-upgrade");
                    transaction.restore(); // Also rejects a pending owner, including a carrier already restored.
                }
            }
            ModernStateFiles files=new ModernStateFiles(recovery);File generations=new ModernStateFiles(new File(recovery,"generations")).root;
            File directory=new File(generations,version);ModernStateFiles.canonical(directory);
            if(!directory.exists()) {
                ModernStateFiles staging=new ModernStateFiles(new File(generations,"staging-"+version));
                replace(staging.file("controller.zip"),Files.readAllBytes(new File(module,"controller.zip").toPath()),0600);
                replace(staging.file("recovery-boot.sh"),Files.readAllBytes(new File(module,"recovery-boot.sh").toPath()),0700);
                String checksums=candidate+"  controller.zip\n"+inventory.get("recovery-boot.sh")+"  recovery-boot.sh\n";
                replace(staging.file("recovery.sha256"),checksums.getBytes("UTF-8"),0600);
                if(!candidate.equals(ModernInstallationTransaction.digest(staging.file("controller.zip")))||!inventory.get("recovery-boot.sh").equals(ModernInstallationTransaction.digest(staging.file("recovery-boot.sh"))))throw new IOException("publication-readback-unconfirmed");
                Files.move(staging.root.toPath(),directory.toPath(),StandardCopyOption.ATOMIC_MOVE);
            }
            File helper=new File(directory,"controller.zip"),script=new File(directory,"recovery-boot.sh");
            for(File file:Arrays.asList(helper,script,new File(directory,"recovery.sha256")))ModernStateFiles.canonical(file);
            if(!version.equals(generation(ModernInstallationTransaction.digest(helper),ModernInstallationTransaction.digest(script))))throw new IOException("publication-readback-unconfirmed");
            File checksum=new File(directory,"recovery.sha256");String expected=candidate+"  controller.zip\n"+inventory.get("recovery-boot.sh")+"  recovery-boot.sh\n";
            if(!checksum.isFile()||checksum.length()>1024||!expected.equals(new String(Files.readAllBytes(checksum.toPath()),"UTF-8")))throw new IOException("publication-readback-unconfirmed");
            if(!activate)return new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("unselected_complete_generation_staged",true);
            ModernStateFiles.canonical(hook.getParentFile());
            if(!hook.getParentFile().isDirectory()&&!hook.getParentFile().mkdirs())throw new IOException("publication-hook-directory-unavailable");
            // The stable dispatcher reads only the committed generation pointer.
            // Staging/copy interruptions leave the previous generation usable.
            replace(files.file("recovery-boot.sh"),Files.readAllBytes(new File(module,"recovery-boot.sh").toPath()),0700);
            String entry="#!/system/bin/sh\nexec sh "+files.file("recovery-boot.sh").getPath()+"\n";replace(hook,entry.getBytes("UTF-8"),0700);
            replace(files.file("current"),(version+"\n").getBytes("UTF-8"),0600);
            if(!candidate.equals(ModernInstallationTransaction.digest(helper))||!inventory.get("recovery-boot.sh").equals(ModernInstallationTransaction.digest(script)))throw new IOException("publication-readback-unconfirmed");
            if(!helper.equals(current(installation)))throw new IOException("publication-readback-unconfirmed");
            return new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("independent_recovery_published",true).put("helper_generation_unchanged",same);
        }
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {if(args.length!=1||!"publish".equals(args[0]))throw new SecurityException("fixed-publication-command-required");
            output=new ModernRecoveryPublication(ModernSelectionController.context(),ModernSelectionController.MODULE,ModernSelectionController.INSTALLATION,false).publish();success=true;
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
