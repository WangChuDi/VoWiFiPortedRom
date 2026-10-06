// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.telephony.SubscriptionManager;
import dev.codex.vowifi.tool.RuntimeAbiProbe;
import org.json.JSONObject;
import java.util.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Integration observations only; no carrier overrides, radio requests, calls or SMS. */
public final class ModernRuntimeCheck {
    private static String error(Throwable t){
        while(t instanceof java.lang.reflect.InvocationTargetException&&t.getCause()!=null)t=t.getCause();
        return t.getClass().getSimpleName();
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();
        try {
            if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
            if(args.length!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)
                throw new IllegalStateException("runtime-profile-refused");
            Looper.prepareMainLooper();
            Context context=ActivityThread.systemMain().getSystemContext();
            Class<?> initializer=Class.forName("android.telephony.TelephonyFrameworkInitializer");
            Class<?> manager=Class.forName("android.os.TelephonyServiceManager");
            if(initializer.getMethod("getTelephonyServiceManager").invoke(null)==null)
                initializer.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT);
            result.put("root_abi",new JSONObject(RuntimeAbiProbe.inspect(Build.VERSION.SDK_INT,ModernRuntimeCheck.class.getClassLoader())));
            PackageManager pm=context.getPackageManager();
            JSONObject packages=new JSONObject();
            String[][] profiles={
                {"dev.codex.vowifi.iwlan","READ_PHONE_STATE","READ_PRIVILEGED_PHONE_STATE","CONNECTIVITY_USE_RESTRICTED_NETWORKS","FOREGROUND_SERVICE","BIND_IMS_SERVICE"},
                {"dev.codex.vowifi.qns","READ_PHONE_STATE","READ_PRIVILEGED_PHONE_STATE"},
                {"me.phh.ims","READ_PHONE_STATE","READ_PRIVILEGED_PHONE_STATE","CONNECTIVITY_USE_RESTRICTED_NETWORKS","MODIFY_PHONE_STATE","RECORD_AUDIO","SEND_SMS"}};
            for(String[] profile:profiles){
                JSONObject item=new JSONObject();
                try {
                    ApplicationInfo app=pm.getApplicationInfo(profile[0],0);
                    item.put("installed",true).put("system_app",(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0);
                    item.put("privileged_app",(app.privateFlags&ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)!=0);
                    JSONObject permissions=new JSONObject();
                    for(int i=1;i<profile.length;i++)permissions.put(profile[i],
                        pm.checkPermission("android.permission."+profile[i],profile[0])==PackageManager.PERMISSION_GRANTED);
                    item.put("permissions",permissions);
                }catch(Throwable failure){item.put("error",error(failure));}
                packages.put(profile[0],item);
            }
            result.put("packages",packages);
            try {result.put("iwlan_service_abi",snapshot("abi","service_app_declared_library_lookup"));}
            catch(Throwable failure){result.put("iwlan_service_abi_error",error(failure));}
            try {result.put("privileged_explicit_bindings",snapshot("bindings","privileged_app_explicit_binding").getJSONObject("bindings"));}
            catch(Throwable failure){result.put("privileged_bindings_error",error(failure));}
            try {result.put("privileged_nonroot_guard",snapshot("guard","privileged_nonroot_guard_check"));}
            catch(Throwable failure){result.put("privileged_nonroot_guard_error",error(failure));}
            try {
                java.util.List<?> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
                result.put("active_subscriptions",active==null?0:active.size());
            }catch(Throwable failure){result.put("subscriptions_error",error(failure));}
            try {
                IBinder binder=ServiceManager.getService("carrier_config");
                if(binder==null)throw new IllegalStateException("carrier-service-unavailable");
                Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
                Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
                int sub=SubscriptionManager.getDefaultSubscriptionId();
                Object config=CarrierConfigReadCompat.read(api,loader,sub,"android");
                result.put("carrier_binder_read",config instanceof PersistableBundle);
            }catch(Throwable failure){result.put("carrier_binder_read_error",error(failure));}
            result.put("telephony_provider_selection_verified",false);
            result.put("carrier_registration_verified",false).put("call_sms_verified",false).put("dual_sim_verified",false);
        }catch(Throwable failure){try{result.put("error",error(failure));}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(result.has("error")?1:0);
    }
    private static JSONObject snapshot(String method,String scope)throws Exception {
        String nonce=UUID.randomUUID().toString().replace("-","");
        long start=SystemClock.elapsedRealtime();
        java.lang.Process process=new ProcessBuilder("content","call","--uri","content://dev.codex.vowifi.iwlan.runtime",
            "--method",method,"--arg",nonce).redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        final boolean[] overflow={false};
        Thread reader=new Thread(()->{
            try(InputStream in=process.getInputStream()){
                byte[] buffer=new byte[1024];int size;
                while((size=in.read(buffer))>=0){
                    if(output.size()+size>32768){overflow[0]=true;break;}
                    output.write(buffer,0,size);
                }
            }catch(IOException ignored){}
        });reader.setDaemon(true);reader.start();
        if(!process.waitFor(40,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("runtime-timeout");}
        reader.join(1000);
        if(reader.isAlive()||overflow[0]||process.exitValue()!=0)throw new IOException("runtime-command-unavailable");
        Matcher match=Pattern.compile("snapshot=(\\{[^\\r\\n]*\\})\\}\\]").matcher(output.toString("UTF-8"));
        if(!match.find())throw new IOException("runtime-snapshot-unavailable");
        JSONObject snapshot=new JSONObject(match.group(1));
        long now=SystemClock.elapsedRealtime(),sample=snapshot.getLong("sample_elapsed");
        if(snapshot.getInt("schema")!=1||snapshot.getInt("sdk")!=Build.VERSION.SDK_INT||
            !"dev.codex.vowifi.iwlan".equals(snapshot.getString("package"))||!nonce.equals(snapshot.getString("nonce"))||
            snapshot.getInt("pid")<=0||!scope.equals(snapshot.getString("scope"))||sample<start||sample>now||now-sample>5000)
            throw new IOException("runtime-snapshot-invalid");
        return snapshot;
    }
}
