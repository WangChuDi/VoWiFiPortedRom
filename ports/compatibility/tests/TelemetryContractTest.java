// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import dev.codex.vowifi.common.StackTelemetry;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

public final class TelemetryContractTest {
    private static int checks;
    private static final String NONCE="0123456789abcdef";
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static JSONObject status(String channel,int slot,int sub)throws Exception{
        JSONObject value=new JSONObject(StackTelemetry.snapshot(channel,slot,sub));
        return value.put("schema",1).put("nonce",NONCE).put("pid",123).put("boot",7).put("sample_elapsed",200).put("authorized",true).put("observed",true);
    }
    private static JSONObject validate(JSONObject value)throws Exception{return TelemetrySnapshot.validate(value,"iwlan",1,11,NONCE,123,7,190,210);}
    private static void rejected(JSONObject value,String label)throws Exception{
        try{validate(value);throw new AssertionError(label+" accepted");}catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[]args)throws Exception{
        AtomicLong clock=new AtomicLong(100);
        StackTelemetry.Owner old=StackTelemetry.begin("iwlan",1,11,clock::get);
        old.phase(StackTelemetry.Phase.DNS);old.epdgResolved(3);clock.set(110);old.ikeOpened();old.transform(0,1);old.transform(1,1);old.childOpened("ipsec9",1,2,2);
        Map<String,Object> original=StackTelemetry.snapshot("iwlan",1,11);
        check(original.get("ike_open").equals(true)&&original.get("child_open").equals(true),"actual callback flags");
        check(original.get("inbound_transforms").equals(1)&&original.get("outbound_transforms").equals(1),"applied transforms counted");
        JSONObject good=validate(status("iwlan",1,11));check(good.getString("interface").equals("ipsec9"),"whitelist snapshot validates");
        check(StackTelemetry.snapshot("iwlan",1,99)==null,"wrong subscription cannot read owner");
        StackTelemetry.Owner other=StackTelemetry.begin("iwlan",0,22,clock::get);other.ikeOpened();
        StackTelemetry.Owner replacement=StackTelemetry.begin("iwlan",1,11,clock::get);replacement.phase(StackTelemetry.Phase.IKE_NEGOTIATING);
        old.ikeOpened();old.end(true);old.transform(0,5);
        Map<String,Object> current=StackTelemetry.snapshot("iwlan",1,11);
        check(current.get("phase").equals("IKE_NEGOTIATING")&&current.get("ike_open").equals(false),"old owner callbacks cannot mutate replacement");
        check(StackTelemetry.snapshot("iwlan",0,22).get("ike_open").equals(true),"independent slot preserved");
        replacement.phase(StackTelemetry.Phase.DNS);replacement.end(true);replacement.ikeOpened();replacement.childOpened("ipsec-old",1,1,1);
        current=StackTelemetry.snapshot("iwlan",1,11);
        check(current.get("phase").equals("FAILED")&&current.get("failed_stage").equals("DNS")&&current.get("child_open").equals(false),"failed owner retains cause stage and rejects late open");
        try{current.put("phase","REGISTERED");throw new AssertionError("mutable result");}catch(UnsupportedOperationException expected){checks++;}
        check(original.get("ike_open").equals(true),"previous snapshots are isolated immutable copies");
        replacement=StackTelemetry.begin("iwlan",1,11,clock::get);replacement.ikeOpened();replacement.childOpened("ipsec10",1,1,1);
        rejected(status("iwlan",1,11).put("sub",99),"wrong sub");
        rejected(status("iwlan",1,11).put("slot",0),"wrong slot");
        rejected(status("iwlan",1,11).put("pid",124),"wrong PID");
        rejected(status("iwlan",1,11).put("boot",8),"wrong boot");
        rejected(status("iwlan",1,11).put("nonce","ffffffffffffffff"),"wrong nonce");
        rejected(status("iwlan",1,11).put("sample_elapsed",189),"cached prequery status");
        rejected(status("iwlan",1,11).put("sample_elapsed",211),"future status");
        rejected(status("iwlan",1,11).put("updated_elapsed",201),"future producer update");
        rejected(status("iwlan",1,11).put("generation",0),"invalid generation");
        rejected(status("iwlan",1,11).put("ike_open","true"),"string boolean");
        rejected(status("iwlan",1,11).put("inbound_transforms",1.5),"noninteger counter");
        rejected(status("iwlan",1,11).put("sms_rx",-1),"negative counter");
        rejected(status("iwlan",1,11).put("phase","authenticated@example.com"),"free-form text");
        rejected(status("iwlan",1,11).put("interface","ipsec9\nprivate"),"interface injection");
        rejected(status("iwlan",1,11).put("pdu","not exported"),"unexpected sensitive field");
        rejected(status("iwlan",1,11).put("retired",true),"retired active session");
        rejected(status("iwlan",1,11).put("authorized",false),"unselected owner");
        rejected(status("iwlan",1,11).put("registered",true),"registration phase conflict");
        rejected(status("iwlan",1,11).put("failed",true),"failure phase conflict");
        rejected(status("iwlan",1,11).put("phase","CLOSED"),"terminal phase must retire owner");
        try{replacement.phase(StackTelemetry.Phase.CLOSED);throw new AssertionError("terminal bypass");}catch(IllegalArgumentException expected){checks++;}
        try{replacement.phase(null);throw new AssertionError("null phase");}catch(IllegalArgumentException expected){checks++;}
        StackTelemetry.Owner ims=StackTelemetry.begin("ims",1,11,clock::get);ims.phase(StackTelemetry.Phase.REGISTERED);ims.sipResponse(200);ims.add(StackTelemetry.Counter.SMS_RX,1);ims.add(StackTelemetry.Counter.SMS_ACK_OK,1);
        Map<String,Object> imsState=StackTelemetry.snapshot("ims",1,11);
        check(imsState.get("registered").equals(true)&&imsState.get("sms_rx").equals(1L)&&imsState.get("sms_ack_ok").equals(1L),"SMS arrival and framework ACK distinct");
        ims.phase(StackTelemetry.Phase.DOWN);check(StackTelemetry.snapshot("ims",1,11).get("registered").equals(false),"DOWN withdraws registration");
        ims.end(false);ims.add(StackTelemetry.Counter.SMS_RX,1);check(StackTelemetry.snapshot("ims",1,11).get("sms_rx").equals(1L),"retired SMS handler cannot claim new delivery");
        replacement.childOpened("192.0.2.1:secret",1,1,1);check(StackTelemetry.snapshot("iwlan",1,11).get("interface").equals(""),"endpoint-like interface rejected");
        final StackTelemetry.Owner stale=replacement;
        Thread late=new Thread(()->{for(int i=0;i<10000;i++)stale.ikeOpened();});late.start();
        StackTelemetry.Owner fresh=StackTelemetry.begin("iwlan",1,11,clock::get);fresh.phase(StackTelemetry.Phase.DNS);
        late.join();check(StackTelemetry.snapshot("iwlan",1,11).get("phase").equals("DNS"),"concurrent old callbacks isolated");
        fresh.end(false);validate(status("iwlan",1,11));checks++;
        System.out.println("Telemetry ownership/schema contract PASS checks="+checks+" (not a Binder/device test)");
    }
}
