// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Fixed metadata only: never serialize SIP headers, identities, SDP or addresses. */
public final class VoiceResponseObservation {
    private static final Set<String> METHODS=new HashSet<>(Arrays.asList("INVITE","PRACK","UPDATE","ACK","CANCEL","BYE"));
    private static final Set<String> CODECS=new HashSet<>(Arrays.asList("AMR","AMR-WB","PCMA","PCMU","EVS","TELEPHONE-EVENT"));
    private static String first(Map<String,List<String>> headers,String name){
        List<String> values=headers.get(name);
        return values==null||values.isEmpty()||values.get(0)==null?"":values.get(0);
    }
    public static String describe(int status,Map<String,List<String>> headers,byte[] body){
        String method="UNKNOWN";
        Matcher seq=Pattern.compile("^[0-9]{1,10}[ \\t]+([A-Z]+)[ \\t]*$").matcher(first(headers,"cseq"));
        if(seq.matches()&&METHODS.contains(seq.group(1)))method=seq.group(1);
        String warning="none";
        Matcher warn=Pattern.compile("^([3][0-9]{2})[ \\t]").matcher(first(headers,"warning"));
        if(warn.find())warning=warn.group(1);
        String cause="none";
        Matcher why=Pattern.compile("(?i)^Q\\.850[ \\t]*;[ \\t]*cause[ \\t]*=[ \\t]*([0-9]{1,3})(?:[ \\t]*;|[ \\t]*$)").matcher(first(headers,"reason"));
        if(why.find()){int n=Integer.parseInt(why.group(1));if(n<=127)cause=Integer.toString(n);}
        boolean sdp=first(headers,"content-type").equalsIgnoreCase("application/sdp");
        boolean required=false;
        List<String> requirements=headers.get("require");
        if(requirements!=null)for(String item:requirements)if(item!=null)for(String token:item.split(","))if(token.trim().equalsIgnoreCase("precondition"))required=true;
        Set<String> codecs=new TreeSet<>();Set<String> qos=new TreeSet<>();
        if(sdp&&body!=null&&body.length<=65536){
            for(String line:new String(body,StandardCharsets.UTF_8).split("[\\r\\n]+")){
                Matcher codec=Pattern.compile("(?i)^a=rtpmap:([0-9]{1,3}) ([A-Z-]+)/([0-9]{1,6})(?:/([0-9]{1,2}))?$").matcher(line);
                if(codec.matches()&&Integer.parseInt(codec.group(1))<=127&&CODECS.contains(codec.group(2).toUpperCase(Locale.ROOT)))codecs.add(codec.group(2).toUpperCase(Locale.ROOT));
                Matcher pre=Pattern.compile("^a=(curr|des|conf):qos (?:(mandatory|optional) )?(local|remote|e2e) (none|send|recv|sendrecv)$").matcher(line);
                if(pre.matches())qos.add(pre.group(1)+"_"+pre.group(3)+"_"+(pre.group(2)==null?"":pre.group(2)+"_")+pre.group(4));
            }
        }
        boolean reliable=first(headers,"rseq").matches("[0-9]{1,10}");
        boolean contact=!first(headers,"contact").isEmpty();
        List<String> routes=headers.get("record-route");int routeCount=routes==null?0:Math.min(routes.size(),16);
        return "voice-sip-response code="+(status>=100&&status<=699?status:0)+" method="+method+" warning="+warning+" cause="+cause+" sdp="+sdp+" precondition="+required+" codecs="+(codecs.isEmpty()?"none":String.join(",",codecs))+" qos="+(qos.isEmpty()?"none":String.join(",",qos))+" reliable="+reliable+" contact="+contact+" routes="+routeCount;
    }
    private VoiceResponseObservation(){}
}
