// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import java.io.IOException;

/** Only the observed shared phone process may be restarted, and only while idle. */
final class ModernPhoneRefresh {
    static int boot(Context context)throws Exception {
        String stored=ModernRootSettings.get(Settings.Global.BOOT_COUNT);int value=stored==null?-1:Integer.parseInt(stored);
        if(value<0)throw new IOException("known-boot-count-required");return value;
    }
    static boolean refresh(Context context,int previousPid,int previousBoot,int slot,Boolean legacy)throws Exception {
        ModernPhoneIdle.requireIdle(context);boolean killed=false;
        if(boot(context)==previousBoot&&ModernSystemObservation.phonePid()==previousPid){
            ModernPhoneIdle.requireIdle(context);
            if(ModernSystemObservation.phonePid()!=previousPid)throw new IOException("phone-process-changed-before-refresh");
            android.os.Process.sendSignal(previousPid,9);killed=true;
        }
        long deadline=SystemClock.elapsedRealtime()+35000;
        while(SystemClock.elapsedRealtime()<deadline){
            try{
                boolean rebuilt=boot(context)!=previousBoot||ModernSystemObservation.phonePid()!=previousPid;
                ModernIwlanObservation.Result mode=ModernIwlanObservation.read(ModernSystemObservation.telephonyDebug(),slot);
                boolean expected=legacy==null?(Build.VERSION.SDK_INT>=34&&mode.legacy==null):legacy.equals(mode.legacy);
                if(rebuilt&&expected&&!(Boolean.FALSE.equals(legacy)&&Boolean.FALSE.equals(mode.cachedWlan)))return killed;
            }catch(Exception pending){}
            Thread.sleep(500);
        }
        throw new IOException("phone-cache-refresh-unconfirmed");
    }
}
