// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import org.json.JSONObject;
import java.util.Map;

/** Same-process root observations and an explicit guarded IMS client-rebind. */
public final class StackTelemetryProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=0)throw new SecurityException("status-root-required");
        boolean clients="capabilities".equals(method)||"client-rebind".equals(method);
        boolean nativeSms="native-sms".equals(method);
        boolean runtimeAbi="runtime-abi".equals(method);
        if((!"status".equals(method)&&!clients&&!nativeSms&&!runtimeAbi)||arg==null||arg.length()>96)throw new IllegalArgumentException("status-request");
        String[] parts=arg.split(":",-1);
        if(parts.length!=4||!parts[1].matches("[0-7]")||!parts[2].matches("[0-9]{1,10}")||!parts[3].matches("[0-9a-f]{16}"))
            throw new IllegalArgumentException("status-selection");
        String channel=parts[0],pkg=getContext().getPackageName();
        if(!pkg.equals(channel.equals("ims")?"me.phh.ims":"dev.codex.vowifi."+channel)||
           !(channel.equals("iwlan")||channel.equals("qns")||channel.equals("ims")))throw new IllegalArgumentException("status-channel");
        int slot=Integer.parseInt(parts[1]),sub=Integer.parseInt(parts[2]);
        if(runtimeAbi){
            try{
                SubscriptionInfo actual=StackProfile.selectedSubscription(getContext(),slot);
                boolean authorized=actual!=null&&actual.getSubscriptionId()==sub;
                Map<String,Object> before=authorized?StackTelemetry.snapshot(channel,slot,sub):null;
                if(before!=null&&Boolean.TRUE.equals(before.get("retired")))before=null;
                JSONObject value=new JSONObject().put("schema",1).put("channel",channel).put("slot",slot).put("sub",sub).put("nonce",parts[3])
                    .put("pid",android.os.Process.myPid()).put("boot",Settings.Global.getInt(getContext().getContentResolver(),Settings.Global.BOOT_COUNT,-1))
                    .put("scope","active_service_process_lookup").put("read_only",true).put("initialization_performed",false).put("calls_verified",false)
                    .put("authorized",authorized).put("observed",before!=null).put("catalogue",1).put("sdk",Build.VERSION.SDK_INT);
                if(before!=null){
                    Map<String,String> checks=ServiceAbiCatalog.inspect(channel,StackTelemetryProvider.class.getClassLoader());
                    SubscriptionInfo afterSub=StackProfile.selectedSubscription(getContext(),slot);
                    Map<String,Object> after=StackTelemetry.snapshot(channel,slot,sub);
                    if(afterSub==null||afterSub.getSubscriptionId()!=sub||after==null||Boolean.TRUE.equals(after.get("retired"))||!before.get("generation").equals(after.get("generation")))throw new IllegalStateException("abi-owner-changed");
                    int[] counts=ServiceAbiCatalog.counts(checks);
                    value.put("generation",after.get("generation")).put("checks",new JSONObject(checks)).put("total",checks.size()).put("visible",counts[0]).put("missing",counts[1]).put("inaccessible",counts[2]).put("linkage_errors",counts[3]);
                }
                value.put("sample_elapsed",SystemClock.elapsedRealtime());Bundle result=new Bundle();result.putString("snapshot",value.toString());return result;
            }catch(Exception unavailable){throw new IllegalStateException("abi-query-unavailable");}
        }
        if(nativeSms){
            if(!"ims".equals(channel)||!"me.phh.ims".equals(pkg))throw new IllegalArgumentException("native-query-channel");
            try{
                Object observed=Class.forName("me.phh.ims.NativeSmsDiagnostics").getMethod("query",Context.class,int.class,int.class).invoke(null,getContext(),slot,sub);
                JSONObject value=new JSONObject((Map)observed);
                value.put("schema",1).put("channel","ims-native-sms").put("slot",slot).put("sub",sub).put("nonce",parts[3]);
                value.put("pid",android.os.Process.myPid()).put("boot",Settings.Global.getInt(getContext().getContentResolver(),Settings.Global.BOOT_COUNT,-1));
                value.put("sample_elapsed",SystemClock.elapsedRealtime());
                Bundle result=new Bundle();result.putString("snapshot",value.toString());return result;
            }catch(Exception unavailable){throw new IllegalStateException("native-query-unavailable");}
        }
        if(clients){
            if(!"ims".equals(channel)||!"me.phh.ims".equals(pkg))throw new IllegalArgumentException("capability-channel");
            try{
                Object observed=Class.forName("me.phh.ims.PhhImsService").getMethod("clientCapabilityStatus",int.class,int.class,boolean.class)
                    .invoke(null,slot,sub,"client-rebind".equals(method));
                JSONObject value=new JSONObject((Map)observed);
                value.put("schema",1).put("channel",channel).put("slot",slot).put("sub",sub).put("nonce",parts[3]);
                value.put("pid",android.os.Process.myPid());
                value.put("boot",Settings.Global.getInt(getContext().getContentResolver(),Settings.Global.BOOT_COUNT,-1));
                value.put("sample_elapsed",SystemClock.elapsedRealtime());
                Bundle result=new Bundle();result.putString("snapshot",value.toString());return result;
            }catch(Exception unavailable){throw new IllegalStateException("capability-request-unavailable");}
        }
        JSONObject json=new JSONObject();
        try{
            json.put("schema",1).put("channel",channel).put("slot",slot).put("sub",sub).put("nonce",parts[3]);
            json.put("pid",android.os.Process.myPid());
            json.put("boot",Settings.Global.getInt(getContext().getContentResolver(),Settings.Global.BOOT_COUNT,-1));
            SubscriptionInfo actual=StackProfile.selectedSubscription(getContext(),slot);
            boolean authorized=actual!=null&&actual.getSubscriptionId()==sub;
            Map<String,Object> status=authorized?StackTelemetry.snapshot(channel,slot,sub):null;
            json.put("authorized",authorized).put("observed",status!=null);
            if(status!=null)for(Map.Entry<String,Object> e:status.entrySet())json.put(e.getKey(),e.getValue());
            json.put("sample_elapsed",SystemClock.elapsedRealtime());
            Bundle result=new Bundle();result.putString("snapshot",json.toString());return result;
        }catch(Exception failure){throw new IllegalStateException("status-unavailable");}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new SecurityException("status-call-only");}
    @Override public String getType(Uri u){return "application/json";}
    @Override public Uri insert(Uri u,ContentValues v){throw new SecurityException("status-read-only");}
    @Override public int delete(Uri u,String s,String[] a){throw new SecurityException("status-read-only");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException("status-read-only");}
}
