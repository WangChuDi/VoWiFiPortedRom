// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Coordinates a selected owner's providers, component gate and shared IWLAN mode. */
public final class ModernSelectionTransaction implements AutoCloseable {
    static final long LEASE_MILLIS=90000,TRIAL_MILLIS=300000;
    private static final String RECORD="selection.properties";
    private static final String[] LEASE_FIELDS={"sub","boot","until"};
    private final Context context;private final int slot,sub;private final boolean test;
    private final File module,installation,coordination,carrierState,lockRoot;
    private final ModernControllerLock held;private final ModernStateFiles files;
    private final String prefix;private final boolean ownsLock;private boolean closed;
    public ModernSelectionTransaction(Context context,int slot,int sub,File module,File installation,File coordination,File carrierState,boolean test)throws Exception {
        this(context,slot,sub,module,installation,coordination,carrierState,test,null);
    }
    ModernSelectionTransaction(Context context,int slot,int sub,File module,File installation,File coordination,File carrierState,boolean test,ModernControllerLock borrowed)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||slot<0||slot>7||sub<0)throw new SecurityException("modern-root-owner-required");
        this.context=context;this.slot=slot;this.sub=sub;this.test=test;
        this.module=module.getAbsoluteFile();this.installation=installation.getAbsoluteFile();this.coordination=coordination.getAbsoluteFile();this.carrierState=carrierState.getAbsoluteFile();
        lockRoot=new File(test?"/data/local/tmp/codex-modern-persistence-tests":"/data/adb/codex_vowifi_stack_modern/transactions");
        String owner="slot-"+slot+"-sub-"+sub;
        if(test) {
            if(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name"))||
                !this.coordination.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/selection")||
                !this.module.equals(new File(this.coordination.getParentFile(),"module"))||!this.installation.equals(new File(this.coordination.getParentFile(),"state"))||
                !this.carrierState.getName().matches(owner+"-[0-9a-f]{32}"))throw new SecurityException("fixed-selection-fixture-required");
        } else if(!this.coordination.equals(new File("/data/adb/codex_vowifi_stack_modern/coordination"))||!this.module.equals(new File("/data/adb/modules/codex_vowifi_stack_modern"))||
            !this.installation.equals(new File("/data/adb/codex_vowifi_stack_modern/installation"))||!owner.equals(this.carrierState.getName()))throw new SecurityException("fixed-selection-path-required");
        if(!lockRoot.equals(this.carrierState.getParentFile()))throw new SecurityException("fixed-selection-carrier-root-required");
        for(File path:Arrays.asList(this.module,this.installation,this.coordination,this.carrierState))ModernStateFiles.canonical(path);
        owner();ownsLock=borrowed==null;held=ownsLock?new ModernControllerLock(lockRoot,test):borrowed;held.requireHeld(lockRoot);ModernStateFiles state;
        try {state=new ModernStateFiles(new File(this.coordination,"owners/"+owner));}
        catch(Exception error){if(ownsLock)held.close();throw error;}
        files=state;prefix="codex_wfc_stack_slot_"+slot+"_";
    }
    private void requireLock()throws Exception {if(closed)throw new IOException("closed-selection-transaction");held.requireHeld(lockRoot);owner();}
    private void owner()throws Exception {
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();TelephonyManager phone=context.getSystemService(TelephonyManager.class);
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot&&info.getSubscriptionId()==sub) {
            String operator=phone.createForSubscriptionId(sub).getSimOperator();
            if(phone.getSimState(slot)!=TelephonyManager.SIM_STATE_READY||operator==null||(!test&&!"23415".equals(operator))||(test&&(!operator.matches("[0-9]{5,6}")||"23415".equals(operator))))throw new SecurityException("modern-owner-profile-refused");return;
        }
        throw new SecurityException("selected-subscription-changed");
    }
    private ModernProviderTransaction carrier()throws Exception {return new ModernProviderTransaction(context,slot,sub,carrierState,test,held);}
    private ModernSharedIwlan mode()throws Exception {return new ModernSharedIwlan(context,new File(coordination,"resources"),held,test);}
    private ModernSharedSelectedRoles roles()throws Exception {return new ModernSharedSelectedRoles(context,coordination,held,test);}
    private Properties record()throws Exception {
        requireLock();Properties value=files.read(RECORD);
        if(!"3".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!Integer.toString(slot).equals(value.getProperty("slot"))||!Integer.toString(sub).equals(value.getProperty("sub"))||
            !value.getProperty("token","").matches("[0-9a-f]{32}")||!value.getProperty("mask","").matches("[1-7]")||
            !Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(value.getProperty("phase"))||
            !Arrays.asList("true","false").contains(value.getProperty("persistent"))||!Arrays.asList("true","false").contains(value.getProperty("mode_owned")))throw new IOException("selection-record-refused");
        for(String field:LEASE_FIELDS)if(!Arrays.asList("true","false").contains(value.getProperty("before."+field+".present"))||value.getProperty("before."+field+".value")==null||value.getProperty("before."+field+".value").length()>256)throw new IOException("selection-lease-baseline-refused");
        if(Integer.parseInt(value.getProperty("boot","-1"))<0||Long.parseLong(value.getProperty("trial.until","0"))<=0)throw new IOException("selection-deadline-refused");
        ModernSelectedPermissions.validateRecord(value);return value;
    }
    private void phase(Properties value,String phase)throws Exception {value.setProperty("phase",phase);files.write(RECORD,value);}
    private void token(Properties value,String token)throws Exception {if(token==null||!token.equals(value.getProperty("token")))throw new SecurityException("stale-selection-token-refused");}
    private int mask(Properties value){return Integer.parseInt(value.getProperty("mask"));}
    private String get(String field)throws Exception {return ModernRootSettings.get(prefix+field);}
    private void put(String field,String value)throws Exception {ModernRootSettings.put(prefix+field,value);}
    private String before(Properties value,String field){return Boolean.parseBoolean(value.getProperty("before."+field+".present"))?value.getProperty("before."+field+".value"):null;}
    private static long number(String value,long fallback){try{return Long.parseLong(value);}catch(Exception invalid){return fallback;}}
    private void baselineLease(Properties value)throws Exception {
        if(number(get("boot"),-1)==ModernPhoneRefresh.boot(context)&&number(get("until"),0)>SystemClock.elapsedRealtime())throw new IOException("untracked-active-slot-lease-refused");
        if(slot==1&&sub==1&&number(ModernRootSettings.get("codex_wfc_stack_trial_boot"),-1)==ModernPhoneRefresh.boot(context)&&number(ModernRootSettings.get("codex_wfc_stack_trial_until"),0)>SystemClock.elapsedRealtime())throw new IOException("untracked-legacy-lease-refused");
        for(String field:LEASE_FIELDS){String old=get(field);if(old!=null&&old.length()>256)throw new IOException("lease-value-too-large");value.setProperty("before."+field+".present",Boolean.toString(old!=null));value.setProperty("before."+field+".value",old==null?"":old);}
    }
    private void checkLease(Properties value,boolean allowOriginal)throws Exception {
        for(String field:LEASE_FIELDS) {
            String actual=get(field),wanted=value.getProperty("intent."+field),previous=value.getProperty("previous."+field);
            boolean matches=Objects.equals(actual,wanted)||(allowOriginal&&Objects.equals(actual,before(value,field)));
            if(allowOriginal&&(Objects.equals(actual,previous)&&previous!=null||("until".equals(field)&&"0".equals(actual))))matches=true;
            if(!matches)throw new IOException("external-slot-lease-change-refused");
        }
    }
    private void publishLease(Properties value,boolean renew)throws Exception {
        if("PUBLISHING".equals(value.getProperty("lease.phase")))completeLease(value);
        if(renew)checkLease(value,false);else checkLease(value,true);
        for(String field:LEASE_FIELDS){String old=value.getProperty("intent."+field);if(old!=null)value.setProperty("previous."+field,old);}
        value.setProperty("intent.sub",Integer.toString(sub));value.setProperty("intent.boot",Integer.toString(ModernPhoneRefresh.boot(context)));
        value.setProperty("intent.until",Long.toString(SystemClock.elapsedRealtime()+LEASE_MILLIS));value.setProperty("lease.phase","PUBLISHING");files.write(RECORD,value);
        completeLease(value);
    }
    private void completeLease(Properties value)throws Exception {
        checkLease(value,true);
        put("until","0");put("sub",value.getProperty("intent.sub"));put("boot",value.getProperty("intent.boot"));put("until",value.getProperty("intent.until"));checkLease(value,false);
        value.setProperty("lease.phase","PUBLISHED");files.write(RECORD,value);
    }
    private void restoreLease(Properties value)throws Exception {
        checkLease(value,true);put("until","0");put("sub",before(value,"sub"));put("boot",before(value,"boot"));put("until",before(value,"until"));
        for(String field:LEASE_FIELDS)if(!Objects.equals(get(field),before(value,field)))throw new IOException("original-slot-lease-restore-unconfirmed");
    }
    private void installed(int components)throws Exception {
        if(new File(module,"disable").exists()||new File(module,"remove").exists())throw new IOException("enabled-selection-module-required");
        try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,test,held)) {
            if(!"PREPARED".equals(transaction.phase())||!transaction.ready(components))throw new IOException("prepared-installation-required");
        }
    }
    private boolean otherIwlanOwner()throws Exception {
        File owners=files.root.getParentFile();File[] children=owners.listFiles();if(children==null)throw new IOException("selection-inventory-unavailable");
        boolean other=false;
        for(File child:children) {
            ModernStateFiles.canonical(child);if(!child.isDirectory()||!child.getName().matches("slot-[0-7]-sub-[0-9]+"))throw new IOException("selection-inventory-refused");
            if(child.equals(files.root))continue;
            ModernStateFiles state=new ModernStateFiles(child);
            if(!state.file(RECORD).exists()) {
                String suffix=carrierState.getName().substring(("slot-"+slot+"-sub-"+sub).length());File core=new File(lockRoot,child.getName()+suffix);ModernStateFiles.canonical(core);File[] content=child.listFiles();
                if(content!=null&&content.length==0&&!core.exists())continue;throw new IOException("other-owner-record-unavailable");
            }
            Properties value=state.read(RECORD);
            if(!"3".equals(value.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(value.getProperty("build"))||!value.getProperty("mask","").matches("[1-7]")||!Arrays.asList("true","false").contains(value.getProperty("mode_owned"))||
                !value.getProperty("slot","").matches("[0-7]")||!value.getProperty("sub","").matches("[0-9]+")||!child.getName().equals("slot-"+value.getProperty("slot")+"-sub-"+value.getProperty("sub"))||
                !Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(value.getProperty("phase"))||
                (Boolean.parseBoolean(value.getProperty("mode_owned"))&&(Integer.parseInt(value.getProperty("mask"))&1)==0))throw new IOException("other-owner-record-refused");
            if(!Arrays.asList("RESTORED","ARCHIVING").contains(value.getProperty("phase"))) {
                if(Integer.toString(slot).equals(value.getProperty("slot")))throw new IOException("same-slot-owner-recovery-required");
                if(Boolean.parseBoolean(value.getProperty("mode_owned")))other=true;
            }
        }
        return other;
    }
    private void companion(int mask)throws Exception {
        File bridge=new File("/data/adb/modules/codex_vowifi_sms");ModernStateFiles.canonical(bridge);
        if((mask&4)!=0&&bridge.isDirectory()&&!new File(bridge,"disable").exists()&&!new File(bridge,"remove").exists())throw new IOException("active-companion-must-be-restored-first");
    }
    private void archive(Properties value)throws Exception {
        if(!Arrays.asList("RESTORED","ARCHIVING").contains(value.getProperty("phase")))throw new IOException("restored-selection-required");
        for(String field:LEASE_FIELDS)if(!Objects.equals(get(field),before(value,field)))throw new IOException("original-slot-lease-changed-before-archive");
        ModernStateFiles history=new ModernStateFiles(new File(coordination,"history/slot-"+slot+"-sub-"+sub+"-"+value.getProperty("token")));
        File destination=new File(history.root,"carrier");ModernStateFiles.canonical(destination);
        if(carrierState.exists()) {
            if(destination.exists())throw new IOException("selection-archive-conflict");
            try(ModernProviderTransaction transaction=carrier()){if(!"RESTORED".equals(transaction.statePhase())||!transaction.confirmRestored())throw new IOException("original-carrier-required-before-archive");}
            phase(value,"ARCHIVING");Files.move(carrierState.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
        } else if(!destination.isDirectory()||!"ARCHIVING".equals(value.getProperty("phase")))throw new IOException("selection-archive-carrier-unavailable");
        File archivedPhase=new File(destination,"phase");ModernStateFiles.canonical(archivedPhase);
        if(!archivedPhase.isFile()||archivedPhase.length()>64||!"RESTORED".equals(new String(Files.readAllBytes(archivedPhase.toPath()),"UTF-8")))throw new IOException("selection-archive-phase-refused");
        history.write(RECORD,value);Files.delete(files.file(RECORD).toPath());
    }
    public String trial(int components)throws Exception {
        return trial(components,null);
    }
    String trialForEmulator(int components,ModernProviderTransaction.SnapshotObserver observer)throws Exception {
        if(!test||observer==null)throw new SecurityException("owned-snapshot-fixture-required");
        return trial(components,observer);
    }
    private String trial(int components,ModernProviderTransaction.SnapshotObserver observer)throws Exception {
        requireLock();if(components<1||components>7)throw new IllegalArgumentException("fixed-components-required");ModernPhoneIdle.requireIdle(context);installed(components);companion(components);otherIwlanOwner();
        if(files.file(RECORD).exists())archive(record());
        if(carrierState.exists())throw new IOException("untracked-carrier-state-recovery-required");
        Properties value=new Properties();value.setProperty("schema","3");value.setProperty("build",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);value.setProperty("slot",Integer.toString(slot));value.setProperty("sub",Integer.toString(sub));
        value.setProperty("token",UUID.randomUUID().toString().replace("-",""));value.setProperty("mask",Integer.toString(components));value.setProperty("persistent","false");value.setProperty("mode_owned","false");
        value.setProperty("boot",Integer.toString(ModernPhoneRefresh.boot(context)));value.setProperty("trial.until",Long.toString(SystemClock.elapsedRealtime()+TRIAL_MILLIS));baselineLease(value);roles().snapshot(value);phase(value,"PREPARING");
        try {
            try(ModernProviderTransaction transaction=carrier()){if(observer==null)transaction.snapshot();else transaction.snapshotForEmulator(observer);}phase(value,"PREPARED");
            if((components&1)!=0){value.setProperty("mode_owned","true");files.write(RECORD,value);mode().acquire(slot);owner();}
            phase(value,"SELECTING");publishLease(value,false);
            roles().requested(value);files.write(RECORD,value);
            try(ModernProviderTransaction transaction=carrier()){transaction.apply(components);if(!transaction.verifySelection(components))throw new IOException("selected-providers-unconfirmed");}
            roles().observed(value);
            phase(value,"ACTIVE");return value.getProperty("token");
        } catch(Exception failure) {
            try{restore(value.getProperty("token"));}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;
        }
    }
    public boolean verify(String token)throws Exception {
        Properties value=record();token(value,token);if(!"ACTIVE".equals(value.getProperty("phase")))throw new IOException("active-selection-required");roles().verifyOwner(value);installed(mask(value));checkLease(value,false);
        if(number(get("boot"),-1)!=ModernPhoneRefresh.boot(context)||number(get("until"),0)<=SystemClock.elapsedRealtime())return false;
        try(ModernProviderTransaction transaction=carrier()){return transaction.verifySelection(mask(value));}
    }
    public void retain(String token)throws Exception {
        Properties value=record();token(value,token);if(!verify(token))throw new IOException("active-lease-required-before-retain");
        if(!Boolean.parseBoolean(value.getProperty("persistent"))&&(ModernPhoneRefresh.boot(context)!=Integer.parseInt(value.getProperty("boot"))||SystemClock.elapsedRealtime()>=Long.parseLong(value.getProperty("trial.until"))))throw new IOException("expired-trial-cannot-be-retained");
        value.setProperty("persistent","true");files.write(RECORD,value);publishLease(value,true);
    }
    public boolean renew(String token)throws Exception {
        Properties value=record();token(value,token);if(!"ACTIVE".equals(value.getProperty("phase")))throw new IOException("active-selection-required");
        if(!Boolean.parseBoolean(value.getProperty("persistent"))&&(ModernPhoneRefresh.boot(context)!=Integer.parseInt(value.getProperty("boot"))||SystemClock.elapsedRealtime()>=Long.parseLong(value.getProperty("trial.until")))){restore(token);return false;}
        if("PUBLISHING".equals(value.getProperty("lease.phase")))completeLease(value);checkLease(value,false);roles().maintain(value);installed(mask(value));try(ModernProviderTransaction transaction=carrier()){if(!transaction.verifySelection(mask(value)))throw new IOException("selected-providers-changed");}
        if(Boolean.parseBoolean(value.getProperty("mode_owned")))mode().acquire(slot);publishLease(value,true);return true;
    }
    public void restore(String token)throws Exception {
        Properties value=record();token(value,token);ModernPhoneIdle.requireIdle(context);
        if("ARCHIVING".equals(value.getProperty("phase")))throw new IOException("selection-archive-must-finish-first");
        boolean restored="RESTORED".equals(value.getProperty("phase"));
        if(restored) {
            for(String field:LEASE_FIELDS)if(!Objects.equals(get(field),before(value,field)))throw new IOException("original-slot-lease-changed");
        } else {checkLease(value,true);phase(value,"RESTORING");restoreLease(value);}
        try(ModernProviderTransaction transaction=carrier()) {
            transaction.restore();if("RESTORED_FILE".equals(transaction.statePhase()))transaction.reloadRestored();
            if(!transaction.confirmRestored())throw new IOException("original-carrier-restore-unconfirmed");
        }
        boolean originalInstallation=false;
        if(restored)try(ModernInstallationTransaction transaction=new ModernInstallationTransaction(context,module,installation,test,held)) {
            if("RESTORED".equals(transaction.phase())){transaction.restore();originalInstallation=true;}
        }
        // Carrier/file/lease recovery can still complete if a disabled module's
        // privileged app is absent. Its permission journal remains pending.
        if(!originalInstallation)roles().release(value);
        if(Boolean.parseBoolean(value.getProperty("mode_owned"))) {
            boolean other=otherIwlanOwner();ModernSharedIwlan shared=mode();shared.release(other);
            if(!other)shared.finishCycle(new File(new ModernStateFiles(new File(coordination,"mode-history")).root,"mode-"+value.getProperty("token")+".properties"));
            value.setProperty("mode_owned","false");
        }
        phase(value,"RESTORED");
    }
    public Properties status()throws Exception {Properties value=record();Properties safe=new Properties();for(String name:Arrays.asList("phase","mask","persistent","mode_owned"))safe.setProperty(name,value.getProperty(name));return safe;}
    String currentToken()throws Exception {return record().getProperty("token");}
    void confirmRestoredOwner()throws Exception {
        Properties value=record();if(!"RESTORED".equals(value.getProperty("phase"))||Boolean.parseBoolean(value.getProperty("mode_owned")))throw new IOException("restored-selection-required");
        for(String field:LEASE_FIELDS)if(!Objects.equals(get(field),before(value,field)))throw new IOException("original-slot-lease-changed");
        if(!originalCarrierRestored())throw new IOException("original-carrier-restore-unconfirmed");
    }
    public boolean originalCarrierRestored()throws Exception {
        requireLock();try(ModernProviderTransaction transaction=carrier()) {
            return Arrays.asList("RESTORED_FILE","RESTORED").contains(transaction.statePhase())&&transaction.confirmRestored();
        }
    }
    void finishArchive(String token)throws Exception {Properties value=record();token(value,token);archive(value);}
    @Override public void close()throws Exception {if(closed)return;held.requireHeld(lockRoot);closed=true;if(ownsLock)held.close();}
}
