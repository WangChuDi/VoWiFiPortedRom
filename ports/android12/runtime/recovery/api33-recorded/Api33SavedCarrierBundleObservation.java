// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Read the SAME saved records; no writes, constructors, override or reload. */
public final class Api33SavedCarrierBundleObservation {
    public static void main(String[] args){JSONObject result=new JSONObject();boolean success=false;
        try{
            Api33RecordedRecoveryAudit.profile();if(args.length!=1||!args[0].matches("slot-[0-7]-sub-[0-9]+-[0-9a-f]{32}"))throw new IOException("original-core-required");
            File core=new File(Api33RecordedRecoveryAudit.CARRIER,args[0]);Api33RecordedRecoveryAudit.canonical(core);Api33RecordedRecoveryAudit.inspect(true);
            if(!"9fb1923dfc09aa175b7d76fabed135bebf36e5cb7b4a8c3ee03398316c0cb634".equals(Api33RecordedRecoveryAudit.digest(new File(core,"phase"))))throw new IOException("original-file-handoff-required");
            Matcher name=Pattern.compile("slot-([0-7])-sub-([0-9]+)-[0-9a-f]{32}").matcher(args[0]);if(!name.matches())throw new IOException("original-core-required");int slot=Integer.parseInt(name.group(1)),sub=Integer.parseInt(name.group(2));
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();if(active==null||active.size()!=1||active.get(0).getSubscriptionId()!=sub||active.get(0).getSimSlotIndex()!=slot)throw new IOException("original-fake-owner-required");
            Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,ServiceManager.getService("carrier_config"));
            OverrideFileStore.Target target=CarrierOverrideFiles.target(context,active.get(0),api,loader,core);Method owner=ModernSeededPersistenceTrial.class.getDeclaredMethod("requireOwner",File.class,OverrideFileStore.Target.class);owner.setAccessible(true);owner.invoke(null,core,target);
            String[] names={"phase","profile","config-before.bin","emulator-original.bin","override-before.xml","persistence.properties","emulator-owner"};Map<String,String> hashes=new HashMap<>();for(String item:names)hashes.put(item,Api33RecordedRecoveryAudit.digest(new File(core,item)));
            Method read=Api33CarrierFinalizeOriginal.class.getDeclaredMethod("readBundle",File.class);read.setAccessible(true);Method diff=Api33CarrierFinalizeOriginal.class.getDeclaredMethod("differences",PersistableBundle.class,PersistableBundle.class);diff.setAccessible(true);
            PersistableBundle saved=(PersistableBundle)read.invoke(null,new File(core,"config-before.bin")),outer=(PersistableBundle)read.invoke(null,new File(core,"emulator-original.bin")),xml=CarrierOverrideFiles.readBundle(new File(core,"override-before.xml"));
            Object version=xml.get("__carrier_config_package_version__");xml.remove("__carrier_config_package_version__");PersistableBundle merged=new PersistableBundle(outer);merged.putAll(xml);PersistableBundle live=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
            if(target.file.exists()||!ModernCarrierBaseline.hasEmptyTransientOverride(slot)||!ModernProviderTransaction.same(outer,live))throw new IOException("outer-pristine-state-required");
            result.put("saved_seed_vs_original_outer_plus_saved_xml",diff.invoke(null,saved,merged)).put("saved_seed_contains_serialization_metadata",saved.containsKey("__carrier_config_package_version__")).put("saved_seed_version_equals_saved_xml",Objects.equals(saved.get("__carrier_config_package_version__"),version)).put("original_outer_config_loaded",true).put("original_override_absent",true);
            for(String item:names)if(!hashes.get(item).equals(Api33RecordedRecoveryAudit.digest(new File(core,item))))throw new IOException("original-record-changed");
            result.put("original_record_bytes_unchanged",true);success=true;
        }catch(Throwable error){try{result.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error));}catch(Exception ignored){}}
        try{result.put("schema",1).put("sdk",33).put("read_only",true).put("physical_phone_modified",false).put("status",success?"observed":"refused");}catch(Exception ignored){}System.out.println(result);System.exit(success?0:1);
    }
}
