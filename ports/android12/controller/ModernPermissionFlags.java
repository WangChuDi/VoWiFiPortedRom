// SPDX-License-Identifier: GPL-2.0
import android.content.pm.PackageManager;
import java.util.Properties;

/** PermissionController owns sensitivity metadata; controllers never write it. */
final class ModernPermissionFlags {
    static final int INFORMATIONAL=PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED|
        PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;
    static boolean samePolicy(int actual,int original){return ((actual^original)&~INFORMATIONAL)==0;}
    static boolean foreignChange(int actual,int original,int roleOwned){return ((actual^original)&~(INFORMATIONAL|roleOwned))!=0;}
    /** Fixture comparison only: every non-flag field remains exact, including grants. */
    static boolean sameRecordedPolicy(Properties original,Properties actual){
        if(!original.keySet().equals(actual.keySet()))return false;
        for(String key:original.stringPropertyNames()){
            String before=original.getProperty(key),after=actual.getProperty(key);
            if(key.matches("flags\\.[012]\\.(READ_PHONE_STATE|RECORD_AUDIO|SEND_SMS)|role\\.[012]\\.flags")){
                if(!samePolicy(Integer.parseInt(after),Integer.parseInt(before)))return false;
            }else if(!before.equals(after))return false;
        }
        return true;
    }
}
