// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Original property presence/value is shared across all selected IWLAN owners. */
final class ModernSharedIwlan {
    static final String KEY="ro.telephony.iwlan_operation_mode";
    private final ModernStateFiles files;
    ModernSharedIwlan(File root)throws Exception {files=new ModernStateFiles(root);}
    private static Properties property()throws Exception {
        Object handle=SystemProperties.class.getMethod("find",String.class).invoke(null,KEY);
        Properties value=new Properties();value.setProperty("present",Boolean.toString(handle!=null));
        value.setProperty("value",SystemProperties.get(KEY));return value;
    }
    private Properties record()throws Exception {
        Properties value=files.read("iwlan-mode.properties");
        if(!"1".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||
            !Arrays.asList("ORIGINAL","CHANGING","AP_ASSISTED","RESTORING","RESTORED").contains(value.getProperty("phase"))||
            !Arrays.asList("true","false").contains(value.getProperty("before.present"))||value.getProperty("before.value")==null)
            throw new IOException("shared-mode-record-refused");return value;
    }
    private static boolean original(Properties record)throws Exception {
        Properties actual=property();return record.getProperty("before.present").equals(actual.getProperty("present"))&&record.getProperty("before.value").equals(actual.getProperty("value"));
    }
    /** null means a current manager without the old legacy switch (AOSP14+). */
    static Boolean legacy(String dump,int slot)throws Exception {
        return ModernIwlanObservation.read(dump,slot).legacy;
    }
    private static void reset(String... arguments)throws Exception {
        ArrayList<String> command=new ArrayList<>();command.add("resetprop");command.add("-n");command.addAll(Arrays.asList(arguments));
        java.lang.Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
        Thread discard=new Thread(()->{try(InputStream stream=child.getInputStream()){byte[] block=new byte[1024];while(stream.read(block)!=-1){}}catch(IOException ignored){}});discard.setDaemon(true);discard.start();
        if(!child.waitFor(8,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("shared-mode-reset-timeout");}
        discard.join(500);if(child.exitValue()!=0||discard.isAlive())throw new IOException("shared-mode-reset-refused");
    }
    boolean acquire(int slot)throws Exception {
        if(!files.file("iwlan-mode.properties").exists()){
            Properties before=property();Properties value=new Properties();value.setProperty("schema","1");value.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);
            value.setProperty("before.present",before.getProperty("present"));value.setProperty("before.value",before.getProperty("value"));value.setProperty("phase","ORIGINAL");files.write("iwlan-mode.properties",value);
        }
        Properties value=record();String phase=value.getProperty("phase");
        if("AP_ASSISTED".equals(phase)){if(!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");return false;}
        if(!Arrays.asList("ORIGINAL","CHANGING").contains(phase))throw new IOException("shared-mode-recovery-required");
        if("ORIGINAL".equals(phase)&&!original(value))throw new IOException("external-shared-mode-change-refused");
        ModernIwlanObservation.Result observation=ModernIwlanObservation.read(ModernSystemObservation.telephonyDebug(),slot);
        Boolean old=observation.legacy;
        if(Boolean.FALSE.equals(old)&&Boolean.FALSE.equals(observation.cachedWlan))throw new IOException("shared-mode-cache-refresh-required");
        if(old==null&&Build.VERSION.SDK_INT<=33)throw new IOException("shared-mode-legacy-observation-required");
        if(Boolean.TRUE.equals(old)||"CHANGING".equals(phase)){
            value.setProperty("phase","CHANGING");files.write("iwlan-mode.properties",value);
            reset(KEY,"AP-assisted");if(!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("shared-mode-apply-unconfirmed");
            value.setProperty("phase","AP_ASSISTED");files.write("iwlan-mode.properties",value);return true;
        }
        return false;
    }
    boolean release(boolean otherIwlanOwner)throws Exception {
        if(!files.file("iwlan-mode.properties").exists())return false;
        Properties value=record();String phase=value.getProperty("phase");
        if(otherIwlanOwner){
            if(("AP_ASSISTED".equals(phase)&&!"AP-assisted".equals(SystemProperties.get(KEY)))||("ORIGINAL".equals(phase)&&!original(value)))throw new IOException("external-shared-mode-change-refused");
            if(!Arrays.asList("ORIGINAL","AP_ASSISTED").contains(phase))throw new IOException("shared-mode-recovery-required");return false;
        }
        if("RESTORED".equals(phase)){if(!original(value))throw new IOException("shared-mode-restore-unconfirmed");return false;}
        boolean changed=!"ORIGINAL".equals(phase);
        if(changed){
            if(!original(value)&&!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");
            value.setProperty("phase","RESTORING");files.write("iwlan-mode.properties",value);
            if(!original(value)){
                if(Boolean.parseBoolean(value.getProperty("before.present")))reset(KEY,value.getProperty("before.value"));else reset("--delete",KEY);
            }
        }
        if(!original(value))throw new IOException("shared-mode-restore-unconfirmed");
        value.setProperty("phase","RESTORED");files.write("iwlan-mode.properties",value);return changed;
    }
    void finishCycle(File archive)throws Exception {
        if(!files.file("iwlan-mode.properties").exists())return;
        Properties value=record();if(!"RESTORED".equals(value.getProperty("phase"))||!original(value))throw new IOException("shared-mode-archive-refused");
        ModernStateFiles.canonical(archive);if(archive.exists())throw new IOException("shared-mode-archive-exists");
        java.nio.file.Files.move(files.file("iwlan-mode.properties").toPath(),archive.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
