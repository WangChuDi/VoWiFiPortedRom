// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.app.ActivityThread;
import android.content.Context;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.telephony.*;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.RegistrationManager;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Read-only UID 2000 feasibility probe. No Shizuku SDK or privilege escalation. */
public final class ShellCapabilityProbe {
    private interface Check{boolean run()throws Exception;}
    private static void probe(JSONObject out,String key,Check check)throws Exception{
        JSONObject entry=new JSONObject();
        try{entry.put("call_succeeded",true).put("value",check.run());}
        catch(Throwable failure){while(failure instanceof InvocationTargetException&&failure.getCause()!=null)failure=failure.getCause();entry.put("call_succeeded",false).put("error",failure.getClass().getSimpleName());}
        out.put(key,entry);
    }
    public static void main(String[] args){
        JSONObject out=new JSONObject();
        try{
            if(android.os.Process.myUid()!=2000)throw new SecurityException("shell-required");
            if(args.length!=1)throw new IllegalArgumentException("slot-required");
            int slot=Integer.parseInt(args[0]);if(slot<0||slot>7)throw new IllegalArgumentException("slot-range");
            out.put("schema",1).put("uid",2000).put("sdk",Build.VERSION.SDK_INT).put("slot",slot).put("read_only",true).put("shizuku_transport_tested",false);
            Looper.prepareMainLooper();Context system=ActivityThread.systemMain().getSystemContext();
            Context context=system.createPackageContext("com.android.shell",0);
            RootDiagnostics.initializeTelephony();
            JSONObject permissions=new JSONObject();
            for(String name:new String[]{"READ_PRIVILEGED_PHONE_STATE","MODIFY_PHONE_STATE","WRITE_APN_SETTINGS","RECEIVE_SMS","BIND_IMS_SERVICE","MANAGE_IPSEC_TUNNELS"})
                permissions.put(name,context.checkPermission("android.permission."+name,android.os.Process.myPid(),2000)==android.content.pm.PackageManager.PERMISSION_GRANTED);
            out.put("permissions",permissions);
            probe(out,"network_enumeration",()->context.getSystemService(ConnectivityManager.class).getAllNetworks()!=null);
            probe(out,"wifi_network_observation",()->{ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return true;}return false;});
            final int[] sub={-1};
            probe(out,"active_subscription_query",()->{List<SubscriptionInfo> list=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();if(list!=null)for(SubscriptionInfo info:list)if(info.getSimSlotIndex()==slot)sub[0]=info.getSubscriptionId();return sub[0]>=0;});
            if(sub[0]>=0){
                probe(out,"carrier_config_read",()->context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sub[0])!=null);
                ImsMmTelManager manager=ImsMmTelManager.createForSubscriptionId(sub[0]);
                probe(out,"wfc_setting_read",()->manager.isVoWiFiSettingEnabled());
                probe(out,"wfc_roaming_read",()->manager.isVoWiFiRoamingSettingEnabled());
                probe(out,"provisioning_read",()->{Class<?> type=Class.forName("android.telephony.ims.ProvisioningManager");Object instance=type.getMethod("createForSubscriptionId",int.class).invoke(null,sub[0]);return Boolean.TRUE.equals(type.getMethod("getProvisioningStatusForCapability",int.class,int.class).invoke(instance,1,1));});
                probe(out,"ims_registration_callback",()->{ExecutorService executor=Executors.newSingleThreadExecutor();CountDownLatch signal=new CountDownLatch(1);RegistrationManager.RegistrationCallback callback=new RegistrationManager.RegistrationCallback(){public void onRegistered(int transport){signal.countDown();}public void onRegistering(int transport){signal.countDown();}public void onUnregistered(android.telephony.ims.ImsReasonInfo reason){signal.countDown();}};boolean registered=false;try{manager.registerImsRegistrationCallback(executor,callback);registered=true;return signal.await(4,TimeUnit.SECONDS);}finally{if(registered)manager.unregisterImsRegistrationCallback(callback);executor.shutdownNow();}});
                probe(out,"native_ims_sms_query",()->{Class<?> type=Class.forName("com.android.internal.telephony.ISms");Object service=Class.forName(type.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("isms"));return Boolean.TRUE.equals(type.getMethod("isImsSmsSupportedForSubscriber",int.class).invoke(service,sub[0]));});
                probe(out,"preferred_apn_read",()->{try(Cursor c=context.getContentResolver().query(Uri.parse("content://telephony/carriers/preferapn/subId/"+sub[0]),new String[]{"_id"},null,null,null)){return c!=null;}});
            }
            probe(out,"magisk_module_file_read",()->{try(InputStream stream=new FileInputStream("/data/adb/modules/codex_vowifi_stack_api30/module.prop")){return stream.read()>=0;}});
            out.put("system_partition_write_access_hint",new File("/system/priv-app").canWrite());
            out.put("module_directory_write_access_hint",new File("/data/adb/modules").canWrite());
            out.put("mutation_tested",false).put("status","completed");
        }catch(Throwable failure){try{out.put("status","failed").put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(out.toString());System.exit(0);
    }
}
