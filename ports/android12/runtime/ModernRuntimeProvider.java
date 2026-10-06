// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.runtime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import dev.codex.vowifi.tool.RuntimeAbiProbe;
import org.json.JSONObject;

/** Root-only lookup in the service APK's declared-library class loader. */
public final class ModernRuntimeProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=0)throw new SecurityException("runtime-root-required");
        if(!("abi".equals(method)||"bindings".equals(method)||"guard".equals(method))||arg==null||!arg.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("runtime-request");
        try {
            JSONObject result;
            if("abi".equals(method)) {
                result=new JSONObject(RuntimeAbiProbe.inspect(Build.VERSION.SDK_INT,ModernRuntimeProvider.class.getClassLoader()));
                result.put("scope","service_app_declared_library_lookup");
            }else if("guard".equals(method)) {
                long identity=Binder.clearCallingIdentity();
                try {
                    boolean denied=false;
                    try {getContext().getContentResolver().call(Uri.parse("content://dev.codex.vowifi.iwlan.runtime"),"abi",arg,null);}
                    catch(SecurityException failure){denied="runtime-root-required".equals(failure.getMessage());}
                    result=new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT);
                    result.put("scope","privileged_nonroot_guard_check").put("denied",denied);
                    result.put("tested_uid",android.os.Process.myUid());
                    result.put("caller_permission_granted",getContext().checkSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")==0);
                }finally {Binder.restoreCallingIdentity(identity);}
            }else {
                long identity=Binder.clearCallingIdentity();
                try {
                    result=new JSONObject().put("schema",1).put("sdk",Build.VERSION.SDK_INT);
                    result.put("scope","privileged_app_explicit_binding");
                    result.put("bindings",ModernServiceBindings.inspect(getContext()));
                    result.put("telephony_provider_selection_verified",false);
                } finally {Binder.restoreCallingIdentity(identity);}
            }
            result.put("package",getContext().getPackageName());
            result.put("pid",android.os.Process.myPid());
            result.put("nonce",arg);
            result.put("sample_elapsed",SystemClock.elapsedRealtime());
            Bundle bundle=new Bundle();bundle.putString("snapshot",result.toString());return bundle;
        }catch(Exception failure){throw new IllegalStateException("runtime-unavailable");}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new SecurityException("runtime-call-only");}
    @Override public String getType(Uri u){return "application/json";}
    @Override public Uri insert(Uri u,ContentValues v){throw new SecurityException("runtime-read-only");}
    @Override public int delete(Uri u,String s,String[] a){throw new SecurityException("runtime-read-only");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException("runtime-read-only");}
}
