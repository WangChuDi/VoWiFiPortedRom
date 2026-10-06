// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Device-wide original role policy, referenced by component owners under the carrier lock. */
final class ModernSharedSelectedRoles {
    private static final int[] GROUPS={1,4};
    private final Context context;private final File coordination,carriers;private final ModernControllerLock held;
    private final ModernStateFiles resources;private final boolean test;private final String suffix;
    ModernSharedSelectedRoles(Context context,File coordination,ModernControllerLock held,boolean test)throws Exception {
        this.context=context;this.coordination=coordination.getAbsoluteFile();this.held=held;this.test=test;
        carriers=new File(test?"/data/local/tmp/codex-modern-persistence-tests":"/data/adb/codex_vowifi_stack_modern/transactions");held.requireHeld(carriers);
        ModernStateFiles.canonical(this.coordination);
        if(test) {
            if(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))||!this.coordination.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/selection"))throw new SecurityException("fixed-shared-role-fixture-required");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((this.coordination.getParentFile().getName()+":selection").getBytes("UTF-8"));StringBuilder value=new StringBuilder();for(byte b:hash)value.append(String.format(Locale.ROOT,"%02x",b&255));suffix="-"+value.substring(0,32);
        } else {
            if(!this.coordination.equals(ModernSelectionController.COORDINATION))throw new SecurityException("fixed-shared-role-path-required");suffix="";
        }
        resources=new ModernStateFiles(new File(this.coordination,"resources"));
    }
    private String build(){return Build.VERSION.SDK_INT+":"+Build.FINGERPRINT;}
    private static boolean group(Properties record,int group){return (Integer.parseInt(record.getProperty("mask"))&group)!=0;}
    private static String shared(int group){return "role.shared."+group+".";}
    private static String name(int group){return "roles-"+group+".properties";}
    private static boolean finished(Properties owner){return Arrays.asList("RESTORED","ARCHIVING").contains(owner.getProperty("phase"));}
    private void subject(Properties owner)throws Exception {
        if(!"3".equals(owner.getProperty("schema"))||!build().equals(owner.getProperty("build"))||!owner.getProperty("slot","").matches("[0-7]")||!owner.getProperty("sub","").matches("0|[1-9][0-9]{0,9}")||!owner.getProperty("token","").matches("[0-9a-f]{32}")||!owner.getProperty("mask","").matches("[1-7]"))throw new IOException("shared-role-owner-record-refused");
        Integer.parseInt(owner.getProperty("sub"));
    }
    private List<Properties> owners()throws Exception {
        held.requireHeld(carriers);File directory=new File(coordination,"owners");ModernStateFiles.canonical(directory);if(!directory.exists())return Collections.emptyList();
        File[] children=directory.listFiles();if(!directory.isDirectory()||children==null||children.length>64)throw new IOException("shared-role-inventory-refused");Arrays.sort(children,Comparator.comparing(File::getName));List<Properties> result=new ArrayList<>();Set<String> tokens=new HashSet<>();
        for(File child:children) {
            ModernStateFiles.canonical(child);if(!child.isDirectory()||!child.getName().matches("slot-[0-7]-sub-[0-9]+"))throw new IOException("shared-role-inventory-refused");
            ModernStateFiles state=new ModernStateFiles(child);
            if(!state.file("selection.properties").exists()) {
                File[] content=child.listFiles();File core=new File(carriers,child.getName()+suffix);ModernStateFiles.canonical(core);
                if(content!=null&&content.length==0&&!core.exists())continue;throw new IOException("shared-role-inventory-refused");
            }
            Properties owner=state.read("selection.properties");subject(owner);
            if(!tokens.add(owner.getProperty("token")))throw new IOException("shared-role-inventory-refused");
            if(!child.getName().equals("slot-"+owner.getProperty("slot")+"-sub-"+owner.getProperty("sub"))||!Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(owner.getProperty("phase")))throw new IOException("shared-role-inventory-refused");
            ModernSelectedPermissions.validateRecord(owner);
            if(!finished(owner))for(int group:GROUPS)if(group(owner,group)&&(!owner.getProperty(shared(group)+"id","").matches("[0-9a-f]{32}")||!Arrays.asList("true","false").contains(owner.getProperty(shared(group)+"released"))))throw new IOException("shared-role-reference-unavailable");
            result.add(owner);
        }
        return result;
    }
    private static List<String> originalKeys(int group) {
        ArrayList<String> keys=new ArrayList<>();for(int index:group==1?new int[]{0}:new int[]{1,2})for(String field:Arrays.asList("uid","apk","flags","grant"))keys.add("role."+index+"."+field);
        if(group==1)for(int index=0;index<2;index++)keys.add("role.data.op."+index);return keys;
    }
    private static List<String> observedKeys(int group) {
        ArrayList<String> keys=new ArrayList<>();for(int index:group==1?new int[]{0}:new int[]{1,2}){keys.add("role."+index+".observed");keys.add("role."+index+".observed.grant");}
        if(group==1)for(int index=0;index<2;index++)keys.add("role.data.observed.op."+index);return keys;
    }
    private Properties resource(int group)throws Exception {
        Properties value=resources.read(name(group));
        if(!"1".equals(value.getProperty("schema"))||!build().equals(value.getProperty("build"))||!Integer.toString(group).equals(value.getProperty("mask"))||!value.getProperty("id","").matches("[0-9a-f]{32}")||!Arrays.asList("OWNED","RESTORING","RESTORED").contains(value.getProperty("phase")))throw new IOException("shared-role-resource-refused");
        ModernSelectedPermissions.validateRecord(value);if("true".equals(value.getProperty("goal.ready")))ModernSelectedPermissions.validateObserved(value);return value;
    }
    private void reference(Properties owner,Properties resource,int group)throws Exception {
        subject(owner);ModernSelectedPermissions.validateRecord(owner);
        if(!resource.getProperty("id").equals(owner.getProperty(shared(group)+"id")))throw new IOException("shared-role-reference-changed");
        for(String key:originalKeys(group))if(!Objects.equals(owner.getProperty(key),resource.getProperty(key)))throw new IOException("shared-role-reference-changed");
    }
    private List<Properties> holders(Properties resource,int group,Properties excluded)throws Exception {
        List<Properties> result=new ArrayList<>();
        for(Properties owner:owners())if(group(owner,group)&&!finished(owner)&&!"true".equals(owner.getProperty(shared(group)+"released"))) {
            reference(owner,resource,group);
            if(excluded==null||!owner.getProperty("token").equals(excluded.getProperty("token")))result.add(owner);
        }
        return result;
    }
    private void selected(Properties owner)throws Exception {
        int slot=Integer.parseInt(owner.getProperty("slot")),sub=Integer.parseInt(owner.getProperty("sub"));
        try(ModernProviderTransaction transaction=new ModernProviderTransaction(context,slot,sub,new File(carriers,"slot-"+slot+"-sub-"+sub+suffix),test,held)) {
            if(!transaction.verifySelection(Integer.parseInt(owner.getProperty("mask"))))throw new IOException("shared-role-active-provider-unconfirmed");
        }
    }
    private void repairActive(Properties resource,int group,List<Properties> participants)throws Exception {
        boolean active=false;
        for(Properties participant:participants)if("ACTIVE".equals(participant.getProperty("phase"))){selected(participant);active=true;}
        if(!active)return;
        if(!"OWNED".equals(resource.getProperty("phase"))||!"true".equals(resource.getProperty("goal.ready")))throw new IOException("shared-role-active-goal-unavailable");
        Properties target=ModernSelectedPermissions.activeTarget(resource);
        if(!ModernSelectedPermissions.matches(context,target))ModernSelectedPermissions.restore(context,target);
    }
    void snapshot(Properties owner)throws Exception {
        held.requireHeld(carriers);subject(owner);owners();
        for(int group:GROUPS)if(group(owner,group)) {
            Properties value;
            if(resources.file(name(group)).exists()) {
                value=resource(group);List<Properties> participants=holders(value,group,null);
                if("RESTORED".equals(value.getProperty("phase"))) {
                    if(!participants.isEmpty())throw new IOException("shared-role-recovery-required");
                    File history=new ModernStateFiles(new File(coordination,"resource-history")).file("roles-"+group+"-"+value.getProperty("id")+".properties");if(history.exists())throw new IOException("shared-role-archive-conflict");
                    Files.move(resources.file(name(group)).toPath(),history.toPath(),StandardCopyOption.ATOMIC_MOVE);value=null;
                } else {
                    if(!"OWNED".equals(value.getProperty("phase")))throw new IOException("shared-role-recovery-required");
                    if(participants.isEmpty()&&("true".equals(value.getProperty("goal.ready"))||!ModernSelectedPermissions.matches(context,value)))throw new IOException("shared-role-untracked-policy-refused");
                    repairActive(value,group,participants);
                }
            } else {for(Properties participant:owners())if(group(participant,group)&&!finished(participant))throw new IOException("shared-role-reference-unavailable");value=null;}
            if(value==null) {
                value=new Properties();value.setProperty("schema","1");value.setProperty("build",build());value.setProperty("id",UUID.randomUUID().toString().replace("-",""));value.setProperty("mask",Integer.toString(group));value.setProperty("phase","OWNED");ModernSelectedPermissions.snapshot(context,value);resources.write(name(group),value);
            }
            for(String key:originalKeys(group))owner.setProperty(key,value.getProperty(key));owner.setProperty(shared(group)+"id",value.getProperty("id"));owner.setProperty(shared(group)+"released","false");
        }
    }
    void requested(Properties owner)throws Exception {
        held.requireHeld(carriers);
        for(int group:GROUPS)if(group(owner,group)) {
            Properties value=resource(group);reference(owner,value,group);if(!"OWNED".equals(value.getProperty("phase")))throw new IOException("shared-role-recovery-required");ModernSelectedPermissions.requested(value);resources.write(name(group),value);
        }
        ModernSelectedPermissions.requested(owner);
    }
    void observed(Properties owner)throws Exception {
        held.requireHeld(carriers);ModernSelectedPermissions.observed(context,owner);
        for(int group:GROUPS)if(group(owner,group)) {
            Properties value=resource(group);reference(owner,value,group);
            if("true".equals(value.getProperty("goal.ready"))) {
                Properties target=ModernSelectedPermissions.activeTarget(value);selected(owner);
                if(!ModernSelectedPermissions.matches(context,target))ModernSelectedPermissions.restore(context,target);
                ModernSelectedPermissions.observed(context,owner);
            } else {for(String key:observedKeys(group))value.setProperty(key,owner.getProperty(key));value.setProperty("goal.ready","true");ModernSelectedPermissions.validateObserved(value);resources.write(name(group),value);}
        }
    }
    void maintain(Properties owner)throws Exception {
        held.requireHeld(carriers);subject(owner);selected(owner);
        for(int group:GROUPS)if(group(owner,group)) {
            Properties value=resource(group);reference(owner,value,group);if("true".equals(owner.getProperty(shared(group)+"released")))throw new IOException("shared-role-released-owner-refused");repairActive(value,group,holders(value,group,null));
        }
    }
    void verifyOwner(Properties owner)throws Exception {
        held.requireHeld(carriers);subject(owner);
        for(int group:GROUPS)if(group(owner,group)) {
            Properties value=resource(group);reference(owner,value,group);holders(value,group,null);
            if(!"OWNED".equals(value.getProperty("phase"))||!"true".equals(value.getProperty("goal.ready"))||!"false".equals(owner.getProperty(shared(group)+"released")))throw new IOException("shared-role-active-goal-unavailable");
            ModernSelectedPermissions.validate(context,value);
        }
    }
    void release(Properties owner)throws Exception {
        held.requireHeld(carriers);subject(owner);
        for(int group:GROUPS)if(group(owner,group)) {
            if("true".equals(owner.getProperty(shared(group)+"released")))continue;
            Properties value=resource(group);reference(owner,value,group);List<Properties> others=holders(value,group,owner);
            if(others.isEmpty()) {
                value.setProperty("phase","RESTORING");resources.write(name(group),value);ModernSelectedPermissions.restore(context,value);value.setProperty("phase","RESTORED");resources.write(name(group),value);
            } else {if(!"OWNED".equals(value.getProperty("phase")))throw new IOException("shared-role-recovery-required");repairActive(value,group,others);}
            owner.setProperty(shared(group)+"released","true");new ModernStateFiles(new File(coordination,"owners/slot-"+owner.getProperty("slot")+"-sub-"+owner.getProperty("sub"))).write("selection.properties",owner);
        }
    }
    void recoverUnused()throws Exception {
        held.requireHeld(carriers);owners();
        for(int group:GROUPS)if(resources.file(name(group)).exists()) {
            Properties value=resource(group);if(!holders(value,group,null).isEmpty())throw new IOException("shared-role-owner-recovery-required");
            if(!"RESTORED".equals(value.getProperty("phase"))) {
                // A snapshot killed before publishing its first owner has no
                // permission intent. Do not adopt a journal after lost mutations.
                if("true".equals(value.getProperty("goal.ready"))||!"OWNED".equals(value.getProperty("phase"))||!ModernSelectedPermissions.matches(context,value))throw new IOException("shared-role-untracked-policy-refused");
                value.setProperty("phase","RESTORED");resources.write(name(group),value);
            }
        }
    }
}
