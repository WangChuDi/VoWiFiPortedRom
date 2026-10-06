// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.runtime;
import android.content.*;
import android.content.pm.*;
import android.os.IBinder;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Explicit bindings from a registered privileged app, never a carrier selection. */
public final class ModernServiceBindings {
    private ModernServiceBindings(){}
    public static JSONObject inspect(Context context)throws Exception {
        JSONObject bindings=new JSONObject();
        String[][] services={
            {"iwlan_data","dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan.ModernIwlanDataService","android.telephony.data.DataService","android.permission.BIND_TELEPHONY_DATA_SERVICE","android.telephony.data.IDataService"},
            {"iwlan_network","dev.codex.vowifi.iwlan","dev.codex.vowifi.iwlan.TrialIwlanNetworkService","android.telephony.NetworkService","android.permission.BIND_TELEPHONY_NETWORK_SERVICE","android.telephony.INetworkService"},
            {"qns","dev.codex.vowifi.qns","dev.codex.vowifi.qns.TrialQnsService","android.telephony.data.QualifiedNetworksService","android.permission.BIND_TELEPHONY_DATA_SERVICE","android.telephony.data.IQualifiedNetworksService"},
            {"ims","me.phh.ims","me.phh.ims.PhhImsService","android.telephony.ims.ImsService","android.permission.BIND_IMS_SERVICE","android.telephony.ims.aidl.IImsServiceController"}};
        for(String[] service:services)bindings.put(service[0],bind(context,service));
        return bindings;
    }
    private static JSONObject bind(Context context,String[] profile)throws Exception {
        JSONObject result=new JSONObject();
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch ready=new CountDownLatch(1);
        final IBinder[] connected={null};
        ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder service){connected[0]=service;ready.countDown();}
            public void onServiceDisconnected(ComponentName name){}
            public void onNullBinding(ComponentName name){ready.countDown();}
            public void onBindingDied(ComponentName name){ready.countDown();}
        };
        boolean bound=false;
        try {
            ComponentName component=new ComponentName(profile[1],profile[2]);
            ServiceInfo info=context.getPackageManager().getServiceInfo(component,0);
            if(!info.exported||!profile[4].equals(info.permission))throw new SecurityException("service-binding-declaration");
            boolean action=false;
            for(ResolveInfo match:context.getPackageManager().queryIntentServices(new Intent(profile[3]).setPackage(profile[1]),0))
                if(profile[2].equals(match.serviceInfo.name))action=true;
            if(!action)throw new IllegalStateException("service-action-unresolved");
            if(!profile[1].equals(context.getPackageName())&&context.checkSelfPermission(profile[4])!=PackageManager.PERMISSION_GRANTED){
                result.put("connected",false).put("requires_platform_caller",true);return result;
            }
            bound=context.bindService(new Intent(profile[3]).setComponent(component),Context.BIND_AUTO_CREATE,executor,connection);
            if(!bound||!ready.await(7,TimeUnit.SECONDS)||connected[0]==null)throw new IllegalStateException("service-bind-unobserved");
            boolean descriptor=profile[5].equals(connected[0].getInterfaceDescriptor());
            result.put("connected",connected[0].pingBinder()&&descriptor).put("descriptor_matches",descriptor);
        }catch(Throwable failure){result.put("connected",false).put("error",failure.getClass().getSimpleName());}
        finally {
            if(bound)try{context.unbindService(connection);}catch(Exception ignored){}
            executor.shutdownNow();
        }
        return result;
    }
}
