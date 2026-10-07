// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.*;

/** Closed metadata protocol, separate from the service-status schema. */
public final class ImsClientSnapshot {
    private static final Set<String> FLAGS=new HashSet<>(Arrays.asList("registered","callback_count_observed","initialized","removed","client_rebind_pending","sms_session_idle","rebind_requested","rebind_accepted"));
    private static final Set<String> COUNTERS=new HashSet<>(Arrays.asList("notification_returns","sms_ready_events","enable_events","disable_events","client_rebind_returns"));
    private static final Set<String> BASE=new HashSet<>(Arrays.asList("schema","channel","slot","sub","nonce","pid","boot","sample_elapsed","generation","enabled_mask","last_notified_mask","feature_state"));
    static long number(Map<String,Object> v,String key){
        Object n=v.get(key);if(!(n instanceof Number))throw new IllegalArgumentException("client-number");
        long i=((Number)n).longValue();if(((Number)n).doubleValue()!=i)throw new IllegalArgumentException("client-integer");return i;
    }
    static boolean flag(Map<String,Object> v,String key){Object b=v.get(key);if(!(b instanceof Boolean))throw new IllegalArgumentException("client-flag");return (Boolean)b;}
    public static boolean profileEligible(int sdk,String device,boolean pinned){return sdk==30&&"raphael".equals(device)&&pinned;}
    public static Map<String,Object> validate(Map<String,Object> v,int slot,int sub,String nonce,int pid,int boot,long begin,long now,boolean requested){
        if(number(v,"schema")!=1||!"ims".equals(v.get("channel"))||number(v,"slot")!=slot||number(v,"sub")!=sub||!nonce.equals(v.get("nonce"))||number(v,"pid")!=pid||number(v,"boot")!=boot||boot<0||pid<=0)throw new IllegalArgumentException("client-provenance");
        long sample=number(v,"sample_elapsed");if(sample<begin||sample>now||now-sample>5000)throw new IllegalArgumentException("client-stale");
        for(String key:v.keySet())if(!BASE.contains(key)&&!FLAGS.contains(key)&&!COUNTERS.contains(key)&&!key.equals("capability_callback_count"))throw new IllegalArgumentException("client-schema");
        for(String key:FLAGS)flag(v,key);
        for(String key:COUNTERS)if(number(v,key)<0)throw new IllegalArgumentException("client-counter");
        for(String key:new String[]{"enabled_mask","last_notified_mask"})if(number(v,key)<0||number(v,key)>65535)throw new IllegalArgumentException("client-mask");
        if(number(v,"generation")<1||number(v,"feature_state")<0||number(v,"feature_state")>2)throw new IllegalArgumentException("client-lifecycle");
        if(flag(v,"rebind_requested")!=requested||flag(v,"rebind_accepted")&&!requested)throw new IllegalArgumentException("client-action");
        if(flag(v,"callback_count_observed")!=v.containsKey("capability_callback_count")||v.containsKey("capability_callback_count")&&number(v,"capability_callback_count")<0)throw new IllegalArgumentException("client-callback-count");
        if(!flag(v,"registered")&&number(v,"last_notified_mask")!=0)throw new IllegalArgumentException("client-registration");
        Map<String,Object> clean=new LinkedHashMap<>(v);
        for(String key:new String[]{"nonce","pid","boot"})clean.remove(key);
        return clean;
    }
}
