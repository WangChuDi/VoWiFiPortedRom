// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.app.ActivityThread;
import android.content.Context;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Root worker keeps modern lease tokens entirely outside application telemetry. */
public final class ModernAppActions {
    public static void main(String[] args) {
        JSONObject result=new JSONObject();boolean success=false;
        try {
            if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||args.length!=4||!Arrays.asList("trial","enable","reload","rollback").contains(args[0])||!args[1].matches("[0-7]")||!args[2].matches("0|[1-9][0-9]{0,9}")||!args[3].matches("[1-7]"))throw new SecurityException("modern-app-action-refused");
            Looper.prepareMainLooper();Context context=ActivityThread.systemMain().getSystemContext();RootDiagnostics.initializeTelephony();int slot=Integer.parseInt(args[1]),sub=Integer.parseInt(args[2]);
            if(!"rollback".equals(args[0])&&!ModernControllerObservation.moduleMatches(context))throw new IOException("modern-module-profile-mismatch");
            // Recheck the tuple immediately before invoking the guarded production
            // CLI; a selection in the activity is not proof the SIM is unchanged.
            if("rollback".equals(args[0])) {
                // Only an existing, same-build private owner record can request
                // recovery without a live SIM. The CLI rechecks actual absence.
                ModernControllerObservation.owner(new File(ModernControllerObservation.STATE,"coordination/owners/slot-"+slot+"-sub-"+sub+"/selection.properties"),slot,sub);
            } else {
                boolean ready=false;List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();TelephonyManager phone=context.getSystemService(TelephonyManager.class);
                if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot&&info.getSubscriptionId()==sub&&phone.getSimState(slot)==TelephonyManager.SIM_STATE_READY&&"23415".equals(phone.createForSubscriptionId(sub).getSimOperator()))ready=true;
                if(!ready)throw new SecurityException("ready-VOXI-owner-required");
            }
            ArrayList<String> command=new ArrayList<>(Arrays.asList("sh",new File(ModernControllerObservation.MODULE,"control.sh").getPath()));
            switch(args[0]) {
                case "trial":command.addAll(Arrays.asList("select",args[1],args[2],args[3]));break;
                case "rollback":command.addAll(Arrays.asList("recover",args[1],args[2]));break;
                default:
                    File file=new File(ModernControllerObservation.STATE,"coordination/owners/slot-"+slot+"-sub-"+sub+"/selection.properties");Properties owner=ModernControllerObservation.owner(file,slot,sub);
                    if(!"ACTIVE".equals(owner.getProperty("phase")))throw new IOException("active-selection-required");
                    command.addAll(Arrays.asList("enable".equals(args[0])?"retain":"renew",args[1],args[2],owner.getProperty("token")));
            }
            java.lang.Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
            // Drain without exporting stdout: select emits a private token. Parse
            // only bounded final result status, never relay subprocess JSON/text.
            final JSONObject[] finalResult={null};final boolean[] invalid={false};
            Thread reader=new Thread(()->{try(BufferedReader input=new BufferedReader(new InputStreamReader(child.getInputStream(),"UTF-8"))){String line;int bytes=0;while((line=input.readLine())!=null){bytes+=line.length();if(bytes>65536){invalid[0]=true;continue;}if(line.startsWith("{")&&line.endsWith("}"))try{finalResult[0]=new JSONObject(line);}catch(Exception ignored){invalid[0]=true;}}}catch(IOException failure){invalid[0]=true;}});reader.setDaemon(true);reader.start();
            if(!child.waitFor(300,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("modern-action-observation-timeout");}reader.join(1000);
            JSONObject reply=finalResult[0];String expected="trial".equals(args[0])?"trial":"rollback".equals(args[0])?"recover":"enable".equals(args[0])?"retain":"renew";
            if(child.exitValue()!=0||reader.isAlive()||invalid[0]||reply==null||reply.has("error")||reply.optInt("schema")!=1||reply.optInt("sdk")!=Build.VERSION.SDK_INT||!expected.equals(reply.optString("action")))throw new IOException("modern-action-unconfirmed");
            boolean pending=ModernActionPolicy.pendingRecovery(reply,args[0],"rollback".equals(args[0]));
            result.put("schema",1).put("sdk",Build.VERSION.SDK_INT).put("action",args[0]).put("action_completed",!pending).put("recovery_pending_owner",pending).put("registration_verified",false).put("call_sms_verified",false);success=true;
        }catch(Throwable error){try{result.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        System.out.println(result.toString());System.exit(success?0:1);
    }
}
