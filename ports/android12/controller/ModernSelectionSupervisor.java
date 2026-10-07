// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** One device-wide inventory tick; a resident process repeats it under a separate lock. */
public final class ModernSelectionSupervisor {
    private final Context context;private final File module,installation,coordination,carriers,recoveryHelper;private final boolean test;private final String suffix,generation;
    ModernSelectionSupervisor(Context context,File module,File installation,File coordination,boolean test)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-supervisor-required");
        this.context=context;this.module=module.getAbsoluteFile();this.installation=installation.getAbsoluteFile();this.coordination=coordination.getAbsoluteFile();this.test=test;
        if(test) {
            if(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))||!this.coordination.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/selection")||!this.module.equals(new File(this.coordination.getParentFile(),"module"))||!this.installation.equals(new File(this.coordination.getParentFile(),"state")))throw new SecurityException("fixed-supervisor-fixture-required");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((this.coordination.getParentFile().getName()+":selection").getBytes("UTF-8"));StringBuilder value=new StringBuilder();for(byte b:hash)value.append(String.format(Locale.ROOT,"%02x",b&255));suffix="-"+value.substring(0,32);
        } else {
            if(!this.module.equals(ModernSelectionController.MODULE)||!this.installation.equals(ModernSelectionController.INSTALLATION)||!this.coordination.equals(ModernSelectionController.COORDINATION))throw new SecurityException("fixed-supervisor-path-required");suffix="";
        }
        carriers=new File(test?"/data/local/tmp/codex-modern-persistence-tests":ModernSelectionController.CARRIERS.getPath());
        for(File path:Arrays.asList(this.module,this.installation,this.coordination,carriers))ModernStateFiles.canonical(path);
        recoveryHelper=ModernRecoveryPublication.current(this.installation);
        generation=recoveryHelper!=null?ModernInstallationTransaction.digest(recoveryHelper):null;
    }
    private boolean enabled(){return module.isDirectory()&&!new File(module,"disable").exists()&&!new File(module,"remove").exists()&&ModernRecoveryPublication.payloadMatches(context,module,installation,test);}
    private List<Properties> inventory()throws Exception {
        File root=new File(coordination,"owners");ModernStateFiles.canonical(root);if(!root.exists())return Collections.emptyList();if(!root.isDirectory())throw new IOException("supervisor-owner-root-refused");
        File[] children=root.listFiles();if(children==null||children.length>64)throw new IOException("supervisor-inventory-unavailable");Arrays.sort(children,Comparator.comparing(File::getName));List<Properties> owners=new ArrayList<>();
        for(File child:children) {
            ModernStateFiles.canonical(child);if(!child.isDirectory()||!child.getName().matches("slot-[0-7]-sub-[0-9]+"))throw new IOException("supervisor-owner-path-refused");
            File record=new File(child,"selection.properties");ModernStateFiles.canonical(record);
            if(!record.exists()) {
                File[] contents=child.listFiles();File core=new File(carriers,child.getName()+suffix);ModernStateFiles.canonical(core);
                if(contents!=null&&contents.length==0&&!core.exists())continue;
                throw new IOException("supervisor-untracked-owner-state");
            }
            Properties value=new ModernStateFiles(child).read("selection.properties");
            if(!"3".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!value.getProperty("slot","").matches("[0-7]")||!value.getProperty("sub","").matches("0|[1-9][0-9]{0,9}")||!child.getName().equals("slot-"+value.getProperty("slot")+"-sub-"+value.getProperty("sub"))||!value.getProperty("token","").matches("[0-9a-f]{32}")||!value.getProperty("mask","").matches("[1-7]")||!Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(value.getProperty("phase")))throw new IOException("supervisor-owner-record-refused");
            Integer.parseInt(value.getProperty("sub"));ModernSelectedPermissions.validateRecord(value);owners.add(value);
        }
        return owners;
    }
    private void carrierInventory(List<Properties> owners)throws Exception {
        Set<String> tracked=new HashSet<>();for(Properties owner:owners)tracked.add("slot-"+owner.getProperty("slot")+"-sub-"+owner.getProperty("sub")+suffix);
        File[] children=carriers.listFiles();if(children==null)throw new IOException("supervisor-carrier-inventory-unavailable");
        for(File child:children)if(child.getName().startsWith("slot-")&&(!test||child.getName().endsWith(suffix))) {
            ModernStateFiles.canonical(child);
            if(!child.isDirectory())throw new IOException("supervisor-carrier-path-refused");
            if(tracked.contains(child.getName()))continue;
            File phase=new File(child,"phase");ModernStateFiles.canonical(phase);
            if(!phase.isFile()||phase.length()>64||!"RESTORED".equals(new String(java.nio.file.Files.readAllBytes(phase.toPath()),"UTF-8")))throw new IOException("supervisor-untracked-carrier-state");
        }
    }
    JSONObject tick(boolean forceRecovery)throws Exception {
        JSONObject output=new JSONObject();int renewed=0,restored=0,pending=0,active=0,waitingOwner=0;JSONArray failures=new JSONArray();boolean permissionRestored=false;
        try(ModernControllerLock held=new ModernControllerLock(carriers,test)) {
            // Publication holds this same lock. An older resident must stop before
            // reading or changing any owner after the independent helper changes.
            if(generation!=null) {
                File published;try{published=ModernRecoveryPublication.current(installation);}catch(Exception unavailable){published=null;}
                if(!recoveryHelper.equals(published))return output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("generation_changed",true).put("pending",0).put("stop_supervisor",true);
            }
            // Validate the complete inventory before changing any owner. A bad
            // trailing entry must not be silently hidden by an early return.
            List<Properties> owners=inventory();carrierInventory(owners);String installationPhase="ABSENT";
            if(new File(installation,"baseline.properties").isFile())try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,test,held)){installationPhase=transaction.phase();}
            boolean recovery=forceRecovery||!enabled()||Arrays.asList("PREPARING","RESTORING").contains(installationPhase);
            for(Properties owner:owners) {
                String phase=owner.getProperty("phase");
                if("RESTORED".equals(phase)&&!recovery)continue;
                int slot=Integer.parseInt(owner.getProperty("slot")),sub=Integer.parseInt(owner.getProperty("sub"));
                try(ModernSelectionTransaction transaction=ModernSelectionTransaction.recovery(context,slot,sub,module,installation,coordination,new File(carriers,"slot-"+slot+"-sub-"+sub+suffix),test,held)) {
                    String token=owner.getProperty("token");
                    if("RESTORED".equals(phase)){transaction.confirmRestoredOwner();}
                    else if("ARCHIVING".equals(phase)){transaction.finishArchive(token);restored++;}
                    else if("ACTIVE".equals(phase)&&!recovery&&transaction.liveOwner()) {if(transaction.renew(token)){renewed++;active++;}else restored++;}
                    else {transaction.restore(token);if("true".equals(transaction.status().getProperty("recovery_pending_owner"))){pending++;waitingOwner++;}else restored++;}
                }catch(Exception failure){pending++;failures.put(failure.getClass().getSimpleName());}
            }
            if(recovery&&pending==0&&new File(installation,"baseline.properties").isFile()) {
                try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,test,held)) {transaction.restore();permissionRestored="RESTORED".equals(transaction.phase());}
                catch(Exception failure){pending++;failures.put(failure.getClass().getSimpleName());}
            }
            output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("recovery_requested",recovery).put("owner_count",owners.size()).put("renewed",renewed).put("restored",restored).put("active",active).put("pending",pending).put("waiting_owner",waitingOwner).put("failures",failures).put("installation_policy_restored",permissionRestored).put("stop_supervisor",recovery&&pending==0&&(permissionRestored||!new File(installation,"baseline.properties").exists()));
        }
        return output;
    }
    boolean alive()throws Exception {
        File path=new File(coordination,"supervisor.lock");ModernStateFiles.canonical(path);if(!path.exists())return false;
        if(!path.isFile())throw new IOException("supervisor-lock-path-refused");
        try(RandomAccessFile file=new RandomAccessFile(path,"rw");FileLock probe=file.getChannel().tryLock()) {
            if(probe!=null||recoveryHelper==null||file.length()!=65)return false;
            byte[] contents=new byte[65];file.readFully(contents);return (recoveryHelper.getParentFile().getName()+"\n").equals(new String(contents,"UTF-8"));
        }
    }
    static String processStartTicks(int pid)throws Exception {
        if(pid<=1)throw new IOException("fixture-resident-process-owner-refused");
        File path=new File("/proc/"+pid+"/stat");
        byte[] bytes=java.nio.file.Files.readAllBytes(path.toPath());
        if(bytes.length>4096)throw new IOException("fixture-resident-process-owner-refused");
        String value=new String(bytes,"UTF-8");int end=value.lastIndexOf(')');
        if(!value.startsWith(pid+" (")||end<0)throw new IOException("fixture-resident-process-owner-refused");
        String[] fields=value.substring(end+1).trim().split("\\s+");
        // The first suffix field is Linux stat field 3; starttime is field 22.
        if(fields.length<20||!fields[19].matches("[0-9]{1,20}"))throw new IOException("fixture-resident-process-owner-refused");
        return fields[19];
    }
    JSONObject runResident()throws Exception {
        String classpath=System.getenv("CLASSPATH");boolean matching=ModernFixtureHelperPaths.residentSourceAllowed(test,classpath,recoveryHelper==null?null:recoveryHelper.getPath());
        if(matching&&test&&ModernFixtureHelperPaths.contains(classpath)) {
            File source=new File(classpath);ModernStateFiles.canonical(source);
            matching=ModernInstallationTransaction.digest(source).equals(generation);
        }
        if(generation==null||!matching||!generation.equals(ModernInstallationTransaction.digest(recoveryHelper)))throw new IOException("published-supervisor-helper-required");
        ModernStateFiles state=new ModernStateFiles(coordination);
        try(RandomAccessFile file=new RandomAccessFile(state.file("supervisor.lock"),"rw")) {
            android.system.Os.chmod(state.file("supervisor.lock").getPath(),0600);FileLock acquired=null;long deadline=SystemClock.elapsedRealtime()+60000;
            while(acquired==null&&SystemClock.elapsedRealtime()<deadline){acquired=file.getChannel().tryLock();if(acquired==null)Thread.sleep(250);}
            try(FileLock lock=acquired) {
                if(lock==null)throw new IOException("supervisor-already-running");
                file.setLength(0);file.write((recoveryHelper.getParentFile().getName()+"\n").getBytes("UTF-8"));file.getFD().sync();
                Properties identity=new Properties();identity.setProperty("schema","1");identity.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);identity.setProperty("generation",recoveryHelper.getParentFile().getName());identity.setProperty("pid",Integer.toString(android.os.Process.myPid()));identity.setProperty("start_ticks",processStartTicks(android.os.Process.myPid()));identity.setProperty("boot",Integer.toString(ModernPhoneRefresh.boot(context)));state.write("resident.properties",identity);
                String previous="";
                for(;;) {
                    JSONObject output;try{output=tick(false);}catch(Exception error){output=new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("error",error.getClass().getSimpleName());}
                    String current=output.toString();if(!current.equals(previous)){System.out.println(current);System.out.flush();previous=current;}
                    if(output.optBoolean("stop_supervisor"))return output;Thread.sleep(15000);
                }
            }
        }
    }
    public static void main(String[] args) {
        JSONObject output=new JSONObject();boolean success=false;
        try {
            if(args.length!=1||!Arrays.asList("tick","restore-all","run","alive").contains(args[0]))throw new SecurityException("fixed-supervisor-command-required");
            Context context=ModernSelectionController.context();ModernSelectionSupervisor supervisor=new ModernSelectionSupervisor(context,ModernSelectionController.MODULE,ModernSelectionController.INSTALLATION,ModernSelectionController.COORDINATION,false);
            if("alive".equals(args[0])) {
                output.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("supervisor_alive",supervisor.alive());success=true;
            }
            else if(!"run".equals(args[0])){output=supervisor.tick("restore-all".equals(args[0]));success=output.getInt("pending")==0;}
            else {
                output=supervisor.runResident();success=true;
            }
            output.put("carrier_call_sms_verified",false).put("dual_active_sim_verified",false).put("modern_device_lifecycle_verified",false);
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(success?0:1);
    }
}
