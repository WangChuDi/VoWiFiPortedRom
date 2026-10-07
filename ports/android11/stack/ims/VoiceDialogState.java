// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import java.util.*;
import java.util.regex.*;

/** Outgoing dialog routing and local sequence ownership; never log this state. */
public final class VoiceDialogState {
    public static final class Request {
        public final String target;
        public final Map<String,List<String>> headers;
        private Request(String target,Map<String,List<String>> headers){this.target=target;this.headers=headers;}
    }
    private final Map<String,List<String>> original;
    private final String callId,from;
    private final long inviteSequence;
    private long sequence;
    private String target,to;
    private List<String> routes;
    private boolean confirmed;
    public VoiceDialogState(Map<String,List<String>> invite){
        original=copy(invite);callId=one(invite,"call-id");from=one(invite,"from");
        String seq=one(invite,"cseq");
        if(!seq.matches("[0-9]{1,10} INVITE"))throw refused();
        inviteSequence=Long.parseLong(seq.substring(0,seq.indexOf(' ')));
        if(inviteSequence>=2147483647L)throw refused();sequence=inviteSequence;
    }
    private static IllegalStateException refused(){return new IllegalStateException("voice-dialog-unavailable");}
    private static String one(Map<String,List<String>> h,String k){
        List<String> v=h.get(k);if(v==null||v.size()!=1||v.get(0)==null||v.get(0).isEmpty()||v.get(0).contains("\r")||v.get(0).contains("\n"))throw refused();return v.get(0);
    }
    private static Map<String,List<String>> copy(Map<String,List<String>> source){
        Map<String,List<String>> value=new HashMap<>();
        for(Map.Entry<String,List<String>> e:source.entrySet())value.put(e.getKey(),new ArrayList<>(e.getValue()));return value;
    }
    private static String uri(String value){
        if(value.contains("\r")||value.contains("\n"))throw refused();
        int left=value.indexOf('<'),right=value.indexOf('>');
        String v=left>=0&&right>left?value.substring(left+1,right):value.trim();
        if(!(v.startsWith("sip:")||v.startsWith("sips:"))||v.indexOf(' ')>=0||v.length()>4096)throw refused();return v;
    }
    private static List<String> routeValues(Map<String,List<String>> response){
        List<String> result=new ArrayList<>(),raw=response.get("record-route");
        if(raw!=null)for(String value:raw){
            // Split SIP header lists outside quoted strings and name-address brackets.
            if(value==null||value.contains("\r")||value.contains("\n"))throw refused();
            int start=0,depth=0;boolean quoted=false,escaped=false;
            for(int i=0;i<=value.length();i++){
                char c=i==value.length()?',':value.charAt(i);
                if(escaped){escaped=false;continue;}
                if(quoted&&c=='\\'){escaped=true;continue;}
                if(c=='"'){quoted=!quoted;continue;}
                if(!quoted){if(c=='<')depth++;else if(c=='>')depth--;}
                if(c==','&&!quoted&&depth==0){String item=value.substring(start,i).trim();uri(item);result.add(item);start=i+1;}
                if(depth<0)throw refused();
            }
            if(quoted||depth!=0)throw refused();
        }
        if(result.size()>16)throw refused();Collections.reverse(result);return result;
    }
    private void learn(Map<String,List<String>> response,boolean finalResponse){
        if(!one(response,"call-id").equals(callId)||!one(response,"from").equals(from))throw refused();
        String nextTo=one(response,"to");
        if(!nextTo.contains(";tag=")||(to!=null&&!to.equals(nextTo)))throw refused();
        String nextTarget=uri(one(response,"contact"));
        if(routes==null||finalResponse){routes=routeValues(response);target=nextTarget;}
        to=nextTo;if(finalResponse)confirmed=true;
    }
    public synchronized Request prepare(String method,Map<String,List<String>> response){
        if(!Arrays.asList("PRACK","UPDATE","ACK","BYE").contains(method))throw refused();
        if(response!=null)learn(response,"ACK".equals(method));
        if(target==null||to==null||routes==null||("BYE".equals(method)&&!confirmed))throw refused();
        long n=inviteSequence;
        if(!"ACK".equals(method)){if(sequence>=2147483647L)throw refused();n=++sequence;}
        Map<String,List<String>> h=copy(original);
        for(String key:Arrays.asList("route","cseq","content-type","content-length","expires","require"))h.remove(key);
        h.put("to",Arrays.asList(to));h.put("cseq",Arrays.asList(n+" "+method));
        List<String> via=h.get("via");
        if(via!=null){List<String> fresh=new ArrayList<>();for(String value:via)fresh.add(value.replaceAll("(?i);branch=[^;\\s]+",""));h.put("via",fresh);}
        List<String> selected=new ArrayList<>(routes);String requestTarget=target;
        if(!selected.isEmpty()&&!Pattern.compile("(?i)(?:;lr)(?:[;=>]|$)").matcher(uri(selected.get(0))).find()){
            requestTarget=uri(selected.remove(0));selected.add("<"+target+">");
        }
        if(!selected.isEmpty())h.put("route",selected);
        return new Request(requestTarget,h);
    }
}
