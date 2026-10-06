// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.io.IOException;
import org.json.JSONObject;

/** Pending recorded-owner recovery is accepted progress, never completed recovery. */
final class ModernActionPolicy {
    private ModernActionPolicy(){}
    static boolean pendingRecovery(JSONObject reply,String action,boolean recordedOwnerValidated)throws Exception {
        // Published older helpers omit the new recorded-owner field. They can
        // attest only a full live restore/archive; absence/pending still requires
        // the new explicit protocol. The root worker must validate the journal.
        if(recordedOwnerValidated&&"rollback".equals(action)&&reply!=null&&!reply.has("recorded_owner_confirmed")&&Boolean.TRUE.equals(reply.opt("owner_profile_confirmed"))&&
            (Boolean.FALSE.equals(reply.opt("selection_present"))||"RESTORED".equals(reply.optString("phase"))&&Boolean.TRUE.equals(reply.opt("carrier_config_restored")))) {
            reply=new JSONObject(reply.toString()).put("recorded_owner_confirmed",true);
        }
        return pendingRecovery(reply,action);
    }
    static boolean pendingRecovery(JSONObject reply,String action)throws IOException {
        if(reply==null||reply.has("error"))throw new IOException("modern-action-unconfirmed");
        if(reply.has("recovery_pending_owner")&&!(reply.opt("recovery_pending_owner") instanceof Boolean))throw new IOException("modern-action-unconfirmed");
        if(!"rollback".equals(action)) {
            if(!Boolean.TRUE.equals(reply.opt("owner_profile_confirmed"))||Boolean.TRUE.equals(reply.opt("recovery_pending_owner")))throw new IOException("modern-action-unconfirmed");
            return false;
        }
        if(!Boolean.TRUE.equals(reply.opt("recorded_owner_confirmed")))throw new IOException("recorded-recovery-unconfirmed");
        if(Boolean.TRUE.equals(reply.opt("recovery_pending_owner"))) {
            if(!Boolean.TRUE.equals(reply.opt("selection_present"))||!"RESTORING".equals(reply.optString("phase"))||Boolean.TRUE.equals(reply.opt("carrier_config_restored")))throw new IOException("pending-recovery-unconfirmed");
            return true;
        }
        if(!Boolean.TRUE.equals(reply.opt("owner_profile_confirmed"))||!(Boolean.FALSE.equals(reply.opt("selection_present"))||"RESTORED".equals(reply.optString("phase"))&&Boolean.TRUE.equals(reply.opt("carrier_config_restored"))))throw new IOException("full-recovery-unconfirmed");
        return false;
    }
}
