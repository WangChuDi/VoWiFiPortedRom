// SPDX-License-Identifier: GPL-2.0
import me.phh.ims.VoiceResponseObservation;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Synthetic private-looking fields must never leave the fixed metadata observer. */
public final class VoiceMetadataContract {
    static int checks=0;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("metadata-contract");}
    static Map<String,List<String>> headers(String seq){
        Map<String,List<String>> h=new HashMap<>();
        h.put("cseq",Arrays.asList(seq));h.put("content-type",Arrays.asList("application/sdp"));return h;
    }
    public static void main(String[] args){
        Map<String,List<String>> h=headers("42 INVITE");
        h.put("warning",Arrays.asList("399 private.invalid do-not-export"));
        h.put("reason",Arrays.asList("Q.850 ;cause=65 ;text=do-not-export"));
        h.put("from",Arrays.asList("subscriber-do-not-export"));
        h.put("require",Arrays.asList("100rel, precondition"));
        String a=VoiceResponseObservation.describe(488,h,("c=IN IP4 192.0.2.99\n"+
            "a=rtpmap:97 AMR/8000/1\na=curr:qos local none\n"+
            "a=des:qos mandatory remote sendrecv\nprivate-do-not-export").getBytes(StandardCharsets.UTF_8));
        check(a.contains("code=488 method=INVITE warning=399 cause=65"));
        check(a.contains("sdp=true precondition=true codecs=AMR"));
        check(a.contains("curr_local_none"));check(a.contains("des_remote_mandatory_sendrecv"));
        check(!a.contains("do-not-export")&&!a.contains("192.0.2.99")&&!a.contains("private.invalid"));
        check(VoiceResponseObservation.describe(200,headers("9 PRACK"),new byte[0]).contains("method=PRACK"));
        check(VoiceResponseObservation.describe(200,headers("10 UPDATE"),new byte[0]).contains("method=UPDATE"));
        check(VoiceResponseObservation.describe(200,headers("9 INVITE\r\nSECRET"),new byte[0]).contains("method=UNKNOWN"));
        h.put("reason",Arrays.asList("Q.850;cause=999;text=SECRET"));
        check(VoiceResponseObservation.describe(488,h,new byte[0]).contains("cause=none"));
        check(VoiceResponseObservation.describe(999,h,new byte[0]).startsWith("voice-sip-response code=0 "));
        check(VoiceResponseObservation.describe(200,h,"a=rtpmap:200 SECRET/8000".getBytes(StandardCharsets.UTF_8)).contains("codecs=none"));
        h.put("content-type",Arrays.asList("text/plain"));
        check(VoiceResponseObservation.describe(200,h,"a=rtpmap:97 AMR/8000".getBytes(StandardCharsets.UTF_8)).contains("sdp=false"));
        System.out.println("metadata-contracts="+checks+" passed");
    }
}
