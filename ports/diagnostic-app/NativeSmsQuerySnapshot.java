// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.*;

/** Closed, per-request own-UID ISms result. It does not describe software dispatcher readiness. */
public final class NativeSmsQuerySnapshot {
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList("schema","channel","slot","sub","nonce","pid","boot","sample_elapsed","result","status","self_uid","query_elapsed","read_only","scope"));
    public static Map<String,Object> validate(Map<String,Object> v,int slot,int sub,String nonce,int pid,int boot,long begin,long now){
        if(!v.keySet().equals(KEYS)||slot<0||slot>7||sub<0||pid<=0||boot<0||begin<0||now<begin)throw new IllegalArgumentException("native-query-schema");
        if(ImsClientSnapshot.number(v,"schema")!=1||!"ims-native-sms".equals(v.get("channel"))||ImsClientSnapshot.number(v,"slot")!=slot||ImsClientSnapshot.number(v,"sub")!=sub||!nonce.equals(v.get("nonce"))||ImsClientSnapshot.number(v,"pid")!=pid||ImsClientSnapshot.number(v,"boot")!=boot)throw new IllegalArgumentException("native-query-provenance");
        long sample=ImsClientSnapshot.number(v,"sample_elapsed"),checked=ImsClientSnapshot.number(v,"query_elapsed");
        if(sample<begin||sample>now||now-sample>5000||checked<0||checked>sample||checked!=0&&checked<begin)throw new IllegalArgumentException("native-query-stale");
        boolean self=ImsClientSnapshot.flag(v,"self_uid");
        if(!ImsClientSnapshot.flag(v,"read_only")||!"IMS_OR_RADIO".equals(v.get("scope"))||!Arrays.asList("OBSERVED","UNAVAILABLE","TIMEOUT","BUSY","UNSUPPORTED").contains(v.get("status"))||!Arrays.asList("UNKNOWN","TRUE","FALSE").contains(v.get("result")))throw new IllegalArgumentException("native-query-state");
        boolean known=!"UNKNOWN".equals(v.get("result"));
        if(known!= "OBSERVED".equals(v.get("status"))||known&&(!self||checked==0))throw new IllegalArgumentException("native-query-context");
        Map<String,Object> clean=new LinkedHashMap<>(v);for(String key:new String[]{"nonce","pid","boot"})clean.remove(key);return clean;
    }
}
