// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** RFC3264 section 8: preserve the origin and advance its version for changed SDP. */
public final class SdpSessionVersion {
    public static byte[] changedOffer(byte[] previous,byte[] changed){
        if(previous==null||changed==null||previous.length>65536||changed.length>65536)throw invalid();
        if(Arrays.equals(previous,changed))return previous.clone();
        List<String> old=lines(previous),next=lines(changed);String origin=null;int position=-1;
        for(String line:old)if(line.startsWith("o=")){if(origin!=null)throw invalid();origin=line;}
        for(int i=0;i<next.size();i++)if(next.get(i).startsWith("o=")){if(position!=-1)throw invalid();position=i;}
        if(origin==null||position<0||!origin.equals(next.get(position)))throw invalid();
        String[] fields=origin.substring(2).split(" ",-1);
        if(fields.length!=6||!fields[1].matches("[0-9]{1,19}")||!fields[2].matches("[0-9]{1,19}"))throw invalid();
        long version;try{version=Long.parseLong(fields[2]);Long.parseLong(fields[1]);}catch(NumberFormatException e){throw invalid();}
        if(version==Long.MAX_VALUE)throw invalid();fields[2]=Long.toString(version+1);
        next.set(position,"o="+String.join(" ",fields));return (String.join("\r\n",next)+"\r\n").getBytes(StandardCharsets.UTF_8);
    }
    private static List<String> lines(byte[] bytes){return new ArrayList<>(Arrays.asList(new String(bytes,StandardCharsets.UTF_8).split("[\\r\\n]+")));}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("sdp-origin-invalid");}
    private SdpSessionVersion(){}
}
