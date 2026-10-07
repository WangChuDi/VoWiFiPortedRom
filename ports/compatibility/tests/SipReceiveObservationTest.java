// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import dev.codex.vowifi.common.SipReceiveObservation;
import dev.codex.vowifi.common.StackTelemetry;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

/** Real production observation/registry/parser, with adversarial JSON and owner transitions. */
public final class SipReceiveObservationTest {
    private static int checks;private static final String NONCE="0123456789abcdef";
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static JSONObject status(int slot,int sub)throws Exception{
        return new JSONObject(StackTelemetry.snapshot("ims",slot,sub)).put("schema",1).put("nonce",NONCE).put("pid",123).put("boot",7).put("sample_elapsed",1000).put("authorized",true).put("observed",true);
    }
    private static JSONObject validate(JSONObject value,int slot,int sub)throws Exception{return TelemetrySnapshot.validate(value,"ims",slot,sub,NONCE,123,7,990,1010);}
    private static void reject(JSONObject value,String field,Object invalid)throws Exception{
        JSONObject copy=new JSONObject(value.toString());copy.getJSONObject("sip_receive").put(field,invalid);
        try{validate(copy,1,11);throw new AssertionError("accepted "+field);}catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[]args)throws Exception{
        AtomicLong clock=new AtomicLong(100);StackTelemetry.Owner owner=StackTelemetry.begin("ims",1,11,clock::get),other=StackTelemetry.begin("ims",0,22,clock::get);
        check(validate(status(1,11),1,11).getJSONObject("sip_receive").getLong("attempt")==0,"not started is not a live receive loop");
        check(owner.beginReceive(1)==null,"receive cannot precede real SIP attempt");
        owner.sipAttempt(1,true);other.sipAttempt(1,true);SipReceiveObservation rx=owner.beginReceive(1),peer=other.beginReceive(1);
        check(owner.beginReceive(1)==rx&&owner.beginReceive(2)==null,"same attempt reused; foreign attempt rejected");
        clock.set(110);for(SipReceiveObservation.Channel c:SipReceiveObservation.Channel.values())rx.loop(c,SipReceiveObservation.State.RUNNING);
        SipReceiveObservation.Peer client=rx.accepted(),client2=rx.accepted();client.close();client.close();
        rx.parsed(SipReceiveObservation.Channel.MAIN,false);
        rx.parsed(SipReceiveObservation.Channel.TCP,true);rx.rp(SipReceiveObservation.Rp.ACK);rx.unmatched();
        rx.parsed(SipReceiveObservation.Channel.TCP,true);rx.rp(SipReceiveObservation.Rp.ERROR);
        rx.datagram();rx.parsed(SipReceiveObservation.Channel.UDP,true);rx.rp(SipReceiveObservation.Rp.DATA);
        rx.parsed(SipReceiveObservation.Channel.MAIN,true);rx.rp(SipReceiveObservation.Rp.DECODE_ERROR);
        JSONObject snapshot=validate(status(1,11),1,11),v=snapshot.getJSONObject("sip_receive");
        check(v.getLong("messages_main")==2&&v.getLong("messages_tcp")==2&&v.getLong("messages_udp")==1,"channel message provenance");
        check(v.getLong("message_requests")==4&&v.getLong("rp_ack")==1&&v.getLong("rp_error")==1&&v.getLong("rp_data")==1&&v.getLong("rp_decode_error")==1,"decoded/failed RP cases separated");
        check(v.getLong("tcp_active")==1&&v.getLong("tcp_accepted")==2,"close-once peer accounting");
        check(v.getLong("rp_unmatched")==1&&v.getLong("udp_datagrams")==1,"unmatched RP and received datagrams observed");
        check(SipReceiveStatus.describe(snapshot).contains("MESSAGE 4")&&SipReceiveStatus.describe(snapshot).contains("不能单独证明网络送达"),"UI shows counts and liveness limit");
        peer.loop(SipReceiveObservation.Channel.UDP,SipReceiveObservation.State.RUNNING);peer.datagram();peer.parsed(SipReceiveObservation.Channel.UDP,true);peer.rp(SipReceiveObservation.Rp.DATA);
        check(validate(status(0,22),0,22).getJSONObject("sip_receive").getLong("rp_data")==1&&validate(status(1,11),1,11).getJSONObject("sip_receive").getLong("rp_data")==1,"independent SIM observers");
        SipReceiveObservation.Peer a=rx.accepted(),b=rx.accepted(),c=rx.accepted();
        try{rx.accepted();throw new AssertionError("fifth peer");}catch(IllegalStateException expected){checks++;}
        a.close();b.close();c.close();client2.close();check(validate(status(1,11),1,11).getJSONObject("sip_receive").getLong("tcp_active")==0,"bounded peers all released");
        snapshot=status(1,11);
        reject(snapshot,"pdu","private");reject(snapshot,"main","private@example.invalid");reject(snapshot,"retired","false");
        reject(snapshot,"attempt",2);reject(snapshot,"attempt",1.5);reject(snapshot,"schema",2);reject(snapshot,"updated_elapsed",1001);reject(snapshot,"last_message_elapsed",99);
        reject(snapshot,"messages_main",-1);reject(snapshot,"tcp_active",5);reject(snapshot,"message_requests",6);reject(snapshot,"rp_unmatched",3);
        reject(snapshot,"rp_ack",Integer.MAX_VALUE);reject(snapshot,"udp_datagrams",((long)Integer.MAX_VALUE)+1);
        reject(snapshot,"retired",true);reject(snapshot,"last_message_elapsed",111);
        JSONObject noPeers=new JSONObject(snapshot.toString());noPeers.getJSONObject("sip_receive").put("tcp_accepted",0);reject(noPeers,"tcp_active",1);
        JSONObject zero=new JSONObject(snapshot.toString());zero.put("sip_receive",new JSONObject(SipReceiveObservation.unobserved()));reject(zero,"last_message_elapsed",110);
        JSONObject partial=new JSONObject(snapshot.toString());partial.getJSONObject("sip_receive").remove("rp_error");
        try{validate(partial,1,11);throw new AssertionError("partial schema");}catch(Exception expected){checks++;}
        JSONObject legacy=new JSONObject(snapshot.toString());legacy.remove("sip_receive");check(!validate(legacy,1,11).has("sip_receive"),"older producer accepted without invented receive metadata");
        clock.set(120);rx.loop(SipReceiveObservation.Channel.MAIN,SipReceiveObservation.State.FAILED);rx.loop(SipReceiveObservation.Channel.MAIN,SipReceiveObservation.State.ENDED);
        check(validate(status(1,11),1,11).getJSONObject("sip_receive").getString("main").equals("FAILED"),"end preserves prior failure");
        owner.sipAttempt(2,true);String old=new JSONObject(rx.snapshot()).toString();rx.parsed(SipReceiveObservation.Channel.UDP,true);rx.rp(SipReceiveObservation.Rp.ACK);client2.close();
        check(new JSONObject(rx.snapshot()).toString().equals(old),"old attempt frozen after new attempt");
        check(validate(status(1,11),1,11).getJSONObject("sip_receive").getLong("attempt")==0,"fresh connection does not inherit old counts");
        SipReceiveObservation fresh=owner.beginReceive(2);fresh.loop(SipReceiveObservation.Channel.TCP,SipReceiveObservation.State.RUNNING);SipReceiveObservation.Peer owned=fresh.accepted();
        clock.set(130);owner.end(false);owned.close();JSONObject closed=validate(status(1,11),1,11).getJSONObject("sip_receive");
        check(closed.getBoolean("retired")&&closed.getLong("tcp_active")==0&&closed.getString("tcp").equals("ENDED"),"retirement closes logical receive lifetime and peers");
        reject(status(1,11),"retired",false);
        check(validate(status(0,22),0,22).getJSONObject("sip_receive").getString("udp").equals("RUNNING"),"retirement leaves other SIM intact");
        clock.set(140);StackTelemetry.Owner replacement=StackTelemetry.begin("ims",0,22,clock::get);String retired=new JSONObject(peer.snapshot()).toString();peer.datagram();peer.parsed(SipReceiveObservation.Channel.UDP,true);
        check(new JSONObject(peer.snapshot()).toString().equals(retired),"feature replacement freezes old receive token");
        check(validate(status(0,22),0,22).getJSONObject("sip_receive").getLong("attempt")==0&&replacement.beginReceive(1)==null,"replacement requires its own SIP attempt");
        System.out.println("SIP receive observation production contracts PASS checks="+checks+" (not an Android/carrier test)");
    }
}
