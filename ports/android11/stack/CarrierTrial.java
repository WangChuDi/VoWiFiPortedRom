// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import android.app.ActivityThread;
import android.content.Context;
import android.telephony.*;
/** Root-only, fixed slot1/subId1 provider transaction. No subscriber output. */
public final class CarrierTrial {
    private static final String[] KEYS={
        "carrier_data_service_wlan_package_override_string",
        "carrier_network_service_wlan_package_override_string",
        "carrier_qualified_networks_service_package_override_string",
        "config_ims_mmtel_package_override_string"};
    private static final String[] VALUES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
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
        if(args.length==1&&"apply".equals(args[0]))checkProfile();
        IBinder service=ServiceManager.getService("carrier_config");
        if(service==null)throw new IllegalStateException("carrier-service-unavailable");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken("com.android.internal.telephony.ICarrierConfigLoader");
            data.writeInt(1);
            if(args.length==1&&"apply".equals(args[0])){
                PersistableBundle b=new PersistableBundle();
                for(int i=0;i<KEYS.length;i++)b.putString(KEYS[i],VALUES[i]);
                data.writeTypedObject(b,0);data.writeBoolean(true);
                if(!service.transact(3,data,reply,0))throw new IllegalStateException("override-transaction-unavailable");
                reply.readException();System.out.println("provider-override=ACK");
            }else if(args.length==1&&"read".equals(args[0])){
                data.writeString("android");
                if(!service.transact(1,data,reply,0))throw new IllegalStateException("read-transaction-unavailable");
                reply.readException();PersistableBundle b=reply.readTypedObject(PersistableBundle.CREATOR);
                for(String key:KEYS)System.out.println(key+"="+b.getString(key));
            }else throw new IllegalArgumentException("check|read|apply");
        }finally{data.recycle();reply.recycle();}
        System.exit(0);
    }
}
