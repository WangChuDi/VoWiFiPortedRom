// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
/** Original recorded API37 fixture recovery; no new snapshots or journal migration. */
public final class Api37CompatibleFixtureRecovery {
    static JSONObject outerChildObservation;
    static void stage(String s)throws Exception{System.out.println(new JSONObject().put("schema",1).put("sdk",37).put("checkpoint",s));System.out.flush();}
    static void canonical(File f)throws Exception{if(!f.getAbsoluteFile().equals(f.getCanonicalFile())||Files.isSymbolicLink(f.toPath()))throw new IOException("alias-refused");}
    static Properties read(File f)throws Exception{canonical(f);if(!f.isFile()||f.length()>16384)throw new IOException("record-unavailable");Properties p=new Properties();try(InputStream in=new FileInputStream(f)){p.load(in);}return p;}
    static String phase(Properties p){String s=p.getProperty("phase","");return Arrays.asList("PREPARING","PREPARED","SELECTING","ACTIVE","RESTORING","RESTORED","ARCHIVING").contains(s)?s:"UNKNOWN";}
    static JSONObject originalChild(String action,String fixture)throws Exception{
        File compatible=new File("/data/local/tmp/codex-modern-owner-audit.zip");canonical(compatible);
        if(android.system.Os.stat(compatible.getPath()).st_uid!=0||!"5c5748cdd75377a34a05001c22bb3d5e7a9375de060a265b0c3bbb51202b44c4".equals(ModernInstallationTransaction.digest(compatible)))throw new SecurityException("audited-compatible-helper-required");
        // The retained main prepares its own main Looper. Calling it inside this
        // already-initialized process would prepare the main Looper twice.
        String entry,stage;
        if("selection".equals(action)){entry="ModernSelectionEmulatorTrial";stage="selection-cleanup";}
        else if("installation".equals(action)){entry="ModernInstallationEmulatorTrial";stage="recover";}
        else if("outer".equals(action)){entry="ModernInstallationEmulatorTrial";stage="cleanup";}
        else if("audit".equals(action)){entry="ModernInstallationEmulatorTrial";stage="audit";}
        else throw new SecurityException("original-child-action-refused");
        // Delegated role restoration requires exactly the retained helper as
        // CLASSPATH. Execute its existing entry, without shadowing its classes.
        ProcessBuilder command=new ProcessBuilder("/system/bin/app_process","/system/bin",entry,stage,fixture);
        command.environment().put("CLASSPATH",compatible.getPath());command.redirectErrorStream(true);
        java.lang.Process child=command.start();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        java.util.concurrent.atomic.AtomicBoolean overflow=new java.util.concurrent.atomic.AtomicBoolean(),readFailed=new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader=new Thread(()->{try(InputStream in=child.getInputStream()){byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n<=32768&&!overflow.get())bytes.write(buffer,0,n);else overflow.set(true);}}catch(IOException ignored){readFailed.set(true);}},"original-outer-output");reader.setDaemon(true);reader.start();
        if(!child.waitFor(25,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();if(!child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("outer-child-termination-unconfirmed");throw new IOException("outer-child-deadline");}
        reader.join(1000);if(reader.isAlive())throw new IOException("outer-child-output-unconfirmed");
        if(overflow.get()||readFailed.get())throw new IOException("outer-child-output-incomplete");
        outerChildObservation=new JSONObject().put("separate_child_process",true).put("child_exit",child.exitValue());
        JSONObject result=null;for(String line:bytes.toString("UTF-8").split("[\\r\\n]+"))if(line.startsWith("{")&&line.endsWith("}")){if(result!=null)throw new IOException("outer-child-multiple-results");result=new JSONObject(line);}
        if(result!=null){String error=result.optString("error"),origin=result.optString("origin");if(error.matches("[A-Za-z]+(?:Exception|Error)"))outerChildObservation.put("child_error",error);if(origin.matches("Modern[A-Za-z]+\\.[A-Za-z0-9_$<>]+:[0-9]{1,6}|external"))outerChildObservation.put("child_origin",origin);}
        if(result==null||result.optInt("schema",-1)!=1||result.optInt("sdk",-1)!=37||!stage.equals(result.optString("stage")))throw new IOException("outer-child-result-unconfirmed");
        // These pinned retained entries have no status field. A contradictory
        // status from a future entry must not override exit/operation evidence.
        if(result.has("status")&&!"passed".equals(result.optString("status")))throw new IOException("outer-child-result-unconfirmed");
        if(child.exitValue()!=0||result.has("error"))throw new IOException("outer-child-operation-failed");
        if("selection".equals(action)){if(!result.optBoolean("selection_outer_cleanup_completed"))throw new IOException("selection-child-policy-unconfirmed");}
        else if("installation".equals(action)){if(!result.optBoolean("restore_resumed_and_repeated"))throw new IOException("installation-child-policy-unconfirmed");}
        else if("outer".equals(action)){if(!result.optBoolean("entire_outer_permission_observation_restored"))throw new IOException("outer-policy-unconfirmed");}
        else {JSONArray changes=result.optJSONArray("fixed_policy_changes");if(changes==null)throw new IOException("outer-original-policy-audit-unconfirmed");
            for(int i=0;i<changes.length();i++){JSONObject change=changes.getJSONObject(i);String field=change.optString("field"),before=change.optString("before"),after=change.optString("after");
                if(!field.matches("flags\\.[012]\\.(READ_PHONE_STATE|RECORD_AUDIO|SEND_SMS)")||!before.matches("[0-9]{1,10}")||!after.matches("[0-9]{1,10}")||((Integer.parseInt(before)^Integer.parseInt(after))&~768)!=0)throw new IOException("outer-original-policy-audit-unconfirmed");}
        }
        JSONObject observed=new JSONObject().put("separate_child_process",true).put("child_exit",child.exitValue()).put("original_entry_confirmed",true).put("compatible_helper_only_classpath",true).put("permission_policy_excluded_informational_mask",768);
        if("audit".equals(action))observed.put("raw_flag_difference_count",result.getJSONArray("fixed_policy_changes").length()).put("authorization_policy_changes",0);
        if("outer".equals(action))observed.put("sensitivity_metadata_exactly_unchanged_during_cleanup",result.optBoolean("sensitivity_metadata_exactly_unchanged_during_cleanup"));
        return observed;
    }
    public static void main(String[] args){JSONObject out=new JSONObject();boolean ok=false;
        try{
            if(args.length!=2||!Arrays.asList("inventory","selection","installation","outer","audit").contains(args[0])||!args[1].matches("[0-9]{1,2}")||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT!=37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!"CodexVoWiFiApi37".equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-api37-required");
            out.put("schema",1).put("sdk",37).put("action",args[0]).put("read_only","inventory".equals(args[0])).put("physical_phone_modified",false).put("new_baseline_created",false);
            out.put("compatibility_audited",true).put("legacy_journal_migrated",false).put("permission_policy_excluded_informational_mask",768);
            stage("context");Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            stage("subscription");SubscriptionManager subscriptions=context.getSystemService(SubscriptionManager.class);List<SubscriptionInfo> active=null;
            stage("telephony");TelephonyManager phone=context.getSystemService(TelephonyManager.class);boolean single=false,ready=false;int sub=-1,sim=TelephonyManager.SIM_STATE_UNKNOWN,stable=0,previous=-1,attempts=0;long deadline=SystemClock.elapsedRealtime()+10000;
            do {active=subscriptions.getActiveSubscriptionInfoList();single=active!=null&&active.size()==1&&active.get(0).getSimSlotIndex()==0;sub=single?active.get(0).getSubscriptionId():-1;sim=phone.getSimState(0);String op=single?phone.createForSubscriptionId(sub).getSimOperator():null;
                ready=single&&sim==TelephonyManager.SIM_STATE_READY&&op!=null&&op.matches("[0-9]{5,6}")&&!"23415".equals(op);stable=ready?(previous==sub?stable+1:1):0;previous=sub;attempts++;if(stable>=2)break;Thread.sleep(250);
            }while(SystemClock.elapsedRealtime()<deadline);ready=stable>=2;stage("sim-observed");out.put("readiness_samples",attempts).put("two_consecutive_ready_samples",ready);
            out.put("sim_state",sim).put("single_ready_fake_subscription",ready);
            File base=new File("/data/local/tmp/codex-modern-installation-tests");canonical(base);File[] dirs=base.listFiles();if(dirs==null||dirs.length>64)throw new IOException("bounded-fixture-inventory-required");Arrays.sort(dirs,Comparator.comparing(File::getName));
            JSONArray records=new JSONArray();ArrayList<File> targets=new ArrayList<>();
            for(File root:dirs){canonical(root);if(!root.isDirectory()||!root.getName().matches("[0-9a-f]{32}"))throw new IOException("fixture-directory-refused");
                File baseline=new File(root,"state/baseline.properties"),outer=new File(root,"outer.properties");canonical(baseline);canonical(outer);
                if(!outer.isFile())continue;
                Properties install=read(baseline);if(!"1".equals(install.getProperty("schema"))||!(Build.VERSION.SDK_INT+":"+Build.FINGERPRINT).equals(install.getProperty("build")))throw new IOException("original-installation-schema-refused");
                File owners=new File(root,"selection/owners");canonical(owners);File[] entries=owners.isDirectory()?owners.listFiles():new File[0];if(entries==null||entries.length>8)throw new IOException("owner-inventory-refused");
                JSONObject item=new JSONObject().put("ordinal",targets.size()).put("installation_phase",phase(install)).put("installation_schema_1",true).put("outer_record_present",true);JSONArray ownerStates=new JSONArray();
                for(File owner:entries){canonical(owner);if(!owner.isDirectory()||!owner.getName().matches("slot-[0-7]-sub-[0-9]+"))throw new IOException("owner-directory-refused");File file=new File(owner,"selection.properties");canonical(file);if(!file.exists())continue;Properties p=read(file);
                    ownerStates.put(new JSONObject().put("phase",phase(p)).put("schema_3","3".equals(p.getProperty("schema"))).put("current_owner",single&&("slot-0-sub-"+sub).equals(owner.getName())).put("mode_owned","true".equals(p.getProperty("mode_owned"))));
                }
                item.put("owners",ownerStates);records.put(item);targets.add(root);
            }
            out.put("fixture_count",targets.size()).put("fixtures",records);stage("inventory-observed");
            if("inventory".equals(args[0])){ok=true;}
            else{
                if(!ready)throw new SecurityException("ready-original-owner-required");ModernPhoneIdle.requireIdle(context);int ordinal=Integer.parseInt(args[1]);if(ordinal>=targets.size())throw new SecurityException("original-fixture-ordinal-refused");File root=targets.get(ordinal);
                File owner=new File(root,"selection/owners/slot-0-sub-"+sub),record=new File(owner,"selection.properties");canonical(record);Properties selection=read(record);
                if(!"3".equals(selection.getProperty("schema"))||!Integer.toString(sub).equals(selection.getProperty("sub"))||!"0".equals(selection.getProperty("slot")))throw new SecurityException("original-owner-record-refused");
                byte[] hash=MessageDigest.getInstance("SHA-256").digest((root.getName()+":selection").getBytes("UTF-8"));StringBuilder suffix=new StringBuilder();for(byte b:hash)suffix.append(String.format(Locale.ROOT,"%02x",b&255));
                File carrier=new File("/data/local/tmp/codex-modern-persistence-tests","slot-0-sub-"+sub+"-"+suffix.substring(0,32));File coordination=new File(root,"selection");
                if("selection".equals(args[0])){
                    stage("selection-restore");if("ARCHIVING".equals(selection.getProperty("phase")))throw new IOException("archive-pending-refused");
                    out.put("selection_child",originalChild("selection",root.getName()));out.put("selection_repeat_child",originalChild("selection",root.getName()));
                    try(ModernSelectionTransaction tx=new ModernSelectionTransaction(context,0,sub,new File(root,"module"),new File(root,"state"),coordination,carrier,true)){
                        Properties status=tx.status();
                        if(!"RESTORED".equals(status.getProperty("phase"))||!"false".equals(status.getProperty("mode_owned"))||!tx.originalCarrierRestored())throw new IOException("original-selection-restore-unconfirmed");
                    }out.put("original_selection_carrier_lease_mode_restored",true);
                }else{
                    if(!"RESTORED".equals(selection.getProperty("phase"))||!"false".equals(selection.getProperty("mode_owned")))throw new IOException("selection-recovery-required-first");
                    if("installation".equals(args[0])){stage("installation-restore");out.put("installation_child",originalChild("installation",root.getName()));if(!"RESTORED".equals(read(new File(root,"state/baseline.properties")).getProperty("phase")))throw new IOException("original-installation-restore-unconfirmed");out.put("original_installation_restored",true);}
                    else{if(!"RESTORED".equals(read(new File(root,"state/baseline.properties")).getProperty("phase")))throw new IOException("installation-recovery-required-first");stage("original-outer-entry");out.put("outer",originalChild(args[0],root.getName()));}
                }ok=true;
            }
        }catch(Throwable e){try{out.put("error",e.getClass().getSimpleName()).put("reason",ModernSafeFailure.reason(e)).put("origin",ModernSafeFailure.origin(e));if(outerChildObservation!=null)out.put("outer_child_observation",outerChildObservation);}catch(Exception ignored){}}
        try{out.put("status",ok?"passed":"failed");}catch(Exception ignored){}System.out.println(out);System.out.flush();System.exit(ok?0:1);
    }
}
