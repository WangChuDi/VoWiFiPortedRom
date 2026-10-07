// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import dev.codex.vowifi.common.SipReconnectGate;
import dev.codex.vowifi.common.StackTelemetry;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

/** Run the real gate and telemetry/parser through network handovers and stale completions. */
public final class SipReconnectContractTest {
    private static int checks;
    private static final String NONCE="0123456789abcdef";
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static JSONObject status()throws Exception{
        return new JSONObject(StackTelemetry.snapshot("ims",1,11)).put("schema",1).put("nonce",NONCE)
            .put("pid",123).put("boot",7).put("sample_elapsed",200).put("authorized",true).put("observed",true);
    }
    private static JSONObject validate(JSONObject value)throws Exception{
        return TelemetrySnapshot.validate(value,"ims",1,11,NONCE,123,7,190,210);
    }
    private static void reject(JSONObject value,String label)throws Exception{
        try{validate(value);throw new AssertionError(label+" accepted");}catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[]args)throws Exception{
        SipReconnectGate<String> gate=new SipReconnectGate<>(),peer=new SipReconnectGate<>();
        SipReconnectGate.Attempt<String> a=gate.offer("A"),p=peer.offer("peer");
        check(gate.offer("B")==null,"second handshake deferred");
        gate.offer("A");
        check(gate.finish(a,true).deferredNetwork==null,"latest same-network event clears obsolete deferred candidate");
        check(gate.offer("A")==null&&gate.current(a),"registered same network not reconnected");
        gate.lost("A");check(!gate.current(a),"lost registered network rejects late packets");
        a=gate.offer("A");gate.offer("B");gate.lost("A");
        check(!gate.current(a)&&gate.offer("B")==null,"handover waits for canceled handshake cleanup");
        SipReconnectGate.Completion<String> done=gate.finish(a,true);
        check(!done.accepted&&"B".equals(done.deferredNetwork),"canceled success rejected and next network retained");
        SipReconnectGate.Attempt<String> b=gate.offer(done.deferredNetwork);
        check(b.generation>a.generation&&!gate.current(a)&&gate.current(b),"new generation isolates old completion");
        try{gate.finish(a,true);throw new AssertionError("old completion accepted");}catch(IllegalStateException expected){checks++;}
        check(gate.current(b)&&peer.current(p),"invalid completion leaves both current and peer intact");
        gate.offer("C");gate.lost("C");check(gate.finish(b,false).deferredNetwork==null,"lost queued candidate removed");
        b=gate.offer("B");gate.offer("C");gate.stop();done=gate.finish(b,true);
        check(!done.accepted&&done.deferredNetwork==null&&gate.offer("D")==null,"shutdown cannot publish or restart");
        check(peer.finish(p,true).accepted&&peer.current(p),"peer slot unaffected by shutdown");

        AtomicLong clock=new AtomicLong(100);
        StackTelemetry.Owner owner=StackTelemetry.begin("ims",1,11,clock::get);
        validate(status());checks++;
        owner.sipAttempt(1,true);clock.set(110);owner.sipStage(1,StackTelemetry.SipStage.INITIAL_REGISTER);
        clock.set(120);owner.sipResponse(1,401);owner.sipStage(1,StackTelemetry.SipStage.AKA);
        clock.set(130);owner.sipStage(1,StackTelemetry.SipStage.AUTH_REGISTER);owner.sipResponse(1,200);owner.phase(StackTelemetry.Phase.REGISTERED);
        clock.set(140);owner.sipFailure(1,StackTelemetry.SipFailure.TIMEOUT);owner.phase(StackTelemetry.Phase.DOWN);owner.sipRetry(1,2000);
        JSONObject failed=validate(status());
        check(!failed.getBoolean("registered")&&failed.getInt("sip_status")==200,"historical 200 cannot set current registration");
        check(failed.getString("sip_failure_stage").equals("REGISTERED")&&failed.getLong("sip_failure_elapsed")==140,"failure has actual stage and timestamp");
        check(SipTransportObservation.describe(failed).contains("计划时间，不代表已执行"),"UI distinguishes scheduled from executed retry");
        owner.phase(StackTelemetry.Phase.DOWN);validate(status());checks++;
        check(status().getLong("sip_retry_due_elapsed")==0,"phase change clears obsolete retry deadline");
        clock.set(150);owner.sipAttempt(2,true);owner.sipResponse(1,403);owner.sipFailure(1,StackTelemetry.SipFailure.OTHER);
        JSONObject fresh=validate(status());
        check(fresh.getInt("sip_status")==0&&fresh.getLong("sip_response_elapsed")==0&&fresh.getString("sip_failure").equals("NONE"),"new attempt clears historical response and ignores old results");
        check(SipTransportObservation.describe(fresh).contains("尚无已观测 SIP 响应"),"new attempt UI does not reuse historical response");
        JSONObject legacy=new JSONObject(fresh.toString());
        for(String key:new String[]{"sip_connect_schema","sip_attempt","sip_attempt_started_elapsed","sip_stage","sip_stage_elapsed","sip_transport","sip_failure","sip_failure_stage","sip_failure_elapsed","sip_response_elapsed","sip_retry_due_elapsed"})legacy.remove(key);
        check(SipTransportObservation.describe(validate(legacy)).contains("历史响应码不能确认"),"legacy engine accepted with explicit unknown SIP timing");
        JSONObject incomplete=status();incomplete.remove("sip_transport");reject(incomplete,"incomplete extension");
        reject(status().put("sip_stage","private endpoint"),"free-text stage");
        reject(status().put("sip_attempt",2.5),"fractional attempt");
        reject(status().put("sip_response_elapsed",160),"future response");
        reject(status().put("sip_failure","TIMEOUT"),"failure without timestamp");
        reject(status().put("sip_retry_due_elapsed",3000),"deadline outside retry stage");
        reject(status().put("sip_stage","RETRY_WAIT"),"retry stage without deadline");
        reject(status().put("sip_stage_elapsed",99),"stage predates owner");
        clock.set(160);owner.end(false);owner.sipResponse(2,200);validate(status());checks++;
        check(status().getInt("sip_status")==0&&status().getString("sip_stage").equals("STOPPED"),"retired instance rejects late SIP success");
        System.out.println("SIP handover/attempt provenance contract PASS checks="+checks+" (not a device/carrier test)");
    }
}
