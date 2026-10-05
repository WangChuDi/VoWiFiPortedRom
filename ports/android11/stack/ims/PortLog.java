// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

/** Suppress upstream protocol/body/key logs. Explicit API30 event logs are separate. */
public final class PortLog {
    public static int d(String tag,String message){return 0;}
    public static int d(String tag,String message,Throwable error){return 0;}
    public static int i(String tag,String message){return 0;}
    public static int w(String tag,String message){return 0;}
    public static int w(String tag,String message,Throwable error){return 0;}
    public static int e(String tag,String message){return 0;}
    public static int e(String tag,String message,Throwable error){
        return android.util.Log.e(tag,"IMS error: "+error.getClass().getSimpleName());
    }
}
