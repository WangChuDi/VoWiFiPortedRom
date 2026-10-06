// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Shared property + cache reconstruction journal, serialized with all carrier owners. */
final class ModernSharedIwlan {
    static final String KEY="ro.telephony.iwlan_operation_mode";
    private final ModernStateFiles files;private final Context context;
    private final ModernControllerLock held;private final File lockRoot;
    ModernSharedIwlan(Context context,File root,ModernControllerLock held,boolean test)throws Exception {
        this.context=context;this.held=held;
        lockRoot=new File(test?"/data/local/tmp/codex-modern-persistence-tests":"/data/adb/codex_vowifi_stack_modern/transactions");held.requireHeld(lockRoot);
        String path=root.getAbsolutePath();
        if(!(test?path.matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/(shared-mode|selection/resources)"):path.equals("/data/adb/codex_vowifi_stack_modern/coordination/resources")))throw new SecurityException("fixed-shared-mode-path-required");
        files=new ModernStateFiles(root);
    }
    private static Properties property()throws Exception {
        Object handle=SystemProperties.class.getMethod("find",String.class).invoke(null,KEY);
        Properties value=new Properties();value.setProperty("present",Boolean.toString(handle!=null));
        value.setProperty("value",SystemProperties.get(KEY));return value;
    }
    private Properties record()throws Exception {
        held.requireHeld(lockRoot);Properties value=files.read("iwlan-mode.properties");
        if(!"2".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||
            !Arrays.asList("ORIGINAL","CHANGING","REFRESH_AP","AP_ASSISTED","RESTORING","REFRESH_ORIGINAL","RESTORED").contains(value.getProperty("phase"))||
            !Arrays.asList("true","false").contains(value.getProperty("before.present"))||value.getProperty("before.value")==null||
            !Arrays.asList("true","false","unknown").contains(value.getProperty("before.legacy"))||
            !value.getProperty("slot","").matches("[0-7]"))throw new IOException("shared-mode-record-refused");
        return value;
    }
    private static boolean original(Properties record)throws Exception {
        Properties actual=property();return record.getProperty("before.present").equals(actual.getProperty("present"))&&record.getProperty("before.value").equals(actual.getProperty("value"));
    }
    static Boolean legacy(String dump,int slot)throws Exception {return ModernIwlanObservation.read(dump,slot).legacy;}
    private static void reset(String... arguments)throws Exception {
        ArrayList<String> command=new ArrayList<>();command.add("resetprop");command.add("-n");command.addAll(Arrays.asList(arguments));
        java.lang.Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
        Thread discard=new Thread(()->{try(InputStream stream=child.getInputStream()){byte[] block=new byte[1024];while(stream.read(block)!=-1){}}catch(IOException ignored){}});discard.setDaemon(true);discard.start();
        if(!child.waitFor(8,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("shared-mode-reset-timeout");}
        discard.join(500);if(child.exitValue()!=0||discard.isAlive())throw new IOException("shared-mode-reset-refused");
    }
    private void phase(Properties value,String phase)throws Exception {value.setProperty("phase",phase);files.write("iwlan-mode.properties",value);}
    private void pending(Properties value,String phase)throws Exception {
        ModernPhoneIdle.requireIdle(context);
        value.setProperty("refresh.pid",Integer.toString(ModernSystemObservation.phonePid()));value.setProperty("refresh.boot",Integer.toString(ModernPhoneRefresh.boot(context)));phase(value,phase);
    }
    private boolean refresh(Properties value,Boolean expected)throws Exception {
        int pid=Integer.parseInt(value.getProperty("refresh.pid","0")),boot=Integer.parseInt(value.getProperty("refresh.boot","-1"));
        if(pid<=1||boot<0)throw new IOException("shared-mode-refresh-marker-required");
        return ModernPhoneRefresh.refresh(context,pid,boot,Integer.parseInt(value.getProperty("slot")),expected);
    }
    private static ModernIwlanObservation.Result observe(int slot)throws Exception {
        ModernIwlanObservation.Result result=null;
        // A successful dumpsys exit can still omit a later direct-state field.
        // Retry fresh read-only observations; never infer legacy=false from absence.
        for(int attempt=0;attempt<3;attempt++) {
            result=ModernIwlanObservation.read(ModernSystemObservation.telephonyDebug(),slot);
            if(result.legacy!=null||Build.VERSION.SDK_INT>33)return result;
            if(attempt<2)Thread.sleep(200);
        }
        return result;
    }
    boolean acquire(int slot)throws Exception {
        held.requireHeld(lockRoot);
        if(!files.file("iwlan-mode.properties").exists()) {
            ModernIwlanObservation.Result observation=observe(slot);
            if(observation.legacy==null&&Build.VERSION.SDK_INT<=33)throw new IOException("shared-mode-legacy-observation-required");
            Properties before=property(),value=new Properties();value.setProperty("schema","2");value.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);
            value.setProperty("before.present",before.getProperty("present"));value.setProperty("before.value",before.getProperty("value"));
            value.setProperty("before.legacy",observation.legacy==null?"unknown":observation.legacy.toString());value.setProperty("slot",Integer.toString(slot));
            phase(value,"ORIGINAL");
        }
        Properties value=record();String phase=value.getProperty("phase");
        if("AP_ASSISTED".equals(phase)) {
            if(!"AP-assisted".equals(SystemProperties.get(KEY))) {
                if(!original(value)||Integer.toString(ModernPhoneRefresh.boot(context)).equals(value.getProperty("ap.boot")))throw new IOException("external-shared-mode-change-refused");
                pending(value,"CHANGING");phase="CHANGING";
            } else {
                ModernIwlanObservation.Result mode=observe(slot);
                if(Boolean.TRUE.equals(mode.legacy)||Boolean.FALSE.equals(mode.cachedWlan))throw new IOException("shared-mode-cache-changed");return false;
            }
        }
        if("ORIGINAL".equals(phase)) {
            if(!original(value))throw new IOException("external-shared-mode-change-refused");
            ModernIwlanObservation.Result mode=observe(slot);
            if(mode.legacy==null&&Build.VERSION.SDK_INT<=33)throw new IOException("shared-mode-legacy-observation-required");
            if(Boolean.TRUE.equals(mode.legacy))pending(value,"CHANGING");
            else if(Boolean.FALSE.equals(mode.cachedWlan))pending(value,"REFRESH_AP");
            else return false;
            phase=value.getProperty("phase");
        }
        if("CHANGING".equals(phase)) {
            if(!original(value)&&!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");
            if(!"AP-assisted".equals(SystemProperties.get(KEY)))reset(KEY,"AP-assisted");
            if(!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("shared-mode-apply-unconfirmed");
            phase(value,"REFRESH_AP");phase="REFRESH_AP";
        }
        if(!"REFRESH_AP".equals(phase))throw new IOException("shared-mode-recovery-required");
        boolean changed=!original(value);
        if(changed&&!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");
        boolean restarted=refresh(value,Boolean.FALSE);if(changed)value.setProperty("ap.boot",Integer.toString(ModernPhoneRefresh.boot(context)));phase(value,changed?"AP_ASSISTED":"ORIGINAL");return restarted;
    }
    boolean release(boolean otherIwlanOwner)throws Exception {
        held.requireHeld(lockRoot);if(!files.file("iwlan-mode.properties").exists())return false;
        Properties value=record();String phase=value.getProperty("phase");
        if(otherIwlanOwner) {
            if(("AP_ASSISTED".equals(phase)&&!"AP-assisted".equals(SystemProperties.get(KEY)))||("ORIGINAL".equals(phase)&&!original(value)))throw new IOException("external-shared-mode-change-refused");
            if(!Arrays.asList("ORIGINAL","AP_ASSISTED").contains(phase))throw new IOException("shared-mode-recovery-required");return false;
        }
        if("RESTORED".equals(phase)){if(!original(value))throw new IOException("shared-mode-restore-unconfirmed");return false;}
        if("ORIGINAL".equals(phase)){if(!original(value))throw new IOException("external-shared-mode-change-refused");phase(value,"RESTORED");return false;}
        if("CHANGING".equals(phase)&&original(value)&&ModernSystemObservation.phonePid()==Integer.parseInt(value.getProperty("refresh.pid","0"))&&ModernPhoneRefresh.boot(context)==Integer.parseInt(value.getProperty("refresh.boot","-1"))) {
            phase(value,"RESTORED");return false;
        }
        if(!Arrays.asList("RESTORING","REFRESH_ORIGINAL").contains(phase)) {
            if(!original(value)&&!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");
            pending(value,"RESTORING");phase="RESTORING";
        }
        if("RESTORING".equals(phase)) {
            if(!original(value)) {
                if(!"AP-assisted".equals(SystemProperties.get(KEY)))throw new IOException("external-shared-mode-change-refused");
                if(Boolean.parseBoolean(value.getProperty("before.present")))reset(KEY,value.getProperty("before.value"));else reset("--delete",KEY);
            }
            if(!original(value))throw new IOException("shared-mode-restore-unconfirmed");phase(value,"REFRESH_ORIGINAL");
        }
        if(!original(value))throw new IOException("external-shared-mode-change-refused");
        Boolean expected="unknown".equals(value.getProperty("before.legacy"))?null:Boolean.valueOf(value.getProperty("before.legacy"));
        boolean restarted=refresh(value,expected);phase(value,"RESTORED");return restarted;
    }
    void finishCycle(File archive)throws Exception {
        held.requireHeld(lockRoot);if(!files.file("iwlan-mode.properties").exists())return;
        Properties value=record();if(!"RESTORED".equals(value.getProperty("phase"))||!original(value))throw new IOException("shared-mode-archive-refused");
        ModernStateFiles.canonical(archive);if(archive.exists())throw new IOException("shared-mode-archive-exists");
        java.nio.file.Files.move(files.file("iwlan-mode.properties").toPath(),archive.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
