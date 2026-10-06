// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.app.AppOpsManager;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.util.*;

/** Track only the default/system-fixed grants produced by selected data/IMS roles. */
final class ModernSelectedPermissions {
    private static final String[] PACKAGES={"dev.codex.vowifi.iwlan","me.phh.ims","me.phh.ims"};
    private static final String[] PERMISSIONS={"android.permission.READ_PHONE_STATE","android.permission.READ_PHONE_STATE","android.permission.RECORD_AUDIO"};
    private static final int[] COMPONENTS={1,4,4};
    private static final int DEFAULT=PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT,FIXED=PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
    private static final String[] DATA_OPS={"android:manage_ipsec_tunnels","android:fine_location"};
    private static int allowed(int index){return index==0?DEFAULT|FIXED:DEFAULT;}
    private static boolean selected(Properties record,int index){return (Integer.parseInt(record.getProperty("mask"))&COMPONENTS[index])!=0;}
    static void snapshot(Context context,Properties record)throws Exception {
        PackageManager pm=context.getPackageManager();
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
            ApplicationInfo app=pm.getApplicationInfo(PACKAGES[i],0);String prefix="role."+i+".";
            record.setProperty(prefix+"uid",Integer.toString(app.uid));record.setProperty(prefix+"apk",ModernInstallationTransaction.digest(new File(app.sourceDir)));
            record.setProperty(prefix+"flags",Integer.toString(pm.getPermissionFlags(PERMISSIONS[i],PACKAGES[i],UserHandle.SYSTEM)));
            record.setProperty(prefix+"grant",Boolean.toString(pm.checkPermission(PERMISSIONS[i],PACKAGES[i])==PackageManager.PERMISSION_GRANTED));
        }
        if(selected(record,0)) {
            AppOpsManager ops=context.getSystemService(AppOpsManager.class);int uid=Integer.parseInt(record.getProperty("role.0.uid"));
            for(int i=0;i<DATA_OPS.length;i++)record.setProperty("role.data.op."+i,Integer.toString(ops.unsafeCheckOpNoThrow(DATA_OPS[i],uid,PACKAGES[0])));
        }
    }
    private static int before(PackageManager pm,Properties record,int index)throws Exception {
        String prefix="role."+index+".";ApplicationInfo app=pm.getApplicationInfo(PACKAGES[index],0);
        if(!Integer.toString(app.uid).equals(record.getProperty(prefix+"uid"))||!ModernInstallationTransaction.digest(new File(app.sourceDir)).equals(record.getProperty(prefix+"apk"))||!Arrays.asList("true","false").contains(record.getProperty(prefix+"grant")))throw new SecurityException("selected-permission-owner-changed");
        return Integer.parseInt(record.getProperty(prefix+"flags"));
    }
    static void validate(Context context,Properties record)throws Exception {
        validateRecord(record);
        PackageManager pm=context.getPackageManager();for(int i=0;i<PACKAGES.length;i++)if(selected(record,i))before(pm,record,i);
    }
    static void validateRecord(Properties record)throws Exception {
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
            String prefix="role."+i+".";
            if(!record.getProperty(prefix+"uid","").matches("[0-9]{5}")||!record.getProperty(prefix+"apk","").matches("[0-9a-f]{64}")||!Arrays.asList("true","false").contains(record.getProperty(prefix+"grant")))throw new IOException("selected-role-record-refused");
            Integer.parseInt(record.getProperty(prefix+"flags"));
            if(record.getProperty(prefix+"observed")!=null)Integer.parseInt(record.getProperty(prefix+"observed"));
            if(record.getProperty(prefix+"apply_requested")!=null&&!"true".equals(record.getProperty(prefix+"apply_requested")))throw new IOException("selected-role-intent-refused");
        }
        if(selected(record,0))for(int i=0;i<DATA_OPS.length;i++)if(!record.getProperty("role.data.op."+i,"").matches("[0-4]"))throw new IOException("selected-data-appop-baseline-refused");
    }
    static void requested(Properties record) {
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i))record.setProperty("role."+i+".apply_requested","true");
    }
    static void observed(Context context,Properties record)throws Exception {
        PackageManager pm=context.getPackageManager();long deadline=SystemClock.elapsedRealtime()+10000;
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
            int original=before(pm,record,i),actual;
            do {
                actual=pm.getPermissionFlags(PERMISSIONS[i],PACKAGES[i],UserHandle.SYSTEM);
                if(((actual^original)&~allowed(i))!=0)throw new IOException("external-selected-permission-flags-change");
                if(i!=0||(actual&(FIXED|DEFAULT))==(FIXED|DEFAULT)||(original&(PackageManager.FLAG_PERMISSION_USER_SET|PackageManager.FLAG_PERMISSION_USER_FIXED|PackageManager.FLAG_PERMISSION_POLICY_FIXED))!=0)break;
                Thread.sleep(200);
            }while(SystemClock.elapsedRealtime()<deadline);
            // Persist the observed role flags, not a claim that a carrier registered.
            record.setProperty("role."+i+".observed",Integer.toString(actual));
            record.setProperty("role."+i+".observed.grant",Boolean.toString(pm.checkPermission(PERMISSIONS[i],PACKAGES[i])==PackageManager.PERMISSION_GRANTED));
        }
        if(selected(record,0)) {
            AppOpsManager ops=context.getSystemService(AppOpsManager.class);int uid=Integer.parseInt(record.getProperty("role.0.uid"));
            for(int i=0;i<DATA_OPS.length;i++)record.setProperty("role.data.observed.op."+i,Integer.toString(ops.unsafeCheckOpNoThrow(DATA_OPS[i],uid,PACKAGES[0])));
        }
    }
    static void validateObserved(Properties record)throws Exception {
        validateRecord(record);
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
            int original=Integer.parseInt(record.getProperty("role."+i+".flags")),actual=Integer.parseInt(record.getProperty("role."+i+".observed"));
            if(((original^actual)&~allowed(i))!=0||!Arrays.asList("true","false").contains(record.getProperty("role."+i+".observed.grant")))throw new IOException("shared-role-observation-refused");
        }
        if(selected(record,0))for(int i=0;i<DATA_OPS.length;i++) {
            int original=Integer.parseInt(record.getProperty("role.data.op."+i)),actual=Integer.parseInt(record.getProperty("role.data.observed.op."+i));
            if(actual!=original&&actual!=AppOpsManager.MODE_ALLOWED&&actual!=AppOpsManager.MODE_ERRORED)throw new IOException("shared-role-observation-refused");
        }
    }
    static Properties activeTarget(Properties original)throws Exception {
        validateObserved(original);Properties target=new Properties();target.putAll(original);
        for(int i=0;i<PACKAGES.length;i++)if(selected(original,i)) {
            target.setProperty("role."+i+".flags",original.getProperty("role."+i+".observed"));
            target.setProperty("role."+i+".grant",original.getProperty("role."+i+".observed.grant"));
        }
        if(selected(original,0))for(int i=0;i<DATA_OPS.length;i++)target.setProperty("role.data.op."+i,original.getProperty("role.data.observed.op."+i));
        return target;
    }
    static boolean matches(Context context,Properties record)throws Exception {
        validate(context,record);PackageManager pm=context.getPackageManager();
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
            if(pm.getPermissionFlags(PERMISSIONS[i],PACKAGES[i],UserHandle.SYSTEM)!=Integer.parseInt(record.getProperty("role."+i+".flags"))||
                (pm.checkPermission(PERMISSIONS[i],PACKAGES[i])==PackageManager.PERMISSION_GRANTED)!=Boolean.parseBoolean(record.getProperty("role."+i+".grant")))return false;
        }
        if(selected(record,0)) {
            AppOpsManager ops=context.getSystemService(AppOpsManager.class);int uid=Integer.parseInt(record.getProperty("role.0.uid"));
            for(int i=0;i<DATA_OPS.length;i++)if(ops.unsafeCheckOpNoThrow(DATA_OPS[i],uid,PACKAGES[0])!=Integer.parseInt(record.getProperty("role.data.op."+i)))return false;
        }
        return true;
    }
    static void restore(Context context,Properties record)throws Exception {
        validateRecord(record);
        PackageManager pm=context.getPackageManager();
        int[] originals=new int[PACKAGES.length];
        for(int i=0;i<PACKAGES.length;i++)if(selected(record,i))originals[i]=before(pm,record,i);
        // Carrier reload and the framework's role-permission callbacks are
        // asynchronous. One matching read can precede a delayed native revoke.
        // Require an unchanged full policy interval; never ignore foreign flags.
        long deadline=SystemClock.elapsedRealtime()+20000,stableSince=-1;
        do {
            boolean equal=true;
            for(int i=0;i<PACKAGES.length;i++)if(selected(record,i)) {
                int original=originals[i],mask=allowed(i),actual=pm.getPermissionFlags(PERMISSIONS[i],PACKAGES[i],UserHandle.SYSTEM);
                if(((actual^original)&~mask)!=0)throw new IOException("external-selected-permission-flags-change");
                if(i==0&&(original&DEFAULT)==0&&(actual&DEFAULT)!=0) {equal=false;continue;}
                boolean wanted=Boolean.parseBoolean(record.getProperty("role."+i+".grant"));
                boolean granted=pm.checkPermission(PERMISSIONS[i],PACKAGES[i])==PackageManager.PERMISSION_GRANTED;
                if(!granted&&wanted&&record.getProperty("role."+i+".observed")==null&&!"true".equals(record.getProperty("role."+i+".apply_requested"))&&((actual^original)&mask)==0)throw new IOException("untracked-selected-permission-revoke");
                if(actual!=original||granted!=wanted) {
                    equal=false;ModernRolePermissionBroker.restore(i,Integer.parseInt(record.getProperty("role."+i+".uid")),record.getProperty("role."+i+".apk"),original,wanted);
                }
            }
            if(selected(record,0)) {
                AppOpsManager ops=context.getSystemService(AppOpsManager.class);int uid=Integer.parseInt(record.getProperty("role.0.uid"));
                boolean requested="true".equals(record.getProperty("role.0.apply_requested"));
                for(int i=0;i<DATA_OPS.length;i++) {
                    int original=Integer.parseInt(record.getProperty("role.data.op."+i)),actual=ops.unsafeCheckOpNoThrow(DATA_OPS[i],uid,PACKAGES[0]);
                    if(actual!=original) {
                        if(!requested||(actual!=AppOpsManager.MODE_ALLOWED&&actual!=AppOpsManager.MODE_ERRORED))throw new IOException("external-selected-data-appop-change");
                        equal=false;ops.setMode(DATA_OPS[i],uid,PACKAGES[0],original);
                        if(ops.unsafeCheckOpNoThrow(DATA_OPS[i],uid,PACKAGES[0])!=original)throw new IOException("selected-data-appop-restore-unconfirmed");
                    }
                }
            }
            long now=SystemClock.elapsedRealtime();
            if(equal){if(stableSince<0)stableSince=now;if(now-stableSince>=1500)return;}else stableSince=-1;
            Thread.sleep(200);
        }while(SystemClock.elapsedRealtime()<deadline);
        throw new IOException("selected-permission-restore-not-settled");
    }
}
