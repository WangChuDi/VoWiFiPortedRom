// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.io.IOException;
import org.json.JSONObject;
public final class ModernActionPolicyTest {
    private static int cases;
    private static JSONObject pending()throws Exception{return new JSONObject().put("recorded_owner_confirmed",true).put("owner_profile_confirmed",false).put("selection_present",true).put("phase","RESTORING").put("recovery_pending_owner",true);}
    private static void expect(JSONObject reply,String action,boolean pending)throws Exception{if(ModernActionPolicy.pendingRecovery(reply,action)!=pending)throw new AssertionError("action-policy-misclassified");cases++;}
    private static void reject(JSONObject reply,String action)throws Exception{try{ModernActionPolicy.pendingRecovery(reply,action);throw new AssertionError("unconfirmed-action-accepted");}catch(IOException expected){cases++;}}
    public static void main(String[] args)throws Exception{
        expect(pending(),"rollback",true);
        expect(new JSONObject().put("owner_profile_confirmed",true),"trial",false);
        JSONObject restored=new JSONObject().put("owner_profile_confirmed",true).put("recorded_owner_confirmed",true).put("selection_present",true).put("phase","RESTORED").put("carrier_config_restored",true);
        expect(restored,"rollback",false);expect(new JSONObject().put("owner_profile_confirmed",true).put("recorded_owner_confirmed",true).put("selection_present",false),"rollback",false);
        reject(pending(),"trial");reject(pending(),"enable");reject(pending(),"reload");reject(new JSONObject(),"rollback");reject(null,"rollback");
        reject(pending().put("recorded_owner_confirmed",false),"rollback");reject(pending().put("phase","ACTIVE"),"rollback");
        reject(pending().put("carrier_config_restored",true),"rollback");reject(pending().put("selection_present",false),"rollback");
        reject(pending().put("recovery_pending_owner","true"),"rollback");reject(pending().put("error","IOException"),"rollback");
        reject(restored.put("carrier_config_restored",false),"rollback");reject(new JSONObject().put("owner_profile_confirmed","true"),"reload");
        JSONObject legacy=new JSONObject().put("owner_profile_confirmed",true).put("selection_present",true).put("phase","RESTORED").put("carrier_config_restored",true);
        if(ModernActionPolicy.pendingRecovery(legacy,"rollback",true))throw new AssertionError("legacy-full-restore-became-pending");cases++;
        try{ModernActionPolicy.pendingRecovery(legacy,"rollback",false);throw new AssertionError("unvalidated-legacy-restore-accepted");}catch(IOException expected){cases++;}
        JSONObject legacyPending=pending();legacyPending.remove("recorded_owner_confirmed");
        try{ModernActionPolicy.pendingRecovery(legacyPending,"rollback",true);throw new AssertionError("legacy-pending-accepted");}catch(IOException expected){cases++;}
        System.out.println("Modern action-result contracts passed: "+cases);
    }
}
