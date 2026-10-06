// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Fail closed on unknown dumps. Never export or retain carrier dump contents. */
public final class ModernCarrierBaseline {
    private ModernCarrierBaseline(){}
    public static boolean hasEmptyTransientOverride(int slot)throws Exception {
        if(android.os.Process.myUid()!=0||slot<0||slot>7)throw new SecurityException("selected-root-owner-required");
        java.lang.Process child=new ProcessBuilder("dumpsys","carrier_config").redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final boolean[] overflow={false},readFailed={false};
        Thread reader=new Thread(()->{try(InputStream input=child.getInputStream()){
            byte[] block=new byte[4096];int size;
            while((size=input.read(block))>=0){if(bytes.size()+size>2097152){overflow[0]=true;break;}bytes.write(block,0,size);}
        }catch(IOException failure){readFailed[0]=true;}});reader.setDaemon(true);reader.start();
        if(!child.waitFor(10,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("carrier-baseline-timeout");}
        reader.join(1000);
        if(reader.isAlive()||overflow[0]||readFailed[0]||child.exitValue()!=0)throw new IOException("carrier-baseline-unavailable");
        return parseEmptyTransientOverride(bytes.toString("UTF-8"),slot);
    }
    static boolean parseEmptyTransientOverride(String text,int selected)throws IOException {
        int current=-1;boolean found=false,inLayer=false,nonempty=false,ended=false;
        for(String line:text.split("\\r?\\n",-1)){
            String value=line.trim();Matcher phone=Pattern.compile("Phone Id = ([0-9]+)").matcher(value);
            if(phone.matches()){
                if(inLayer)ended=true;
                current=Integer.parseInt(phone.group(1));inLayer=false;
            }else if(current==selected&&value.startsWith("mOverrideConfigs :")){
                if(found)throw new IOException("carrier-baseline-ambiguous");
                String suffix=value.substring("mOverrideConfigs :".length()).trim();
                if(!suffix.isEmpty()&&!"null".equals(suffix))throw new IOException("carrier-baseline-format-unverified");
                found=true;inLayer=true;
            }else if(inLayer){
                if(value.isEmpty()){ended=true;inLayer=false;}
                else if(value.contains(" = "))nonempty=true;
                else throw new IOException("carrier-baseline-format-unverified");
            }
        }
        if(!found||!ended)throw new IOException("carrier-baseline-unconfirmed");
        return !nonempty;
    }
}
