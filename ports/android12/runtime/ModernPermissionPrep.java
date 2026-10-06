// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
/** Fixed SMS permission exemption for the owned test emulator only. */
public final class ModernPermissionPrep {
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37||
            !"1".equals(SystemProperties.get("ro.kernel.qemu")))
            throw new SecurityException("modern-test-emulator-required");
        Looper.prepareMainLooper();
        Context context=ActivityThread.systemMain().getSystemContext();
        PackageManager pm=context.getPackageManager();
        java.util.Set<String> permissions=pm.getWhitelistedRestrictedPermissions("me.phh.ims",PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);
        boolean accepted=permissions!=null&&permissions.contains("android.permission.SEND_SMS");
        if(!accepted)accepted=pm.addWhitelistedRestrictedPermission("me.phh.ims","android.permission.SEND_SMS",
            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);
        System.out.println("sms-test-restriction-exemption="+accepted);
        System.exit(accepted?0:1);
    }
}
