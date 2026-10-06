// SPDX-License-Identifier: GPL-2.0
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Device-wide permission preparation, separate from per-subscription carrier state. */
public final class ModernInstallationTransaction implements AutoCloseable {
    static final String[] PACKAGES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    static final String[] APK_PATHS={"system/priv-app/Api31Iwlan/Api31Iwlan.apk","system/priv-app/Api31Qns/Api31Qns.apk","system/priv-app/Api31Ims/Api31Ims.apk"};
    private static final String[][] RUNTIME={{"READ_PHONE_STATE"},{"READ_PHONE_STATE"},{"READ_PHONE_STATE","RECORD_AUDIO","SEND_SMS"}};
    private static final String[][] PRIVILEGED={{"READ_PRIVILEGED_PHONE_STATE","CONNECTIVITY_USE_RESTRICTED_NETWORKS","BIND_IMS_SERVICE"},{"READ_PRIVILEGED_PHONE_STATE"},{"READ_PRIVILEGED_PHONE_STATE","CONNECTIVITY_USE_RESTRICTED_NETWORKS","MODIFY_PHONE_STATE"}};
    private static final String SMS="android.permission.SEND_SMS";
    private static final String IPSEC="android:manage_ipsec_tunnels";
    private final Context context;private final PackageManager pm;private final AppOpsManager ops;
    private final File state,module;private final RandomAccessFile lockFile;private final FileLock lock;
    private final ModernControllerLock carrierLock;private final boolean ownsCarrierLock;
    private final File carrierRoot;private final boolean test;private boolean closed;
    public ModernInstallationTransaction(Context context,File module,File state,boolean test)throws Exception {
        this(context,module,state,test,null);
    }
    public ModernInstallationTransaction(Context context,File module,File state,boolean test,ModernControllerLock held)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-required");
        this.context=context;this.pm=context.getPackageManager();this.ops=context.getSystemService(AppOpsManager.class);
        this.test=test;
        this.state=state.getAbsoluteFile();this.module=module.getAbsoluteFile();
        if(test){
            if(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))||
                !this.state.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/state")||
                !this.module.equals(new File(this.state.getParentFile(),"module")))throw new SecurityException("owned-installation-fixture-required");
        }else if(!this.module.equals(new File("/data/adb/modules/codex_vowifi_stack_modern"))||!this.state.equals(new File("/data/adb/codex_vowifi_stack_modern/installation")))throw new SecurityException("installation-path-refused");
        canonical(this.module);
        // Recovery reads the original record independently of the current payload.
        // A partially removed module must not block earlier carrier recovery.
        // prepare()/ready() still validate every current and installed APK byte.
        canonical(this.state);
        if(new File(this.state,"baseline.properties").exists())read();
        else if(this.module.isDirectory())installed(payload(),true);
        else throw new IOException("installation-module-unavailable");
        privateDirectory(this.state);
        File path=file("permissions.lock");lockFile=new RandomAccessFile(path,"rw");
        FileLock acquired=null;try{acquired=lockFile.getChannel().tryLock();if(acquired==null)throw new IOException("installation-busy");}catch(Exception failure){lockFile.close();throw failure;}lock=acquired;
        ModernControllerLock shared=null;ownsCarrierLock=held==null;
        carrierRoot=new File(test?"/data/local/tmp/codex-modern-persistence-tests":"/data/adb/codex_vowifi_stack_modern/transactions");
        try{
            shared=ownsCarrierLock?new ModernControllerLock(carrierRoot,test):held;shared.requireHeld(carrierRoot);
        }catch(Exception failure){if(ownsCarrierLock&&shared!=null)shared.close();lock.release();lockFile.close();throw failure;}
        carrierLock=shared;
    }
    private static void canonical(File path)throws IOException {
        if(!path.equals(path.getCanonicalFile())||Files.isSymbolicLink(path.toPath()))throw new IOException("installation-alias-refused");
    }
    private static void privateDirectory(File path)throws Exception {
        canonical(path);if(!path.isDirectory()&&!path.mkdirs())throw new IOException("installation-state-unavailable");
        android.system.Os.chmod(path.getPath(),0700);
    }
    private File file(String name)throws IOException {
        File path=new File(state,name);canonical(path);
        if(!state.equals(path.getParentFile())||(path.exists()&&!path.isFile()))throw new IOException("installation-state-file-refused");return path;
    }
    static String digest(File input)throws Exception {
        if(!input.isFile()||input.length()>67108864)throw new IOException("installation-apk-unavailable");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=new FileInputStream(input)){byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)sha.update(buffer,0,count);}
        StringBuilder result=new StringBuilder();for(byte value:sha.digest())result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();
    }
    private Properties payload()throws Exception {
        File manifest=new File(module,"installation.properties");canonical(manifest);
        if(!manifest.isFile()||manifest.length()>4096)throw new IOException("installation-profile-unavailable");
        Properties result=new Properties();try(InputStream stream=new FileInputStream(manifest)){result.load(stream);}
        if(!"1".equals(result.getProperty("schema")))throw new IOException("installation-profile-refused");return result;
    }
    private ApplicationInfo[] installed(Properties expected,boolean requirePayload)throws Exception {
        ApplicationInfo[] apps=new ApplicationInfo[PACKAGES.length];
        for(int i=0;i<PACKAGES.length;i++){
            String sha=expected.getProperty("apk."+i);if(sha==null||!sha.matches("[0-9a-f]{64}"))throw new IOException("installation-apk-profile-refused");
            if(requirePayload){File apk=new File(module,APK_PATHS[i]);canonical(apk);if(!sha.equals(digest(apk)))throw new IOException("installation-payload-changed");}
            ApplicationInfo app=pm.getApplicationInfo(PACKAGES[i],0);apps[i]=app;
            if((app.flags&ApplicationInfo.FLAG_SYSTEM)==0||(app.privateFlags&ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)==0||app.uid<10000||app.uid>=100000||app.splitSourceDirs!=null)
                throw new SecurityException("primary-system-privapp-required");
            File apk=new File(app.sourceDir);canonical(apk);if(!sha.equals(digest(apk)))throw new SecurityException("installed-apk-mismatch");
            String uid=expected.getProperty("uid."+i);if(uid!=null&&!Integer.toString(app.uid).equals(uid))throw new SecurityException("installation-uid-changed");
            for(String permission:PRIVILEGED[i])if(pm.checkPermission("android.permission."+permission,PACKAGES[i])!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("installed-privileged-grant-required");
        }
        return apps;
    }
    private boolean granted(int index,String name){return pm.checkPermission("android.permission."+name,PACKAGES[index])==PackageManager.PERMISSION_GRANTED;}
    private int flags(int index,String name){return pm.getPermissionFlags("android.permission."+name,PACKAGES[index],UserHandle.SYSTEM);}
    private boolean exemption(){Set<String> permissions=pm.getWhitelistedRestrictedPermissions(PACKAGES[2],PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);return permissions!=null&&permissions.contains(SMS);}
    private int mode(ApplicationInfo[] apps){return ops.unsafeCheckOpNoThrow(IPSEC,apps[0].uid,PACKAGES[0]);}
    private void write(Properties value)throws Exception {
        File temporary=file("baseline.new");try(FileOutputStream stream=new FileOutputStream(temporary)){android.system.Os.chmod(temporary.getPath(),0600);value.store(stream,"private installation recovery");stream.getFD().sync();}
        Files.move(temporary.toPath(),file("baseline.properties").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private Properties read()throws Exception {
        File input=file("baseline.properties");if(!input.isFile()||input.length()>16384)throw new IOException("installation-baseline-unavailable");
        Properties value=new Properties();try(InputStream stream=new FileInputStream(input)){value.load(stream);}
        if(!"1".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build")))throw new SecurityException("installation-build-changed");
        if(!Arrays.asList("PREPARING","PREPARED","RESTORING","RESTORED").contains(value.getProperty("phase")))throw new IOException("installation-phase-refused");
        // Validate the complete recovery record before any grant/revoke/AppOp call.
        for(int i=0;i<PACKAGES.length;i++){
            if(!value.getProperty("apk."+i,"").matches("[0-9a-f]{64}"))throw new IOException("installation-apk-profile-refused");
            if(!value.getProperty("uid."+i,"").matches("[0-9]+"))throw new IOException("installation-uid-record-invalid");
            for(String permission:RUNTIME[i]){
                if(!Arrays.asList("true","false").contains(value.getProperty("grant."+i+"."+permission)))throw new IOException("installation-grant-record-invalid");
                Integer.parseInt(value.getProperty("flags."+i+"."+permission));
            }
        }
        if(!Arrays.asList("true","false").contains(value.getProperty("sms.exemption")))throw new IOException("installation-exemption-record-invalid");
        int before=Integer.parseInt(value.getProperty("ipsec.mode"));if(before<0||before>4)throw new IOException("installation-mode-record-invalid");
        return value;
    }
    public boolean ready()throws Exception {
        requireLock();
        ApplicationInfo[] apps=installed(payload(),true);
        for(int i=0;i<PACKAGES.length;i++)for(String permission:RUNTIME[i])if(!granted(i,permission))return false;
        return exemption()&&mode(apps)==AppOpsManager.MODE_ALLOWED;
    }
    void validatePayload()throws Exception {requireLock();installed(payload(),true);}
    public void archiveRestored()throws Exception {
        requireLock();ModernPhoneIdle.requireIdle(context);requireNoSelectedCarrier();Properties before=read();
        if(!"RESTORED".equals(before.getProperty("phase")))throw new IOException("restored-installation-required-before-archive");
        verifyRestored(before,installed(before,false));
        File history=new File(state,"history");privateDirectory(history);
        File destination=new File(history,"installation-"+UUID.randomUUID().toString().replace("-","")+".properties");canonical(destination);
        if(destination.exists())throw new IOException("installation-archive-conflict");
        Files.move(file("baseline.properties").toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
    }
    public void prepare()throws Exception {
        requireLock();
        validatePayload();
        if(new File(module,"disable").exists()||new File(module,"remove").exists())throw new IOException("enabled-installation-module-required");
        ModernPhoneIdle.requireIdle(context);
        Properties before;
        if(file("baseline.properties").exists()){
            before=read();installed(before,true);
            if("PREPARED".equals(before.getProperty("phase"))){if(!ready())throw new IOException("prepared-permissions-changed");return;}
            if(!"PREPARING".equals(before.getProperty("phase")))throw new IOException("installation-recovery-required");
        }else{
            before=payload();ApplicationInfo[] apps=installed(before,true);
            before.setProperty("schema","1");before.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);before.setProperty("phase","PREPARING");
            for(int i=0;i<PACKAGES.length;i++){
                before.setProperty("uid."+i,Integer.toString(apps[i].uid));
                for(String permission:RUNTIME[i]){before.setProperty("grant."+i+"."+permission,Boolean.toString(granted(i,permission)));before.setProperty("flags."+i+"."+permission,Integer.toString(flags(i,permission)));}
            }
            before.setProperty("sms.exemption",Boolean.toString(exemption()));int previous=mode(apps);
            if(previous<0||previous>4)throw new IOException("installation-appop-unobserved");before.setProperty("ipsec.mode",Integer.toString(previous));write(before);
        }
        ApplicationInfo[] apps=installed(before,true);
        if(!exemption()&&!pm.addWhitelistedRestrictedPermission(PACKAGES[2],SMS,PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))throw new IOException("sms-exemption-refused");
        for(int i=0;i<PACKAGES.length;i++)for(String permission:RUNTIME[i])if(!granted(i,permission))pm.grantRuntimePermission(PACKAGES[i],"android.permission."+permission,UserHandle.SYSTEM);
        ops.setMode(IPSEC,apps[0].uid,PACKAGES[0],AppOpsManager.MODE_ALLOWED);
        if(!ready())throw new IOException("installation-preparation-unconfirmed");before.setProperty("phase","PREPARED");write(before);
    }
    public void restore()throws Exception {
        requireLock();
        ModernPhoneIdle.requireIdle(context);requireNoSelectedCarrier();Properties before=read();ApplicationInfo[] apps=installed(before,false);
        if("RESTORED".equals(before.getProperty("phase"))){verifyRestored(before,apps);return;}
        int currentMode=mode(apps),originalMode=Integer.parseInt(before.getProperty("ipsec.mode"));
        if(currentMode!=AppOpsManager.MODE_ALLOWED&&currentMode!=originalMode)throw new IOException("external-ipsec-policy-change-refused");
        if(Boolean.parseBoolean(before.getProperty("sms.exemption"))&&!exemption())throw new IOException("external-sms-policy-change-refused");
        for(int i=0;i<PACKAGES.length;i++)for(String permission:RUNTIME[i])if(Boolean.parseBoolean(before.getProperty("grant."+i+"."+permission))&&!granted(i,permission))throw new IOException("external-permission-revoke-refused");
        before.setProperty("phase","RESTORING");write(before);
        for(int i=0;i<PACKAGES.length;i++)for(String permission:RUNTIME[i]){
            boolean original=Boolean.parseBoolean(before.getProperty("grant."+i+"."+permission));
            // Only revoke grants added by this preparation. Never grant an originally
            // granted permission revoked by an external owner during the trial.
            if(!original&&granted(i,permission))pm.revokeRuntimePermission(PACKAGES[i],"android.permission."+permission,UserHandle.SYSTEM);
        }
        if(!Boolean.parseBoolean(before.getProperty("sms.exemption"))&&exemption()&&!pm.removeWhitelistedRestrictedPermission(PACKAGES[2],SMS,PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM))throw new IOException("sms-exemption-restore-refused");
        ops.setMode(IPSEC,apps[0].uid,PACKAGES[0],Integer.parseInt(before.getProperty("ipsec.mode")));
        verifyRestored(before,installed(before,false));before.setProperty("phase","RESTORED");write(before);
    }
    private void requireNoSelectedCarrier()throws Exception {
        // Caller holds the carrier controller's global lock. Permission recovery
        // must follow carrier recovery for every owner, never disrupt another SIM.
        File root=carrierRoot;canonical(root);String suffix="";
        if(test) {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest((state.getParentFile().getName()+":selection").getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte value:bytes)hash.append(String.format(Locale.ROOT,"%02x",value&255));suffix="-"+hash.substring(0,32);
        }
        if(root.exists()) {
            File[] children=root.listFiles();if(children==null)throw new IOException("carrier-inventory-unavailable");
            for(File child:children)if(child.getName().startsWith("slot-")&&(!test||child.getName().endsWith(suffix))){
                canonical(child);File phase=new File(child,"phase");canonical(phase);
                if(!child.isDirectory()||!phase.isFile()||phase.length()>64||!"RESTORED".equals(new String(Files.readAllBytes(phase.toPath()),"UTF-8")))throw new IOException("carrier-recovery-must-finish-first");
            }
        }
        File owners=new File(test?state.getParentFile().getPath()+"/selection/owners":"/data/adb/codex_vowifi_stack_modern/coordination/owners");canonical(owners);
        File[] rows=owners.exists()?owners.listFiles():new File[0];if(rows==null)throw new IOException("owner-inventory-unavailable");
        for(File child:rows) {
            canonical(child);if(!child.isDirectory()||!child.getName().matches("slot-[0-7]-sub-[0-9]+"))throw new IOException("owner-inventory-refused");
            File record=new File(child,"selection.properties");canonical(record);
            if(!record.exists()) {
                File[] contents=child.listFiles();if(contents!=null&&contents.length==0)continue;
                throw new IOException("owner-recovery-must-finish-first");
            }
            Properties value=new ModernStateFiles(child).read("selection.properties");
            if(!"3".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!child.getName().equals("slot-"+value.getProperty("slot")+"-sub-"+value.getProperty("sub"))||!Arrays.asList("RESTORED","ARCHIVING").contains(value.getProperty("phase"))||!"false".equals(value.getProperty("mode_owned")))throw new IOException("owner-recovery-must-finish-first");
            ModernSelectedPermissions.validateRecord(value);
        }
        File coordination=owners.getParentFile();
        if(new File(coordination,"resources/roles-1.properties").exists()||new File(coordination,"resources/roles-4.properties").exists())new ModernSharedSelectedRoles(context,coordination,carrierLock,test).recoverUnused();
    }
    private void verifyRestored(Properties before,ApplicationInfo[] apps)throws Exception {
        for(int i=0;i<PACKAGES.length;i++)for(String permission:RUNTIME[i])if(granted(i,permission)!=Boolean.parseBoolean(before.getProperty("grant."+i+"."+permission))||flags(i,permission)!=Integer.parseInt(before.getProperty("flags."+i+"."+permission)))throw new IOException("installation-permission-restore-unconfirmed");
        if(exemption()!=Boolean.parseBoolean(before.getProperty("sms.exemption"))||mode(apps)!=Integer.parseInt(before.getProperty("ipsec.mode")))throw new IOException("installation-policy-restore-unconfirmed");
    }
    private void requireLock()throws IOException {if(closed||!lock.isValid())throw new IOException("closed-installation-transaction");carrierLock.requireHeld(carrierRoot);}
    public String phase()throws Exception {requireLock();return file("baseline.properties").exists()?read().getProperty("phase"):"ABSENT";}
    @Override public void close()throws IOException {if(closed)return;carrierLock.requireHeld(carrierRoot);closed=true;try{if(ownsCarrierLock)carrierLock.close();}finally{try{lock.release();}finally{lockFile.close();}}}
}
