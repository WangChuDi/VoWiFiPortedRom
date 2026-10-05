// SPDX-License-Identifier: GPL-2.0
import android.os.*;
/** Root-only, fixed slot1/subId1 provider transaction. No subscriber output. */
public final class CarrierTrial {
    private static final String[] KEYS={
        "carrier_data_service_wlan_package_override_string",
        "carrier_network_service_wlan_package_override_string",
        "carrier_qualified_networks_service_package_override_string",
        "config_ims_mmtel_package_override_string"};
    private static final String[] VALUES={"dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
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
            }else throw new IllegalArgumentException("read|apply");
        }finally{data.recycle();reply.recycle();}
    }
}
