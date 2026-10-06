// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.util.regex.*;

/** Fail closed on unknown dumps. Never export or retain carrier dump contents. */
public final class ModernCarrierBaseline {
    private ModernCarrierBaseline(){}
    public static boolean hasEmptyTransientOverride(int slot)throws Exception {
        if(android.os.Process.myUid()!=0||slot<0||slot>7)throw new SecurityException("selected-root-owner-required");
        return parseEmptyTransientOverride(ModernSystemObservation.read("carrier_config"),slot);
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
