// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Multi-process, emulator-only existing-file and interrupted-copy recovery fixture. */
public final class ModernSeededPersistenceTrial {
    private static final String MARKER="codex_modern_persistence_test_marker_string";
    private static final String[] STAGES={"prepare","apply","restore-file","arm-file-resume","recover-and-reload","cleanup"};
    private static File privateFile(File state,String name)throws IOException {
        File file=new File(state,name);
        if(!state.equals(file.getCanonicalFile().getParentFile())||Files.isSymbolicLink(file.toPath())||(file.exists()&&!file.isFile()))throw new IOException("fixture-file-refused");
        return file;
    }
    private static void saveOriginal(File state,PersistableBundle value)throws Exception {
        File file=privateFile(state,"emulator-original.bin");if(file.exists())throw new IOException("fixture-already-prepared");
        Parcel parcel=Parcel.obtain();try{
            parcel.writeTypedObject(value,0);byte[] bytes=parcel.marshall();if(bytes.length>1048576)throw new IOException("fixture-too-large");
            try(FileOutputStream stream=new FileOutputStream(file)){stream.write(bytes);stream.getFD().sync();}
        }finally{parcel.recycle();}
    }
    private static PersistableBundle original(File state)throws Exception {
        File file=privateFile(state,"emulator-original.bin");if(!file.isFile()||file.length()>1048576)throw new IOException("fixture-original-unavailable");
        byte[] bytes=Files.readAllBytes(file.toPath());Parcel parcel=Parcel.obtain();
        try{parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);return parcel.readTypedObject(PersistableBundle.CREATOR);}finally{parcel.recycle();}
    }
    private static String phase(File state)throws IOException {
        File file=privateFile(state,"phase");if(!file.isFile()||file.length()>64)throw new IOException("fixture-phase-unavailable");
        return new String(Files.readAllBytes(file.toPath()),"UTF-8");
    }
    private static String fixtureIdentity(OverrideFileStore.Target target)throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(target.file.getName().getBytes("UTF-8"));
        StringBuilder hash=new StringBuilder();for(byte value:digest)hash.append(String.format(Locale.ROOT,"%02x",value&255));
        return target.fingerprint+":"+hash+":"+Build.VERSION.SDK_INT+":"+Build.FINGERPRINT;
    }
    private static void recordOwner(File state,OverrideFileStore.Target target)throws Exception {
        File file=privateFile(state,"emulator-owner");if(file.exists())throw new IOException("fixture-owner-exists");
        try(FileOutputStream stream=new FileOutputStream(file)){stream.write(fixtureIdentity(target).getBytes("UTF-8"));stream.getFD().sync();}
    }
    private static void requireOwner(File state,OverrideFileStore.Target target)throws Exception {
        File file=privateFile(state,"emulator-owner");
        if(!file.isFile()||file.length()>8192||!fixtureIdentity(target).equals(new String(Files.readAllBytes(file.toPath()),"UTF-8")))throw new IOException("fixture-owner-changed");
    }
    private static PersistableBundle config(Class<?> api,Object loader,int sub)throws Exception {
        return (PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
    }
    private static void override(Class<?> api,Object loader,int sub,PersistableBundle value,boolean persistent)throws Exception {
        api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,sub,value,persistent);
    }
    private interface Condition {boolean ready()throws Exception;}
    private static void await(Condition condition)throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;
        for(int i=0;i<25&&SystemClock.elapsedRealtime()<end;i++){
            try{if(condition.ready())return;}catch(IOException pending){}
            Thread.sleep(400);
        }
        throw new IOException("fixture-observation-timeout");
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();boolean success=false;
        try{
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||args.length!=2||!Arrays.asList(STAGES).contains(args[0])||!args[1].matches("[0-9a-f]{32}"))throw new SecurityException("explicit-test-emulator-stage-required");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();
            Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer"),manager=Class.forName("android.os.TelephonyServiceManager");
            if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(active==null||active.size()!=1)throw new IOException("single-test-subscription-required");
            SubscriptionInfo selected=active.get(0);int slot=selected.getSimSlotIndex(),sub=selected.getSubscriptionId();
            String operator=context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub).getSimOperator();
            if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-test-owner-required");
            File state=new File("/data/local/tmp/codex-modern-persistence-tests/slot-"+slot+"-sub-"+sub+"-"+args[1]);
            Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
            Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("carrier_config"));
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("stage",args[0]);
            try(ModernProviderTransaction transaction=new ModernProviderTransaction(context,slot,sub,state,true)){
                OverrideFileStore.Target target=CarrierOverrideFiles.target(context,selected,api,loader,state);
                if(!"prepare".equals(args[0]))requireOwner(state,target);
                switch(args[0]){
                case "prepare":{
                    if(target.file.exists()||privateFile(state,"phase").exists()||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))throw new IOException("fresh-fixture-baseline-required");
                    PersistableBundle before=config(api,loader,sub);
                    if(before==null||!before.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL)||before.containsKey(MARKER))throw new IOException("loaded-unmarked-baseline-required");
                    saveOriginal(state,before);recordOwner(state,target);ModernPhoneIdle.requireIdle(context);
                    PersistableBundle seed=new PersistableBundle();seed.putString(MARKER,args[1]);
                    override(api,loader,sub,seed,true);
                    await(()->target.file.isFile()&&args[1].equals(ModernCarrierOverrideFiles.readBundle(target.file).getString(MARKER))&&args[1].equals(config(api,loader,sub).getString(MARKER)));
                    override(api,loader,sub,null,false);
                    await(()->ModernCarrierBaseline.hasEmptyTransientOverride(slot)&&args[1].equals(config(api,loader,sub).getString(MARKER)));
                    result.put("seeded_save_metadata_visible",config(api,loader,sub).containsKey("__carrier_config_package_version__"));
                    // Model an existing disk-loaded original, rather than the RAM
                    // bundle just mutated by saveConfigToXml's version insertion.
                    ModernPhoneIdle.requireIdle(context);
                    api.getMethod("updateConfigForPhoneId",int.class,String.class).invoke(loader,slot,"LOADED");
                    await(()->ModernCarrierBaseline.hasEmptyTransientOverride(slot)&&args[1].equals(config(api,loader,sub).getString(MARKER))&&!config(api,loader,sub).containsKey("__carrier_config_package_version__"));
                    result.put("seeded_disk_layer_loaded",true);
                    transaction.snapshot();result.put("seeded_original_file_snapshotted",true);break;
                }
                case "apply":
                    transaction.apply(2);
                    if(!transaction.verifySelection(2))throw new IOException("selection-unconfirmed");
                    try{transaction.verifySelection(1);throw new IOException("wrong-component-mask-accepted");}
                    catch(IOException expected){if(!"selected-components-changed".equals(expected.getMessage()))throw expected;}
                    result.put("persistent_qns_selected",true).put("recorded_mask_verified",true).put("other_mask_refused",true);break;
                case "restore-file":
                    transaction.restore();
                    if(!target.file.isFile()||!args[1].equals(ModernCarrierOverrideFiles.readBundle(target.file).getString(MARKER))||transaction.confirmRestored())throw new IOException("file-only-restoration-misreported");
                    result.put("original_file_restored",true).put("loader_not_yet_restored",true);break;
                case "arm-file-resume":{
                    if(!"RESTORED_FILE".equals(phase(state)))throw new IOException("copied-original-phase-required");
                    CarrierOverrideFiles.store(state).verifyRestored(target);
                    // Test-only fixture: model the persisted handoff left after an
                    // interruption between original-file copy and phase advancement.
                    java.lang.reflect.Method method=ModernProviderTransaction.class.getDeclaredMethod("phase",String.class);method.setAccessible(true);method.invoke(transaction,"FILE_RESTORING");
                    result.put("simulated_copy_interruption_armed",true);break;
                }
                case "recover-and-reload":
                    if(!"FILE_RESTORING".equals(phase(state)))throw new IOException("interrupted-copy-phase-required");
                    transaction.restore();
                    if(!target.file.isFile()||!args[1].equals(ModernCarrierOverrideFiles.readBundle(target.file).getString(MARKER))||transaction.confirmRestored())throw new IOException("copy-resumption-unconfirmed");
                    result.put("interrupted_copy_resumed",true);
                    transaction.reloadRestored();
                    if(!transaction.confirmRestored())throw new IOException("reloaded-original-unconfirmed");
                    transaction.restore();
                    if(!transaction.confirmRestored())throw new IOException("repeat-recovery-unconfirmed");
                    result.put("interrupted_copy_resumed",true).put("selected_loader_reloaded",true).put("entire_seeded_config_restored",true).put("repeat_restore_safe",true);break;
                case "cleanup":{
                    PersistableBundle before=original(state);ModernPhoneIdle.requireIdle(context);
                    // This outer test baseline was absent, independent of the seeded
                    // original saved by the production carrier transaction.
                    override(api,loader,sub,null,true);
                    await(()->!target.file.exists()&&ModernCarrierBaseline.hasEmptyTransientOverride(slot)&&ModernProviderTransaction.same(before,config(api,loader,sub)));
                    result.put("entire_pristine_config_restored",true).put("selected_override_absent",true);break;
                }
                }
            }
            result.put("carrier_registration_verified",false).put("voice_sms_verified",false).put("dual_sim_verified",false);success=true;
        }catch(Throwable failure){try{result.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success?0:1);
    }
}
