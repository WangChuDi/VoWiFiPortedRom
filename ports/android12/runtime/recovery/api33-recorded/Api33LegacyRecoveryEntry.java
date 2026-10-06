// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.nio.channels.*;
import java.util.*;
import org.json.*;

/** Wrap the retained original installation helper with current global exclusion. */
public final class Api33LegacyRecoveryEntry {
    public static void main(String[] args){
        try{
            Api33RecordedRecoveryAudit.profile();if(args.length!=2||!Arrays.asList("restore","cleanup").contains(args[0])||!args[1].matches("[0-9a-f]{32}"))throw new SecurityException("fixed-original-recovery-required");
            File lockPath=new File(Api33RecordedRecoveryAudit.CARRIER,"controller.lock");Api33RecordedRecoveryAudit.canonical(lockPath);if(!lockPath.isFile())throw new IOException("existing-global-lock-required");
            try(RandomAccessFile file=new RandomAccessFile(lockPath,"rw");FileLock lock=file.getChannel().tryLock()){
                if(lock==null)throw new IOException("global-controller-busy");Api33RecordedRecoveryAudit.inspect();File root=new File(Api33RecordedRecoveryAudit.ROOT,args[1]);Api33RecordedRecoveryAudit.canonical(root);File baseline=new File(root,"state/baseline.properties"),outer=new File(root,"outer.properties");
                if(!Api33RecordedRecoveryAudit.OUTERS.contains(Api33RecordedRecoveryAudit.digest(outer)))throw new IOException("original-outer-required");
                if("restore".equals(args[0])&&!Api33RecordedRecoveryAudit.BASELINES.contains(Api33RecordedRecoveryAudit.digest(baseline)))throw new IOException("original-baseline-required");
                if("cleanup".equals(args[0])&&!"RESTORED".equals(Api33RecordedRecoveryAudit.read(baseline).getProperty("phase")))throw new IOException("installation-restore-required-first");
                // This main exits after confirming the old fixture; OS releases both locks.
                Class.forName("ModernInstallationEmulatorTrial").getMethod("main",String[].class).invoke(null,(Object)args);
                throw new IOException("legacy-entry-returned-unexpectedly");
            }
        }catch(Throwable error){JSONObject result=new JSONObject();try{result.put("error",error.getClass().getSimpleName()).put("reason","guarded-original-recovery-refused");}catch(Exception ignored){}System.out.println(result);System.exit(1);}
    }
}
