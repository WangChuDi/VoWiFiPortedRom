// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import android.app.ActivityThread;
import android.content.Context;
import android.telephony.*;
import java.io.*;
/** Root-only, fixed slot1/subId1 provider transaction. No subscriber output. */
public final class CarrierTrial {
    private static final String[] KEYS={
        "carrier_data_service_wlan_package_override_string",
        "carrier_network_service_wlan_package_override_string",
        "carrier_qualified_networks_service_package_override_string",
        "config_ims_mmtel_package_override_string"};
    private static final String[] VALUES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    private static final int[] BITS={1,1,2,4};
    private static final File BASELINE=new File("/data/adb/codex_vowifi_stack/providers-before.bin");
    private static void checkProfile()throws Exception {
        if(Build.VERSION.SDK_INT!=30||!"raphael".equals(Build.DEVICE))throw new IllegalStateException("unsupported-device");
        Looper.prepareMainLooper();
        Context context=ActivityThread.systemMain().getSystemContext();
        Class<?> initializer=Class.forName("android.telephony.TelephonyFrameworkInitializer");
        Class<?> services=Class.forName("android.os.TelephonyServiceManager");
        if(initializer.getMethod("getTelephonyServiceManager").invoke(null)==null)
            initializer.getMethod("setTelephonyServiceManager",services).invoke(null,services.getConstructor().newInstance());
        SubscriptionInfo info=null;
        java.util.List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active!=null)for(SubscriptionInfo candidate:active)if(candidate.getSimSlotIndex()==1){info=candidate;break;}
        if(info==null||info.getSubscriptionId()!=1)throw new IllegalStateException("unsupported-subscription");
        TelephonyManager tm=context.getSystemService(TelephonyManager.class).createForSubscriptionId(1);
        if(tm.getSimState()!=TelephonyManager.SIM_STATE_READY||!"23415".equals(tm.getSimOperator()))throw new IllegalStateException("unsupported-operator");
    }
    public static void main(String[] args) {
        try{run(args);System.exit(0);}
        catch(Throwable error){System.out.println("carrier-operation-failed="+error.getClass().getSimpleName());for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().equals("CarrierTrial")){System.out.println("carrier-error-line="+frame.getLineNumber());break;}System.exit(1);}
    }
    private static void run(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
        if(args.length==1&&"check".equals(args[0])){checkProfile();System.out.println("profile=SUPPORTED");System.exit(0);return;}
        if(args.length>=1&&("apply".equals(args[0])||"snapshot".equals(args[0])||"baseline-check".equals(args[0])))checkProfile();
        IBinder service=ServiceManager.getService("carrier_config");
        if(service==null)throw new IllegalStateException("carrier-service-unavailable");
        Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
        Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
        if(args.length==1&&"baseline-check".equals(args[0])){
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
            System.out.println("provider-restoration=ACK");
        }else if(args.length==1&&"clear".equals(args[0])){
            if(Build.VERSION.SDK_INT!=30||!"raphael".equals(Build.DEVICE)||!new File(BASELINE.getParentFile(),"transaction").isFile())throw new SecurityException("rollback-transaction-required");
            api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,1,null,true);
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
                api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,1,value,true);
                System.out.println("provider-override=ACK");
            }else{
                PersistableBundle actual=config(api,loader);
                for(String key:KEYS)if(!java.util.Objects.equals(value.getString(key),actual.getString(key)))throw new IllegalStateException("provider-verification-failed");
                System.out.println("provider-verification=ACK");
            }
        }else if(args.length==1&&"read".equals(args[0])){
            PersistableBundle value=config(api,loader);
            for(String key:KEYS)System.out.println(key+"="+value.getString(key));
        }else throw new IllegalArgumentException("check|read|snapshot|apply [1..7]");
        System.exit(0);
    }
    private static PersistableBundle config(Class<?> api,Object loader)throws Exception{
        try{return (PersistableBundle)api.getMethod("getConfigForSubId",int.class,String.class).invoke(loader,1,"android");}
        catch(NoSuchMethodException missing){return (PersistableBundle)api.getMethod("getConfigForSubId",int.class,String.class,String.class).invoke(loader,1,"android",null);}
    }
    private static PersistableBundle baseline()throws IOException{
        if(!BASELINE.isFile()||BASELINE.length()>32768)throw new IOException("provider-snapshot-unavailable");
        byte[] bytes=new byte[(int)BASELINE.length()];
        try(DataInputStream stream=new DataInputStream(new FileInputStream(BASELINE))){stream.readFully(bytes);}
        Parcel parcel=Parcel.obtain();
        try{parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);return parcel.readTypedObject(PersistableBundle.CREATOR);}
        finally{parcel.recycle();}
    }
    private static boolean ownedOverrideOnDisk()throws IOException{
        File[] files=new File("/data/user_de/0/com.android.phone/files").listFiles();
        if(files==null)throw new IOException("override-directory-unavailable");
        for(File file:files){
            String name=file.getName();
            if(!name.startsWith("carrierconfig-")||!name.contains("-override-")||!name.endsWith(".xml"))continue;
            if(file.length()>1048576)throw new IOException("override-file-too-large");
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream stream=new FileInputStream(file)){byte[] buffer=new byte[4096];int count;while((count=stream.read(buffer))>=0)output.write(buffer,0,count);}
            String xml=output.toString("UTF-8");
            for(String value:VALUES)if(xml.contains(value))return true;
        }
        return false;
    }
}
