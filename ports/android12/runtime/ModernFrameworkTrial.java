// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Transient QNS framework binding test, restricted to a non-VOXI modern emulator. */
public final class ModernFrameworkTrial {
    private static final String KEY="carrier_qualified_networks_service_package_override_string";
    private static final String PACKAGE="dev.codex.vowifi.qns";
    private static boolean same(PersistableBundle a,PersistableBundle b){
        if(a==null||b==null||!a.keySet().equals(b.keySet()))return false;
        for(String key:a.keySet()){
            Object left=a.get(key),right=b.get(key);
            if(left instanceof PersistableBundle){if(!(right instanceof PersistableBundle)||!same((PersistableBundle)left,(PersistableBundle)right))return false;}
            else if(!Objects.deepEquals(left,right))return false;
        }
        return true;
    }
    private static boolean frameworkBinding()throws Exception {
        java.lang.Process process=new ProcessBuilder("dumpsys","activity","services",PACKAGE).redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=process.getInputStream()){
            byte[] bytes=new byte[1024];int length;
            while((length=in.read(bytes))>=0){if(output.size()+length>32768)break;output.write(bytes,0,length);}
        }catch(IOException ignored){}});reader.setDaemon(true);reader.start();
        if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("service-observation-timeout");}
        reader.join(1000);
        if(reader.isAlive()||process.exitValue()!=0)throw new IOException("service-observation-incomplete");
        String text=output.toString("UTF-8");
        // Current service record, a received binding and the platform phone client.
        return text.contains(PACKAGE+"/.TrialQnsService")&&text.contains("received=true")&&
            text.contains("hasBound=true")&&text.contains(":com.android.phone/1001}");
    }
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||
            !"1".equals(SystemProperties.get("ro.kernel.qemu")))throw new SecurityException("test-emulator-required");
        Looper.prepareMainLooper();
        Context context=ActivityThread.systemMain().getSystemContext();
        Class<?> init=Class.forName("android.telephony.TelephonyFrameworkInitializer");
        Class<?> manager=Class.forName("android.os.TelephonyServiceManager");
        if(init.getMethod("getTelephonyServiceManager").invoke(null)==null)
            init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active==null||active.size()!=1)throw new IllegalStateException("one-test-subscription-required");
        int sub=active.get(0).getSubscriptionId();
        String operator=context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub).getSimOperator();
        if(operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new SecurityException("non-VOXI-test-SIM-required");
        File directory=new File("/data/user_de/0/com.android.phone/files");
        File[] files=directory.listFiles();
        if(files==null)throw new IOException("override-directory-unavailable");
        for(File file:files)if(file.getName().startsWith("carrierconfig-")&&file.getName().contains("-override-"))
            throw new IllegalStateException("existing-carrier-override-refused");
        Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
        IBinder binder=ServiceManager.getService("carrier_config");
        if(binder==null)throw new IllegalStateException("carrier-service-unavailable");
        Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        PersistableBundle before=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
        if(before==null||PACKAGE.equals(before.getString(KEY)))throw new IllegalStateException("clean-test-baseline-required");
        JSONObject result=new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("persistent",false);
        boolean attempted=false;
        try {
            attempted=true;
            PersistableBundle trial=new PersistableBundle();trial.putString(KEY,PACKAGE);
            api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,sub,trial,false);
            boolean selected=false,bound=false;
            long deadline=SystemClock.elapsedRealtime()+15000;
            for(int i=0;i<30&&SystemClock.elapsedRealtime()<deadline;i++){
                PersistableBundle current=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
                selected=current!=null&&PACKAGE.equals(current.getString(KEY));
                bound=selected&&frameworkBinding();
                if(bound)break;Thread.sleep(500);
            }
            result.put("carrier_binder_write_readback",selected).put("qns_platform_binding",bound);
        }finally {
            if(attempted)api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,sub,null,false);
            boolean restored=false,complete=false;
            for(int i=0;i<30;i++){
                PersistableBundle now=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
                restored=now!=null&&Objects.equals(before.getString(KEY),now.getString(KEY));
                complete=same(before,now);
                if(restored&&complete)break;Thread.sleep(500);
            }
            result.put("original_qns_restored",restored);
            result.put("original_config_restored",complete);
            boolean persisted=false;
            for(File file:directory.listFiles())if(file.getName().startsWith("carrierconfig-")&&file.getName().contains("-override-"))persisted=true;
            result.put("persisted_override_created",persisted);
            System.out.println(result.toString());
            if(!restored||!complete||persisted)throw new IllegalStateException("framework-trial-restoration-unproven");
        }
        System.exit(result.optBoolean("carrier_binder_write_readback")&&result.optBoolean("qns_platform_binding")?0:1);
    }
}
