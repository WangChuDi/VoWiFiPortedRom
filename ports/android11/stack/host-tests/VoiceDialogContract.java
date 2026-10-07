// SPDX-License-Identifier: GPL-2.0
import me.phh.ims.VoiceDialogState;
import me.phh.ims.SdpSessionVersion;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic wire semantics: loose/strict routing, dialog CSeq and changed SDP. */
public final class VoiceDialogContract {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("dialog-contract");}
    static void rejected(Runnable action){boolean refused=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException e){refused=true;check(!e.getMessage().contains("example.invalid"));}if(!refused)throw new AssertionError("dialog-refusal");}
    static Map<String,List<String>> invite(){Map<String,List<String>> h=new HashMap<>();h.put("from",Arrays.asList("<sip:local@example.invalid>;tag=local"));h.put("to",Arrays.asList("<tel:191>"));h.put("call-id",Arrays.asList("synthetic-call"));h.put("cseq",Arrays.asList("42 INVITE"));h.put("via",Arrays.asList("SIP/2.0/TCP 192.0.2.1;branch=old;rport"));h.put("route",Arrays.asList("<sip:registration.example.invalid;lr>"));return h;}
    static Map<String,List<String>> response(){Map<String,List<String>> h=invite();h.put("to",Arrays.asList("<tel:191>;tag=remote"));h.put("contact",Arrays.asList("<sip:remote@example.invalid;transport=tcp>"));return h;}
    static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    public static void main(String[] args){
        VoiceDialogState d=new VoiceDialogState(invite());Map<String,List<String>> h=response();
        h.put("record-route",Arrays.asList("<sip:far.example.invalid;lr>, <sip:near.example.invalid;lr>"));
        VoiceDialogState.Request p=d.prepare("PRACK",h);
        check(p.target.equals("sip:remote@example.invalid;transport=tcp"));
        check(p.headers.get("route").equals(Arrays.asList("<sip:near.example.invalid;lr>","<sip:far.example.invalid;lr>")));
        check(p.headers.get("cseq").equals(Arrays.asList("43 PRACK")));
        check(!p.headers.get("via").get(0).contains("branch=")&&p.headers.get("via").get(0).contains("rport"));
        VoiceDialogState.Request u=d.prepare("UPDATE",h);check(u.headers.get("cseq").equals(Arrays.asList("44 UPDATE")));
        check(!u.headers.containsKey("content-length")&&!u.headers.containsKey("expires"));
        VoiceDialogState.Request a=d.prepare("ACK",h);check(a.headers.get("cseq").equals(Arrays.asList("42 ACK")));
        check(d.prepare("ACK",h).headers.get("cseq").equals(Arrays.asList("42 ACK")));
        check(d.prepare("BYE",null).headers.get("cseq").equals(Arrays.asList("45 BYE")));
        VoiceDialogState noRoutes=new VoiceDialogState(invite());Map<String,List<String>> direct=response();
        check(!noRoutes.prepare("PRACK",direct).headers.containsKey("route"));
        VoiceDialogState strict=new VoiceDialogState(invite());Map<String,List<String>> s=response();
        s.put("record-route",Arrays.asList("<sip:far.example.invalid;lr>","<sip:strict.example.invalid>"));
        VoiceDialogState.Request t=strict.prepare("PRACK",s);
        check(t.target.equals("sip:strict.example.invalid"));
        check(t.headers.get("route").equals(Arrays.asList("<sip:far.example.invalid;lr>","<sip:remote@example.invalid;transport=tcp>")));
        rejected(()->new VoiceDialogState(invite()).prepare("UPDATE",null));
        Map<String,List<String>> other=response();other.put("call-id",Arrays.asList("another-call"));rejected(()->d.prepare("UPDATE",other));
        byte[] old=bytes("v=0\no=- 777 2 IN IP4 192.0.2.1\na=curr:qos local none");
        byte[] updated=bytes("v=0\no=- 777 2 IN IP4 192.0.2.1\na=curr:qos local sendrecv");
        String next=new String(SdpSessionVersion.changedOffer(old,updated),StandardCharsets.UTF_8);
        check(next.contains("o=- 777 3 IN IP4 192.0.2.1\r\n"));
        check(next.contains("a=curr:qos local sendrecv\r\n"));
        check(Arrays.equals(old,SdpSessionVersion.changedOffer(old,old)));
        rejected(()->SdpSessionVersion.changedOffer(old,bytes("v=0\no=- 888 2 IN IP4 192.0.2.1\na=curr:qos local sendrecv")));
        rejected(()->SdpSessionVersion.changedOffer(bytes("v=0"),bytes("v=0\na=x")));
        rejected(()->SdpSessionVersion.changedOffer(bytes("o=- 1 9223372036854775807 IN IP4 192.0.2.1\na=x"),bytes("o=- 1 9223372036854775807 IN IP4 192.0.2.1\na=y")));
        System.out.println("dialog-contracts="+checks+" passed");
    }
}
