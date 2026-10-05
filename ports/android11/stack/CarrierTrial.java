// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import android.app.ActivityThread;
import android.content.Context;
import android.telephony.*;
import java.io.*;
/** Root-only selected-slot provider transaction. No subscriber output. */
public final class CarrierTrial {
    private static final String[] KEYS={
        "carrier_data_service_wlan_package_override_string",
        "carrier_network_service_wlan_package_override_string",
        "carrier_qualified_networks_service_package_override_string",
        "config_ims_mmtel_package_override_string"};
    private static final String[] VALUES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    private static final int[] BITS={1,1,2,4};
    private static File BASELINE=new File("/data/adb/codex_vowifi_stack/providers-before.bin");
    private static int selectedSlot=1,selectedSub=1;
    private static Context context;
    private static SubscriptionInfo selectedInfo()throws Exception {
        if(context==null){
            Looper.prepareMainLooper();
            context=ActivityThread.systemMain().getSystemContext();
            Class<?> initializer=Class.forName("android.telephony.TelephonyFrameworkInitializer");
            Class<?> services=Class.forName("android.os.TelephonyServiceManager");
            if(initializer.getMethod("getTelephonyServiceManager").invoke(null)==null)
                initializer.getMethod("setTelephonyServiceManager",services).invoke(null,services.getConstructor().newInstance());
        }
        java.util.List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==selectedSlot&&info.getSubscriptionId()==selectedSub)return info;
        throw new IllegalStateException("selected-subscription-changed");
    }
    private static void checkProfile()throws Exception {
        if(Build.VERSION.SDK_INT!=30||!"raphael".equals(Build.DEVICE))throw new IllegalStateException("unsupported-device");
        selectedInfo();
        TelephonyManager manager=context.getSystemService(TelephonyManager.class);
        TelephonyManager tm=manager.createForSubscriptionId(selectedSub);
        if(manager.getSimState(selectedSlot)!=TelephonyManager.SIM_STATE_READY||!"23415".equals(tm.getSimOperator()))throw new IllegalStateException("unsupported-operator");
    }
    private static void requireOwner()throws Exception {
        File state=BASELINE.getParentFile(),owner=new File(state,"owner");
        if(!new File(state,"transaction").isFile())throw new IllegalStateException("transaction-required");
        String value="1:1"; // Proven fixed identity of pre-owner transactions.
        if(owner.isFile())try(BufferedReader reader=new BufferedReader(new FileReader(owner))){value=reader.readLine();}
        if(!(selectedSlot+":"+selectedSub).equals(value))throw new IllegalStateException("transaction-owner-mismatch");
    }
    public static void main(String[] args) {
        try{run(args);System.exit(0);}
        catch(Throwable error){System.out.println("carrier-operation-failed="+error.getClass().getSimpleName());for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().equals("CarrierTrial")){System.out.println("carrier-error-line="+frame.getLineNumber());break;}System.exit(1);}
    }
    private static void run(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
        if(args.length<1)throw new IllegalArgumentException("command-required");
        boolean masked="apply".equals(args[0])||"verify".equals(args[0])||"persistence-verify".equals(args[0]);
        int prefix=masked?2:1;
        if(args.length==prefix+2){
            selectedSlot=Integer.parseInt(args[prefix]);selectedSub=Integer.parseInt(args[prefix+1]);
            if(selectedSlot<0||selectedSlot>7||selectedSub<0)throw new IllegalArgumentException("invalid-selection");
            args=java.util.Arrays.copyOf(args,prefix);
        }
        String selectedState=System.getenv("CODEX_WFC_STATE");
        if(selectedState!=null){
            File root=new File("/data/adb/codex_vowifi_stack").getCanonicalFile();
            File state=new File(selectedState),expected=new File(root,"transactions/slot-"+selectedSlot+"-sub-"+selectedSub);
            if(!state.getAbsoluteFile().equals(root)&&!state.getAbsoluteFile().equals(expected))throw new SecurityException("state-selection-mismatch");
            if(!state.getAbsoluteFile().equals(state.getCanonicalFile())||java.nio.file.Files.isSymbolicLink(state.toPath()))throw new SecurityException("state-path-refused");
            BASELINE=new File(state,"providers-before.bin");
        }
        if(args.length>=1&&("clear".equals(args[0])||"snapshot".equals(args[0])||"apply".equals(args[0])||"verify".equals(args[0])||"persistence-verify".equals(args[0])||"verify-restored".equals(args[0])||"persistence-adopt".equals(args[0])||"persistence-restore".equals(args[0])))requireOwner();
        if(args.length==1&&"check".equals(args[0]))checkProfile();
        if(args.length>=1&&("apply".equals(args[0])||"snapshot".equals(args[0])||"baseline-check".equals(args[0])))checkProfile();
        IBinder service=ServiceManager.getService("carrier_config");
        if(service==null)throw new IllegalStateException("carrier-service-unavailable");
        Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
        Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
        if(new File(BASELINE.getParentFile(),"persistence.properties").isFile()&&!"persistence-probe".equals(args[0]))
            CarrierOverrideFiles.store(BASELINE.getParentFile()).requireIdentity(CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile()));
        if(args.length==1&&"check".equals(args[0])){
            System.out.println("profile=SUPPORTED");
        }else if(args.length==1&&"persistence-probe".equals(args[0])){
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile());
            System.out.println("persistence-schema=RUNTIME_SELECTED_FILE");
            System.out.println("selected-override-present="+target.file.isFile());
            int others=0;for(File file:target.directory.listFiles())if(file.getName().startsWith("carrierconfig-")&&file.getName().contains("-override-")&&!file.equals(target.file))others++;
            System.out.println("other-override-count="+others);
        }else if(args.length==1&&"persistence-adopt".equals(args[0])){
            checkProfile();PersistableBundle original=baseline();
            for(String key:KEYS)for(String value:VALUES)if(value.equals(original.getString(key)))throw new IllegalStateException("replacement-baseline-refused");
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile());
            if(!OverrideFileStore.containsReplacement(target.file,VALUES))throw new IllegalStateException("persistence-selection-unconfirmed");
            CarrierOverrideFiles.store(BASELINE.getParentFile()).adopt(target,new File(BASELINE.getParentFile(),"baseline"));
            System.out.println("persistence-adoption=ACK");
        }else if(args.length==1&&"persistence-restore".equals(args[0])){
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile());
            CarrierOverrideFiles.store(BASELINE.getParentFile()).restore(target);
            CarrierOverrideFiles.restoreLabel(target);
            System.out.println("persistence-restoration=ACK");
        }else if(args.length==2&&"persistence-verify".equals(args[0])){
            int mask=Integer.parseInt(args[1]);if(mask<1||mask>7)throw new IllegalArgumentException("invalid-components");
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile());
            PersistableBundle disk=CarrierOverrideFiles.readBundle(target.file),before=mask==7?null:baseline();
            for(int i=0;i<KEYS.length;i++)if(!java.util.Objects.equals((mask&BITS[i])!=0?VALUES[i]:before.getString(KEYS[i]),disk.getString(KEYS[i])))throw new IllegalStateException("selected-persistence-unconfirmed");
            System.out.println("persistence-verification=ACK");
        }else if(args.length==1&&"baseline-check".equals(args[0])){
            // App-root may retain an isolated mount namespace. Fail before
            // creating a transaction if the phone's backup directory is hidden.
            if(ownedOverrideOnDisk())throw new IllegalStateException("untracked-replacement-refused");
            PersistableBundle actual=config(api,loader);
            if(actual==null)throw new IllegalStateException("baseline-unavailable");
            for(int i=0;i<KEYS.length;i++)if(VALUES[i].equals(actual.getString(KEYS[i])))throw new IllegalStateException("untracked-replacement-refused");
            System.out.println("provider-baseline=CLEAN");
        }else if(args.length==1&&"verify-restored".equals(args[0])){
            PersistableBundle before=baseline();boolean restored=false;
            for(int attempt=0;attempt<20;attempt++){
                PersistableBundle actual=config(api,loader);boolean matches=actual!=null;
                if(actual!=null)for(String key:KEYS)matches&=java.util.Objects.equals(before.getString(key),actual.getString(key));
                if(matches){restored=true;break;}Thread.sleep(500);
            }
            if(!restored)throw new IllegalStateException("provider-restoration-not-observed");
            if(new File(BASELINE.getParentFile(),"persistence.properties").isFile())CarrierOverrideFiles.store(BASELINE.getParentFile()).verifyRestored(CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile()));
            System.out.println("provider-restoration=ACK");
        }else if(args.length==1&&"clear".equals(args[0])){
            if(Build.VERSION.SDK_INT!=30||!"raphael".equals(Build.DEVICE)||!new File(BASELINE.getParentFile(),"transaction").isFile())throw new SecurityException("rollback-transaction-required");
            // A removed or changed SIM must not redirect recovery to another card.
            selectedInfo();
            api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,selectedSub,null,true);
            boolean cleared=false;
            for(int attempt=0;attempt<20;attempt++){
                Thread.sleep(500);
                PersistableBundle actual=config(api,loader);
                boolean owned=false;
                if(actual!=null)for(String key:KEYS)for(String value:VALUES)owned|=value.equals(actual.getString(key));
                if(actual!=null&&!owned){cleared=true;break;}
            }
            if(!cleared)throw new IllegalStateException("override-clear-not-observed");
            // The loader posts persistence changes. Require its current managed
            // override files to stop containing our provider values before XML
            // restoration; preserve the transaction on an unconfirmed clear.
            for(int attempt=0;attempt<20;attempt++){
                if(!ownedOverrideOnDisk()){System.out.println("provider-clear=ACK");System.exit(0);return;}
                Thread.sleep(500);
            }
            throw new IllegalStateException("override-disk-clear-not-observed");
        }else if(args.length==1&&"snapshot".equals(args[0])){
            if(BASELINE.exists())throw new IllegalStateException("provider-snapshot-exists");
            CarrierOverrideFiles.store(BASELINE.getParentFile()).snapshot(CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile()));
            CarrierOverrideFiles.store(BASELINE.getParentFile()).verifyRestored(CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile()));
            checkProfile();
            PersistableBundle before=config(api,loader),saved=new PersistableBundle();
            for(String key:KEYS)saved.putString(key,before.getString(key));
            Parcel parcel=Parcel.obtain();
            try{
                parcel.writeTypedObject(saved,0);
                byte[] bytes=parcel.marshall();
                try(FileOutputStream stream=new FileOutputStream(BASELINE)){stream.write(bytes);stream.getFD().sync();}
            }finally{parcel.recycle();}
            System.out.println("provider-snapshot=SAVED");
        }else if((args.length==1||args.length==2)&&("apply".equals(args[0])||"verify".equals(args[0]))){
            int mask=args.length==2?Integer.parseInt(args[1]):7;
            if(mask<1||mask>7)throw new IllegalArgumentException("invalid-components");
            PersistableBundle before=mask==7?null:baseline(),value=new PersistableBundle();
            for(int i=0;i<KEYS.length;i++)value.putString(KEYS[i],(mask&BITS[i])!=0?VALUES[i]:before.getString(KEYS[i]));
            if("apply".equals(args[0])){
                api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,selectedSub,value,true);
                System.out.println("provider-override=ACK");
            }else{
                PersistableBundle actual=config(api,loader);
                for(String key:KEYS)if(!java.util.Objects.equals(value.getString(key),actual.getString(key)))throw new IllegalStateException("provider-verification-failed");
                System.out.println("provider-verification=ACK");
            }
        }else if(args.length==1&&"read".equals(args[0])){
            selectedInfo();
            PersistableBundle value=config(api,loader);
            for(String key:KEYS)System.out.println(key+"="+value.getString(key));
        }else throw new IllegalArgumentException("check|read|snapshot|apply [1..7]");
        System.exit(0);
    }
    private static PersistableBundle config(Class<?> api,Object loader)throws Exception{
        try{return (PersistableBundle)api.getMethod("getConfigForSubId",int.class,String.class).invoke(loader,selectedSub,"android");}
        catch(NoSuchMethodException missing){return (PersistableBundle)api.getMethod("getConfigForSubId",int.class,String.class,String.class).invoke(loader,selectedSub,"android",null);}
    }
    private static PersistableBundle baseline()throws IOException{
        if(!BASELINE.isFile()||BASELINE.length()>32768)throw new IOException("provider-snapshot-unavailable");
        byte[] bytes=new byte[(int)BASELINE.length()];
        try(DataInputStream stream=new DataInputStream(new FileInputStream(BASELINE))){stream.readFully(bytes);}
        Parcel parcel=Parcel.obtain();
        try{parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);return parcel.readTypedObject(PersistableBundle.CREATOR);}
        finally{parcel.recycle();}
    }
    private static Context context()throws Exception{selectedInfo();return context;}
    private static boolean ownedOverrideOnDisk()throws Exception{
        IBinder service=ServiceManager.getService("carrier_config");
        if(service==null)throw new IllegalStateException("carrier-service-unavailable");
        Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
        Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
        OverrideFileStore.Target target=CarrierOverrideFiles.target(context(),selectedInfo(),api,loader,BASELINE.getParentFile());
        // Persistence-clear observation concerns this SIM only. Another selected
        // SIM's replacement must not prevent or participate in this rollback.
        return OverrideFileStore.containsReplacement(target.file,VALUES);
    }
}
