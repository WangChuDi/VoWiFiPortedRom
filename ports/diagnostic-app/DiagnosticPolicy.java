// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import org.json.JSONObject;

/** Completeness is required for new changes; recorded-owner recovery is separate. */
final class DiagnosticPolicy {
    static boolean complete(JSONObject data){
        return data!=null&&data.optBoolean("diagnostic_complete")&&!data.has("diagnostic_error")&&!data.has("error");
    }
    static boolean wlanConfirmed(JSONObject data){
        return complete(data)&&data.optInt("ims_transport",-1)==2&&PlatformHealth.stable(data.optJSONObject("platform_health"));
    }
}
