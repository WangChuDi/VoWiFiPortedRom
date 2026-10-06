// SPDX-License-Identifier: GPL-2.0
import android.app.*;
import android.content.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Complete the saved RESTORED_FILE handoff without resampling its baseline. */
public final class Api33CarrierFinalizeOriginal {
    private static final String MARKER="codex_modern_persistence_test_marker_string";
    private static final String VERSION="__carrier_config_package_version__";
    /** Counts only: never publish configuration keys, values, SIMs or filenames. */
    private static JSONObject differences(PersistableBundle expected,PersistableBundle actual)throws Exception{
        if(expected==null||actual==null)return new JSONObject().put("bundle_available",false);
        if(expected.size()>4096||actual.size()>4096)throw new IOException("bounded-diagnostic-bundle-required");
        int missing=0,extra=0,changed=0,typeChanged=0,missingNull=0;JSONArray publicMissing=new JSONArray();
        for(String key:expected.keySet()){
            if(!actual.containsKey(key)){
                missing++;if(expected.get(key)==null)missingNull++;
                if(VERSION.equals(key))publicMissing.put("serialization-package-version");
                else for(java.lang.reflect.Field field:CarrierConfigManager.class.getFields())if(field.getType()==String.class&&java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getName().startsWith("KEY_")&&key.equals(field.get(null)))publicMissing.put(field.getName());
                continue;
            }
            Object left=expected.get(key),right=actual.get(key);
            boolean equal=left instanceof PersistableBundle&&right instanceof PersistableBundle?ModernProviderTransaction.same((PersistableBundle)left,(PersistableBundle)right):Objects.deepEquals(left,right);
            if(!equal){changed++;if(left!=null&&right!=null&&!left.getClass().equals(right.getClass()))typeChanged++;}
        }
        for(String key:actual.keySet())if(!expected.containsKey(key))extra++;
        return new JSONObject().put("bundle_available",true).put("expected_key_count",expected.size()).put("actual_key_count",actual.size()).put("missing_key_count",missing).put("missing_null_value_count",missingNull).put("missing_public_key_constants",publicMissing).put("extra_key_count",extra).put("changed_value_count",changed).put("changed_type_count",typeChanged);
    }
    private static JSONObject loaderObservation(Context context,Class<?> api,Object loader,int sub,int slot,OverrideFileStore.Target target,PersistableBundle seeded,PersistableBundle outer,String nonce)throws Exception{
        PersistableBundle live=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android"),disk=CarrierOverrideFiles.readBundle(target.file);
        TelephonyManager manager=context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub);
        String serial=manager.getSimSerialNumber(),carrierPackage=(String)api.getMethod("getDefaultCarrierServicePackageName").invoke(loader);
        OverrideFileStore.Target specific=new OverrideFileStore.Target(target.directory,carrierPackage,serial,manager.getSimSpecificCarrierId());
        String packageVersion=Long.toString(context.getPackageManager().getPackageInfo(carrierPackage,0).getLongVersionCode());
        Object savedVersion=disk.get(VERSION);PersistableBundle withoutMetadata=new PersistableBundle(disk);withoutMetadata.remove(VERSION);
        PersistableBundle merged=new PersistableBundle(outer);merged.putAll(withoutMetadata);
        return new JSONObject().put("file_present",target.file.isFile()).put("filename_matches_current_specific_carrier",target.file.equals(specific.file)).put("xml_package_version_present",savedVersion!=null).put("xml_package_version_is_string",savedVersion instanceof String).put("xml_package_version_matches_installed",packageVersion.equals(savedVersion)).put("sim_ready",manager.getSimState(slot)==TelephonyManager.SIM_STATE_READY).put("live_config_applied",live!=null&&live.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL)).put("live_contains_serialization_metadata",live!=null&&live.containsKey(VERSION)).put("live_marker_matches_original",live!=null&&nonce.equals(live.getString(MARKER))).put("xml_marker_matches_original",nonce.equals(disk.getString(MARKER))).put("transient_override_empty",ModernCarrierBaseline.hasEmptyTransientOverride(slot)).put("live_matches_original_outer",ModernProviderTransaction.same(outer,live)).put("saved_seed_matches_outer_merged_with_xml",ModernProviderTransaction.same(seeded,merged)).put("saved_seed_vs_live",differences(seeded,live)).put("outer_plus_xml_vs_live",differences(merged,live));
    }
    private static PersistableBundle readBundle(File file)throws Exception{
        Api33RecordedRecoveryAudit.canonical(file);if(!file.isFile()||file.length()<1||file.length()>1048576)throw new IOException("bounded-original-bundle-required");byte[] bytes=Files.readAllBytes(file.toPath());Parcel parcel=Parcel.obtain();try{parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);PersistableBundle result=parcel.readTypedObject(PersistableBundle.CREATOR);if(result==null||parcel.dataAvail()!=0)throw new IOException("original-bundle-invalid");return result;}finally{parcel.recycle();}
    }
    private static void restoreOuter(Class<?> api,Object loader,int sub,int slot,OverrideFileStore.Target target,PersistableBundle original)throws Exception{
        api.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,sub,null,true);long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline){PersistableBundle current=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");if(!target.file.exists()&&ModernCarrierBaseline.hasEmptyTransientOverride(slot)&&ModernProviderTransaction.same(original,current))return;Thread.sleep(400);}throw new IOException("original-outer-carrier-unconfirmed");
    }
    /** Only the pinned legacy fake-SIM handoff may omit proven serialization metadata. */
    private static boolean confirmLegacyMetadata(Context context,Class<?> api,Object loader,int sub,int slot,File core,ModernProviderTransaction transaction,OverrideFileStore.Target target,PersistableBundle saved,PersistableBundle outer,String nonce,JSONObject observation)throws Exception{
        String[] required={"file_present","filename_matches_current_specific_carrier","xml_package_version_present","xml_package_version_is_string","xml_package_version_matches_installed","sim_ready","live_config_applied","live_marker_matches_original","xml_marker_matches_original","transient_override_empty"};
        for(String key:required)if(!observation.getBoolean(key))return false;
        if(observation.getBoolean("live_contains_serialization_metadata")||!saved.containsKey(VERSION)||!(saved.get(VERSION)instanceof String)||!"RESTORED_FILE".equals(transaction.statePhase()))return false;
        PersistableBundle disk=CarrierOverrideFiles.readBundle(target.file),live=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
        if(!Objects.equals(saved.get(VERSION),disk.get(VERSION))||live==null||live.containsKey(VERSION)||!nonce.equals(live.getString(MARKER)))return false;
        PersistableBundle normalized=new PersistableBundle(saved);normalized.remove(VERSION);PersistableBundle merged=new PersistableBundle(outer),diskConfig=new PersistableBundle(disk);diskConfig.remove(VERSION);merged.putAll(diskConfig);
        if(!ModernProviderTransaction.same(normalized,live)||!ModernProviderTransaction.same(normalized,merged)||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))return false;
        // Saved bundle, XML, owner and metadata stay byte-for-byte unchanged.
        // Commit only after proving every actual carrier value and saved disk layer.
        Api33RecordedRecoveryAudit.profile();
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if(active==null||active.size()!=1||active.get(0).getSubscriptionId()!=sub||active.get(0).getSimSlotIndex()!=slot)throw new IOException("original-fake-owner-required");
        TelephonyManager manager=context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub);String operator=manager.getSimOperator();
        if(manager.getSimState(slot)!=TelephonyManager.SIM_STATE_READY||operator==null||!operator.matches("[0-9]{5,6}")||"23415".equals(operator))throw new IOException("original-fake-owner-required");
        CarrierOverrideFiles.store(core).requireIdentity(target);CarrierOverrideFiles.store(core).verifyRestored(target);
        if(!target.file.equals(CarrierOverrideFiles.target(context,active.get(0),api,loader,core).file))throw new IOException("original-target-changed");
        java.lang.reflect.Method phase=ModernProviderTransaction.class.getDeclaredMethod("phase",String.class);phase.setAccessible(true);phase.invoke(transaction,"RESTORED");
        return "RESTORED".equals(transaction.statePhase());
    }
    public static void main(String[] args){JSONObject output=new JSONObject();boolean success=false;String checkpoint="profile";
        try{
            Api33RecordedRecoveryAudit.profile();if(args.length!=1)throw new SecurityException("fixed-original-core-required");Matcher name=Pattern.compile("slot-([0-7])-sub-([0-9]+)-[0-9a-f]{32}").matcher(args[0]);if(!name.matches())throw new SecurityException("fixed-original-core-required");
            File core=new File(Api33RecordedRecoveryAudit.CARRIER,args[0]);Api33RecordedRecoveryAudit.canonical(core);File phase=new File(core,"phase"),snapshot=new File(core,"config-before.bin"),profile=new File(core,"profile");
            if(!"9fb1923dfc09aa175b7d76fabed135bebf36e5cb7b4a8c3ee03398316c0cb634".equals(Api33RecordedRecoveryAudit.digest(phase))||!snapshot.isFile()||new File(core,"config-before.bin.new").exists())throw new IOException("saved-file-handoff-required");
            String snapshotSha=Api33RecordedRecoveryAudit.digest(snapshot),profileSha=Api33RecordedRecoveryAudit.digest(profile);Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();ModernPhoneIdle.requireIdle(context);
            try(ModernControllerLock held=new ModernControllerLock(Api33RecordedRecoveryAudit.CARRIER,true)){
                JSONObject before=Api33RecordedRecoveryAudit.inspect(true);if(before.getInt("pending_carrier_count")!=1)throw new IOException("one-original-handoff-required");output.put("before",before);checkpoint="carrier-original-verification";
                int slot=Integer.parseInt(name.group(1)),sub=Integer.parseInt(name.group(2));
                try(ModernProviderTransaction transaction=new ModernProviderTransaction(context,slot,sub,core,true,held)){
                    if(!"RESTORED_FILE".equals(transaction.statePhase()))throw new IOException("saved-file-handoff-required");
                    List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();if(active==null||active.size()!=1||active.get(0).getSubscriptionId()!=sub||active.get(0).getSimSlotIndex()!=slot)throw new IOException("original-fake-owner-required");SubscriptionInfo selected=active.get(0);
                    Class<?> api=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");IBinder binder=ServiceManager.getService("carrier_config");if(binder==null||!binder.isBinderAlive())throw new IOException("carrier-service-unavailable");Object loader=Class.forName(api.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);OverrideFileStore.Target target=CarrierOverrideFiles.target(context,selected,api,loader,core);
                    File outerSnapshot=new File(core,"emulator-original.bin"),backup=new File(core,"override-before.xml");PersistableBundle outerOriginal=readBundle(outerSnapshot),seededOriginal=readBundle(snapshot);String outerSha=Api33RecordedRecoveryAudit.digest(outerSnapshot),backupSha=Api33RecordedRecoveryAudit.digest(backup),metadataSha=Api33RecordedRecoveryAudit.digest(new File(core,"persistence.properties"));String nonce=core.getName().substring(core.getName().lastIndexOf('-')+1);
                    java.lang.reflect.Method requireOwner=ModernSeededPersistenceTrial.class.getDeclaredMethod("requireOwner",File.class,OverrideFileStore.Target.class);requireOwner.setAccessible(true);requireOwner.invoke(null,core,target);
                    PersistableBundle live=(PersistableBundle)CarrierConfigReadCompat.read(api,loader,sub,"android");
                    if(target.file.exists()||!ModernCarrierBaseline.hasEmptyTransientOverride(slot)||!ModernProviderTransaction.same(outerOriginal,live)||outerOriginal.containsKey(MARKER)||!nonce.equals(seededOriginal.getString(MARKER))||!nonce.equals(CarrierOverrideFiles.readBundle(backup).getString(MARKER)))throw new IOException("known-original-outer-cleanup-required");
                    output.put("outer_pristine_config_verified_before",true).put("seeded_marker_identity_verified",true).put("original_fixture_owner_verified",true);boolean reapplied=false;Exception reloadFailure=null;
                    try{
                        checkpoint="reapply-saved-seeded-file";CarrierOverrideFiles.store(core).requireIdentity(target);if(target.file.exists()||!ModernCarrierBaseline.hasEmptyTransientOverride(slot))throw new IOException("original-outer-changed");reapplied=true;CarrierOverrideFiles.store(core).restore(target);CarrierOverrideFiles.restoreLabel(target);
                        checkpoint="reload-original-carrier";transaction.reloadRestored();checkpoint="confirm-original-carrier";if(!transaction.confirmRestored()||!"RESTORED".equals(transaction.statePhase()))throw new IOException("original-carrier-unconfirmed");output.put("seeded_original_loaded_confirmed",true);
                    }catch(Exception failure){
                        reloadFailure=failure;output.put("reload_failure_checkpoint",checkpoint);
                        try{
                            JSONObject observed=loaderObservation(context,api,loader,sub,slot,target,seededOriginal,outerOriginal,nonce);output.put("loader_observation_before_outer_cleanup",observed);
                            if("restored-loader-reload-unconfirmed".equals(failure.getMessage())&&confirmLegacyMetadata(context,api,loader,sub,slot,core,transaction,target,seededOriginal,outerOriginal,nonce,observed)){
                                reloadFailure=null;output.put("legacy_serialization_metadata_only_difference_confirmed",true).put("all_original_carrier_values_loaded_confirmed",true).put("serialized_original_snapshot_raw_equal",false).put("seeded_original_loaded_confirmed",true).put("production_comparison_unchanged",true);
                            }
                        }catch(Exception diagnosticFailure){output.put("loader_observation_error",diagnosticFailure.getClass().getSimpleName());}
                    }finally{
                        if(reapplied){checkpoint="restore-original-outer-carrier";restoreOuter(api,loader,sub,slot,target,outerOriginal);output.put("original_outer_loaded_confirmed",true).put("original_outer_override_absent",true);}
                    }
                    if(!outerSha.equals(Api33RecordedRecoveryAudit.digest(outerSnapshot))||!backupSha.equals(Api33RecordedRecoveryAudit.digest(backup))||!metadataSha.equals(Api33RecordedRecoveryAudit.digest(new File(core,"persistence.properties"))))throw new IOException("original-carrier-evidence-changed");output.put("original_outer_snapshot_bytes_unchanged",true).put("original_xml_backup_bytes_unchanged",true).put("original_persistence_metadata_bytes_unchanged",true);
                    if(reloadFailure!=null){
                        JSONObject failedAfter=Api33RecordedRecoveryAudit.inspect(true);
                        if(before.getInt("pending_installation_count")!=failedAfter.getInt("pending_installation_count")||!before.getString("owner_records_aggregate_sha256").equals(failedAfter.getString("owner_records_aggregate_sha256"))||!before.getString("outer_records_aggregate_sha256").equals(failedAfter.getString("outer_records_aggregate_sha256"))||!snapshotSha.equals(Api33RecordedRecoveryAudit.digest(snapshot))||!profileSha.equals(Api33RecordedRecoveryAudit.digest(profile)))throw new IOException("original-evidence-changed");
                        output.put("after_failed_reload",failedAfter).put("original_snapshot_bytes_unchanged",true).put("original_profile_bytes_unchanged",true).put("owner_record_bytes_unchanged",true).put("outer_record_bytes_unchanged",true).put("new_baseline_sampled",false);checkpoint="reload-original-carrier";throw reloadFailure;
                    }
                }
                JSONObject after=Api33RecordedRecoveryAudit.inspect();if(before.getInt("pending_installation_count")!=after.getInt("pending_installation_count")||!before.getString("owner_records_aggregate_sha256").equals(after.getString("owner_records_aggregate_sha256"))||!before.getString("outer_records_aggregate_sha256").equals(after.getString("outer_records_aggregate_sha256"))||!snapshotSha.equals(Api33RecordedRecoveryAudit.digest(snapshot))||!profileSha.equals(Api33RecordedRecoveryAudit.digest(profile)))throw new IOException("original-evidence-changed");
                output.put("after",after).put("original_snapshot_sha256",snapshotSha).put("original_snapshot_bytes_unchanged",true).put("original_profile_bytes_unchanged",true).put("owner_record_bytes_unchanged",true).put("outer_record_bytes_unchanged",true).put("original_carrier_loaded_confirmed",true).put("new_baseline_sampled",false);success=true;
            }
        }catch(Throwable error){try{output.put("error",error.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(error)).put("origin",ModernSafeFailure.origin(error)).put("checkpoint",checkpoint);}catch(Exception ignored){}}
        try{output.put("schema",1).put("sdk",33).put("physical_phone_modified",false).put("status",success?"passed":"refused");}catch(Exception ignored){}System.out.println(output);System.exit(success?0:1);
    }
}
