// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Carrier layer of a modern controller. Installer, leases and phone reload remain separate. */
public final class ModernProviderTransaction implements AutoCloseable {
    private static final String[] KEYS={"carrier_data_service_wlan_package_override_string","carrier_network_service_wlan_package_override_string","carrier_qualified_networks_service_package_override_string","config_ims_mmtel_package_override_string"};
    private static final String[] VALUES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    private static final int[] BITS={1,1,2,4};
    private final Context context;private final int slot,sub;private final boolean test;
    private final File state;private final Class<?> api;
    private final ModernControllerLock controllerLock;private final boolean ownsLock;private final File lockRoot;private boolean closed;
    public ModernProviderTransaction(Context context,int slot,int sub,File state,boolean emulatorTest)throws Exception {
        this(context,slot,sub,state,emulatorTest,null);
    }
    public ModernProviderTransaction(Context context,int slot,int sub,File state,boolean emulatorTest,ModernControllerLock held)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||slot<0||slot>7||sub<0)
            throw new SecurityException("modern-root-owner-required");
        if(emulatorTest&&!"1".equals(SystemProperties.get("ro.kernel.qemu")))throw new SecurityException("test-emulator-required");
        this.context=context;this.slot=slot;this.sub=sub;this.test=emulatorTest;this.state=state.getAbsoluteFile();
        api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");owner();
        File root=new File(emulatorTest?"/data/local/tmp/codex-modern-persistence-tests":"/data/adb/codex_vowifi_stack_modern/transactions");
        lockRoot=root;
        String expected="slot-"+slot+"-sub-"+sub;
        if(!root.equals(this.state.getParentFile())||!(emulatorTest?this.state.getName().matches(expected+"-[0-9a-f]{32}"):expected.equals(this.state.getName()))||!this.state.equals(this.state.getCanonicalFile()))
            throw new SecurityException("modern-state-owner-refused");
        privateDirectory(root);privateDirectory(this.state);
        ownsLock=held==null;controllerLock=ownsLock?new ModernControllerLock(root,emulatorTest):held;controllerLock.requireHeld(root);
    }
    private static void privateDirectory(File directory)throws IOException {
        if(!directory.getAbsoluteFile().equals(directory.getCanonicalFile())||Files.isSymbolicLink(directory.toPath()))throw new IOException("private-path-refused");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("private-state-unavailable");
        if(!directory.setReadable(false,false)||!directory.setWritable(false,false)||!directory.setExecutable(false,false)||!directory.setReadable(true,true)||!directory.setWritable(true,true)||!directory.setExecutable(true,true))throw new IOException("private-state-mode-unavailable");
    }
    private Object loader()throws Exception {
        IBinder binder=ServiceManager.getService("carrier_config");
        if(binder==null||!binder.isBinderAlive())throw new IOException("carrier-service-unavailable");
        return Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    private SubscriptionInfo owner()throws Exception {
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot&&info.getSubscriptionId()==sub){
            TelephonyManager manager=context.getSystemService(TelephonyManager.class);
            String operator=manager.createForSubscriptionId(sub).getSimOperator();
            if(manager.getSimState(slot)!=TelephonyManager.SIM_STATE_READY||(!test&&!"23415".equals(operator))||(test&&(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))))
                throw new SecurityException("modern-owner-profile-refused");
            return info;
        }
        throw new SecurityException("selected-subscription-changed");
    }
    private OverrideFileStore.Target target()throws Exception {return CarrierOverrideFiles.target(context,owner(),api,loader(),state);}
    private PersistableBundle config()throws Exception {return (PersistableBundle)CarrierConfigReadCompat.read(api,loader(),sub,"android");}
    private File file(String name)throws IOException {
        File result=new File(state,name);
        if(!state.equals(result.getCanonicalFile().getParentFile())||Files.isSymbolicLink(result.toPath())||(result.exists()&&!result.isFile()))throw new IOException("private-file-refused");
        return result;
    }
    private void writeText(String name,String value)throws IOException {
        File temporary=file(name+".new");
        try(FileOutputStream stream=new FileOutputStream(temporary)){stream.write(value.getBytes("UTF-8"));stream.getFD().sync();}
        Files.move(temporary.toPath(),file(name).toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private void phase(String value)throws IOException {writeText("phase",value);}
    private void sameProfile()throws IOException {
        File record=file("profile");
        if(!record.isFile()||record.length()>4096||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(new String(Files.readAllBytes(record.toPath()),"UTF-8")))throw new IOException("controller-build-profile-changed");
    }
    private String phase()throws IOException {
        File current=file("phase");if(!current.isFile()||current.length()>64)throw new IOException("transaction-phase-unavailable");
        return new String(Files.readAllBytes(current.toPath()),"UTF-8");
    }
    private void writeBefore(PersistableBundle before)throws Exception {
        File output=file("config-before.bin");if(output.exists())throw new IOException("snapshot-already-exists");
        Parcel parcel=Parcel.obtain();
        try{parcel.writeTypedObject(before,0);byte[] bytes=parcel.marshall();if(bytes.length>1048576)throw new IOException("snapshot-too-large");try(FileOutputStream stream=new FileOutputStream(output)){stream.write(bytes);stream.getFD().sync();}}
        finally{parcel.recycle();}
    }
    private PersistableBundle before()throws Exception {
        File input=file("config-before.bin");if(!input.isFile()||input.length()>1048576)throw new IOException("snapshot-unavailable");
        byte[] bytes=Files.readAllBytes(input.toPath());Parcel parcel=Parcel.obtain();
        try{parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);return parcel.readTypedObject(PersistableBundle.CREATOR);}
        finally{parcel.recycle();}
    }
    public void snapshot()throws Exception {
        requireLock();
        if(file("phase").exists())throw new IOException("existing-transaction-refused");
        OverrideFileStore.Target target=target();PersistableBundle original=config();
        if(original==null||!original.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL)||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))throw new IOException("clean-loaded-baseline-required");
        // Saving mutates the in-memory persistent bundle with serialization metadata;
        // the loader removes that metadata on read. Snapshot a disk-loaded baseline.
        if(original.containsKey("__carrier_config_package_version__"))throw new IOException("carrier-layer-not-loaded-from-disk");
        for(String key:KEYS)for(String value:VALUES)if(value.equals(original.getString(key)))throw new IOException("untracked-replacement-refused");
        phase("PREPARING");writeText("profile",Build.VERSION.SDK_INT+":"+Build.FINGERPRINT);OverrideFileStore store=CarrierOverrideFiles.store(state);store.snapshot(target);store.verifyRestored(target);writeBefore(original);phase("PREPARED");
    }
    private void override(PersistableBundle value)throws Exception {target();api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader(),sub,value,true);}
    static boolean same(PersistableBundle left,PersistableBundle right){
        if(left==null||right==null||!left.keySet().equals(right.keySet()))return false;
        for(String key:left.keySet())if(left.get(key) instanceof PersistableBundle){if(!(right.get(key) instanceof PersistableBundle)||!same((PersistableBundle)left.get(key),(PersistableBundle)right.get(key)))return false;}
        else if(!Objects.deepEquals(left.get(key),right.get(key)))return false;
        return true;
    }
    public void apply(int mask)throws Exception {
        requireLock();
        if(mask<1||mask>7||!"PREPARED".equals(phase()))throw new IOException("prepared-component-selection-required");
        sameProfile();
        OverrideFileStore.Target target=target();CarrierOverrideFiles.store(state).verifyRestored(target);
        PersistableBundle original=before();
        if(!same(original,config())||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))throw new IOException("baseline-changed");
        PersistableBundle value=new PersistableBundle();for(int i=0;i<KEYS.length;i++)value.putString(KEYS[i],(mask&BITS[i])!=0?VALUES[i]:original.getString(KEYS[i]));
        writeText("components",Integer.toString(mask));phase("APPLYING");override(value);
        long deadline=SystemClock.elapsedRealtime()+15000;
        for(int attempt=0;attempt<20&&SystemClock.elapsedRealtime()<deadline;attempt++){
            try{if(selected(mask)){phase("ACTIVE");return;}}catch(IOException pending){}
            Thread.sleep(500);
        }
        throw new IOException("selected-persistence-unconfirmed");
    }
    private boolean selected(int mask)throws Exception {
        OverrideFileStore.Target target=target();CarrierOverrideFiles.store(state).requireIdentity(target);
        if(!target.file.isFile())return false;
        PersistableBundle actual=config(),disk=ModernCarrierOverrideFiles.readBundle(target.file),original=before();
        if(actual==null||disk==null)return false;
        for(int i=0;i<KEYS.length;i++){
            String expected=(mask&BITS[i])!=0?VALUES[i]:original.getString(KEYS[i]);
            if(!Objects.equals(expected,actual.getString(KEYS[i]))||!Objects.equals(expected,disk.getString(KEYS[i])))return false;
        }
        return true;
    }
    /** Retention checks must match this transaction's recorded component mask. */
    public boolean verifySelection(int mask)throws Exception {
        requireLock();
        if(mask<1||mask>7||!"ACTIVE".equals(phase()))throw new IOException("active-selection-required");
        sameProfile();File record=file("components");
        if(!record.isFile()||record.length()>8||!Integer.toString(mask).equals(new String(Files.readAllBytes(record.toPath()),"UTF-8")))throw new IOException("selected-components-changed");
        return selected(mask);
    }
    public String statePhase()throws IOException {
        requireLock();
        sameProfile();String value=phase();
        if(!Arrays.asList("PREPARING","PREPARED","APPLYING","ACTIVE","CLEARING","FILE_RESTORING","RESTORED_FILE","RESTORED").contains(value))throw new IOException("transaction-phase-unverified");
        return value;
    }
    public void restore()throws Exception {
        requireLock();
        String phase=phase();
        sameProfile();
        OverrideFileStore.Target target=target();OverrideFileStore store=CarrierOverrideFiles.store(state);store.requireIdentity(target);
        if("PREPARED".equals(phase)){
            store.verifyRestored(target);
            if(!same(before(),config())||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))throw new IOException("unused-baseline-changed");
            phase("RESTORED");return;
        }
        if("RESTORED_FILE".equals(phase)||"RESTORED".equals(phase)){store.verifyRestored(target);return;}
        if(!Arrays.asList("APPLYING","ACTIVE","CLEARING","FILE_RESTORING").contains(phase))throw new IOException("restorable-transaction-required");
        if(!"FILE_RESTORING".equals(phase)){
            phase("CLEARING");override(null);
            boolean deleted=false;
            long deadline=SystemClock.elapsedRealtime()+15000;
            for(int attempt=0;attempt<20&&SystemClock.elapsedRealtime()<deadline;attempt++){if(!target.file.exists()&&ModernCarrierBaseline.hasEmptyTransientOverride(slot)){deleted=true;break;}Thread.sleep(500);}
            if(!deleted)throw new IOException("asynchronous-clear-unconfirmed");
            // Commit the handoff before restoring XML. A retry must never clear
            // an already restored original file through the carrier loader.
            phase("FILE_RESTORING");
        }
        // Revalidate live identity immediately before touching its original XML.
        store.requireIdentity(target());store.restore(target);CarrierOverrideFiles.restoreLabel(target);phase("RESTORED_FILE");
    }
    /** Reload this owner's saved carrier layer without killing the shared phone process. */
    public void reloadRestored()throws Exception {
        requireLock();
        if(!"RESTORED_FILE".equals(phase()))throw new IOException("restored-file-required");
        sameProfile();
        OverrideFileStore.Target target=target();CarrierOverrideFiles.store(state).verifyRestored(target);
        if(confirmRestored())return;
        ModernPhoneIdle.requireIdle(context);
        target();
        api.getMethod("updateConfigForPhoneId",int.class,String.class).invoke(loader(),slot,"LOADED");
        long deadline=SystemClock.elapsedRealtime()+15000;
        for(int attempt=0;attempt<20&&SystemClock.elapsedRealtime()<deadline;attempt++){
            if(confirmRestored())return;
            Thread.sleep(500);
        }
        throw new IOException("restored-loader-reload-unconfirmed");
    }
    /** A restored XML alone does not prove the loader consumed the original layer. */
    public boolean confirmRestored()throws Exception {
        requireLock();
        if(!Arrays.asList("RESTORED_FILE","RESTORED").contains(phase()))throw new IOException("restored-file-required");
        sameProfile();
        OverrideFileStore.Target target=target();CarrierOverrideFiles.store(state).verifyRestored(target);
        if(!same(before(),config())||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))return false;
        phase("RESTORED");return true;
    }
    private void requireLock()throws IOException {if(closed)throw new IOException("closed-carrier-transaction");controllerLock.requireHeld(lockRoot);}
    @Override public void close()throws IOException {if(closed)return;controllerLock.requireHeld(lockRoot);closed=true;if(ownsLock)controllerLock.close();}
}
