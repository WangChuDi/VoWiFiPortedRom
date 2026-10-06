// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Fixed, root-only observations; raw contents stay in this process. */
public final class ModernSystemObservation {
    private ModernSystemObservation(){}
    public static String read(String service)throws Exception {
        if(android.os.Process.myUid()!=0||!("carrier_config".equals(service)||"telephony.registry".equals(service)))throw new SecurityException("fixed-root-observation-required");
        return run(new String[]{"dumpsys",service});
    }
    static String telephonyDebug()throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("fixed-root-observation-required");
        return run(new String[]{"dumpsys","activity","service","com.android.phone/.TelephonyDebugService"});
    }
    private static String run(String[] command)throws Exception {
        java.lang.Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final boolean[] overflow={false},readFailed={false};
        Thread reader=new Thread(()->{try(InputStream input=child.getInputStream()){
            byte[] block=new byte[4096];int size;
            while((size=input.read(block))>=0){if(bytes.size()+size>2097152){overflow[0]=true;break;}bytes.write(block,0,size);}
        }catch(IOException failure){readFailed[0]=true;}});reader.setDaemon(true);reader.start();
        if(!child.waitFor(10,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("system-observation-timeout");}
        reader.join(1000);
        if(reader.isAlive()||overflow[0]||readFailed[0]||child.exitValue()!=0)throw new IOException("system-observation-unavailable");
        return bytes.toString("UTF-8");
    }
}
