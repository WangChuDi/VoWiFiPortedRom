// SPDX-License-Identifier: GPL-2.0
import android.content.pm.PackageManager;
import java.util.Properties;

public final class ModernPermissionFlagsTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("permission contract "+checks);}
    public static void main(String[] ignored){
        check(ModernPermissionFlags.INFORMATIONAL==768);
        check(ModernPermissionFlags.samePolicy(256,0));
        check(ModernPermissionFlags.samePolicy(12544,12288));
        check(ModernPermissionFlags.samePolicy(768,0));
        check(ModernPermissionFlags.samePolicy(0,768));
        check(!ModernPermissionFlags.samePolicy(48,0));
        int role=PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT|PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        check(!ModernPermissionFlags.foreignChange(48|256,0,role));
        for(int bit:new int[]{PackageManager.FLAG_PERMISSION_USER_SET,PackageManager.FLAG_PERMISSION_USER_FIXED,PackageManager.FLAG_PERMISSION_POLICY_FIXED,PackageManager.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT,PackageManager.FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT,PackageManager.FLAG_PERMISSION_ONE_TIME,1<<30}){
            check(!ModernPermissionFlags.samePolicy(256|bit,0));
            check(ModernPermissionFlags.foreignChange(256|bit,0,role));
        }
        check(ModernPermissionFlags.foreignChange(PackageManager.FLAG_PERMISSION_SYSTEM_FIXED,0,PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT));
        Properties before=new Properties();before.setProperty("flags.2.SEND_SMS","12288");before.setProperty("grant.2.SEND_SMS","true");before.setProperty("apk.2","unchanged");
        Properties after=new Properties();after.putAll(before);after.setProperty("flags.2.SEND_SMS","12544");
        check(ModernPermissionFlags.sameRecordedPolicy(before,after));
        check("12288".equals(before.getProperty("flags.2.SEND_SMS"))&&"12544".equals(after.getProperty("flags.2.SEND_SMS")));
        after.setProperty("grant.2.SEND_SMS","false");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        after.putAll(before);after.setProperty("flags.2.SEND_SMS","0");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        after.putAll(before);after.setProperty("flags.2.UNKNOWN_PERMISSION","256");before.setProperty("flags.2.UNKNOWN_PERMISSION","0");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        after.remove("apk.2");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        before.clear();after.clear();before.setProperty("role.0.flags","48");before.setProperty("role.0.grant","true");after.putAll(before);after.setProperty("role.0.flags","304");check(ModernPermissionFlags.sameRecordedPolicy(before,after));
        after.setProperty("role.0.grant","false");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        after.putAll(before);after.setProperty("role.0.flags","256");check(!ModernPermissionFlags.sameRecordedPolicy(before,after));
        System.out.println("Modern permission metadata contracts passed: "+checks);
    }
}
