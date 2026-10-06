// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import org.json.JSONObject;

/** Fixed read-only observation. Never calls resetprop or writes carrier state. */
public final class ModernIwlanModeCheck {
    public static void main(String[] args) {
        JSONObject output=new JSONObject();int code=0;
        try {
            output.put("schema",1);output.put("sdk",Build.VERSION.SDK_INT);
            if(args.length!=0||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("fixed-modern-root-observation-required");
            String property=SystemProperties.get(ModernSharedIwlan.KEY);
            String safe=property.isEmpty()?"empty":property;
            if(!java.util.Arrays.asList("empty","default","legacy","AP-assisted").contains(safe))safe="other";
            output.put("mode_property_present",SystemProperties.class.getMethod("find",String.class).invoke(null,ModernSharedIwlan.KEY)!=null);
            output.put("mode_property",safe);
            ModernIwlanObservation.Result result=ModernIwlanObservation.read(ModernSystemObservation.telephonyDebug(),0);
            output.put("slot",0);output.put("legacy",result.legacy==null?JSONObject.NULL:result.legacy);
            output.put("cached_wlan_transport",result.cachedWlan==null?JSONObject.NULL:result.cachedWlan);
            output.put("transport_manager_observed",result.transportManager);
            output.put("access_networks_manager_observed",result.accessNetworksManager);
            output.put("legacy_field_absence_proves_wlan_available",false);
            output.put("read_only",true);output.put("phone_cache_refresh_verified",false);
            output.put("carrier_registration_verified",false);output.put("call_sms_verified",false);
            output.put("status","observed");
        } catch(Throwable failure) {code=1;try{output.put("status","failed");output.put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(output.toString());System.exit(code);
    }
}
