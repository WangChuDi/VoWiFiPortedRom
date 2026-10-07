// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Read ISms as the registered IMS application's own UID; no root or SMS traffic. */
public final class NativeSmsStatusReader {
    private static Boolean calibrated;
    public static boolean ownUid(){return android.os.Process.myUid()>=10000&&android.os.Binder.getCallingUid()==android.os.Process.myUid();}
    public static synchronized boolean profileEligible(){
        if(calibrated!=null)return calibrated;
        if(Build.VERSION.SDK_INT!=30||!"raphael".equals(Build.DEVICE)){calibrated=false;return false;}
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(FileInputStream input=new FileInputStream("/system/framework/telephony-common.jar")){
                byte[] block=new byte[16384];for(int n;(n=input.read(block))>=0;)if(n>0)digest.update(block,0,n);
            }
            StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            calibrated="6cc255f3cd8fe8f11191a1d2ec0bfddfdccf31d3851cdb3cd9282564871c3f74".equals(value.toString());
        }catch(Exception unavailable){calibrated=false;}
        return calibrated;
    }
    public static Boolean observe(int subscription){
        if(subscription<0||!ownUid()||!profileEligible())return null;
        return read(subscription);
    }
    /** Modern observation is read-only and never establishes recovery eligibility. */
    public static boolean diagnosticProfileEligible(){
        return Build.VERSION.SDK_INT>=31&&Build.VERSION.SDK_INT<=37 || Build.VERSION.SDK_INT==30&&profileEligible();
    }
    public static Boolean observeForDiagnostics(int subscription){
        if(subscription<0||!ownUid()||!diagnosticProfileEligible())return null;
        return read(subscription);
    }
    private static Boolean read(int subscription){
        try{
            IBinder binder=ServiceManager.getService("isms");if(binder==null||!binder.isBinderAlive())return null;
            Class<?> type=Class.forName("com.android.internal.telephony.ISms");
            Object service=Class.forName(type.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
            Object value=type.getMethod("isImsSmsSupportedForSubscriber",int.class).invoke(service,subscription);
            return value instanceof Boolean?(Boolean)value:null;
        }catch(Exception|LinkageError unavailable){return null;}
    }
}
