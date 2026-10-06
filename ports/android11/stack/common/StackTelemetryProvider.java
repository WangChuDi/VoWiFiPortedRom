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

/** Same-process, root-only observation. No mutations, networking or SMS reads. */
public final class StackTelemetryProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=0)throw new SecurityException("status-root-required");
        if(!"status".equals(method)||arg==null||arg.length()>96)throw new IllegalArgumentException("status-request");
        String[] parts=arg.split(":",-1);
        if(parts.length!=4||!parts[1].matches("[0-7]")||!parts[2].matches("[0-9]{1,10}")||!parts[3].matches("[0-9a-f]{16}"))
            throw new IllegalArgumentException("status-selection");
        String channel=parts[0],pkg=getContext().getPackageName();
        if(!pkg.equals(channel.equals("ims")?"me.phh.ims":"dev.codex.vowifi."+channel)||
           !(channel.equals("iwlan")||channel.equals("qns")||channel.equals("ims")))throw new IllegalArgumentException("status-channel");
        int slot=Integer.parseInt(parts[1]),sub=Integer.parseInt(parts[2]);
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
