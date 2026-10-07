// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import dev.codex.vowifi.common.SmsSendObservation;
import dev.codex.vowifi.common.StackTelemetry;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

public final class SmsSendObservationTest {
    private static int checks;
    private static final String NONCE="0123456789abcdef";
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static JSONObject status(String channel,int slot,int sub)throws Exception{
        return new JSONObject(StackTelemetry.snapshot(channel,slot,sub)).put("schema",1).put("nonce",NONCE).put("pid",123).put("boot",7).put("sample_elapsed",1000).put("authorized",true).put("observed",true);
    }
    private static JSONObject validate(JSONObject value,String channel,int slot,int sub)throws Exception{
        return TelemetrySnapshot.validate(value,channel,slot,sub,NONCE,123,7,990,1010);
    }
    private static void reject(JSONObject value,String channel,int slot,int sub,String label)throws Exception{
        try{validate(value,channel,slot,sub);throw new AssertionError(label);}catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[]args)throws Exception{
        AtomicLong clock=new AtomicLong(100);
        StackTelemetry.Owner one=StackTelemetry.begin("ims",1,11,clock::get),two=StackTelemetry.begin("ims",0,22,clock::get);
        JSONObject unobserved=validate(status("ims",1,11),"ims",1,11);
        check(unobserved.getLong("sms_send_attempt")==0,"unobserved is not a failed send");
        check(SmsSendStatus.describe(unobserved).contains("尚未观测"),"unobserved UI");
        SmsSendObservation first=one.beginSms(),second=two.beginSms();clock.set(110);
        first.submitted();first.onSip(202);JSONObject waiting=validate(status("ims",1,11),"ims",1,11);
        check(waiting.getString("sms_send_state").equals("WAITING_NETWORK_ACK"),"202 alone not accepted");
        check(waiting.getString("sms_send_rp_state").equals("UNOBSERVED"),"RP not invented");
        first.timeout();first.finish(false);JSONObject timed=validate(status("ims",1,11),"ims",1,11);
        check(timed.getString("sms_send_failure").equals("RP_TIMEOUT"),"202 without RP classified");
        check(SmsSendStatus.describe(timed).contains("SIP 已接受，但等待 RP 回执超时"),"failure UI describes exact gap");
        first.onRp(true,-1);first.onSip(200);first.finish(true);
        check(new JSONObject(first.snapshot()).getString("sms_send_failure").equals("RP_TIMEOUT"),"late acceptance cannot change timeout");
        second.submitted();second.onRp(true,-1);second.onSip(200);second.finish(true);
        JSONObject accepted=validate(status("ims",0,22),"ims",0,22);
        check(accepted.getString("sms_send_state").equals("ACCEPTED"),"RP before SIP accepted");
        check(SmsSendStatus.describe(accepted).contains("不代表收件人收到"),"acceptance not recipient delivery");
        check(validate(status("ims",1,11),"ims",1,11).getString("sms_send_failure").equals("RP_TIMEOUT"),"other SIM success does not overwrite failure");
        reject(status("ims",1,11),"ims",0,22,"wrong owner");
        SmsSendObservation noSip=one.beginSms();noSip.submitted();noSip.onRp(true,-1);noSip.timeout();noSip.finish(false);
        check(validate(status("ims",1,11),"ims",1,11).getString("sms_send_failure").equals("SIP_TIMEOUT"),"RP ACK alone lacks SIP acceptance");
        SmsSendObservation rejected=one.beginSms();rejected.submitted();rejected.onSip(403);rejected.finish(false);
        check(validate(status("ims",1,11),"ims",1,11).getString("sms_send_failure").equals("SIP_REJECTED"),"SIP rejection");
        SmsSendObservation rpError=one.beginSms();rpError.submitted();rpError.onSip(202);rpError.onRp(false,28);rpError.finish(false);
        JSONObject rp=validate(status("ims",1,11),"ims",1,11);
        check(rp.getString("sms_send_failure").equals("RP_REJECTED")&&rp.getInt("sms_send_rp_cause")==28,"RP cause without private payload");
        for(SmsSendObservation.Failure failure:SmsSendObservation.Failure.values())if(failure!=SmsSendObservation.Failure.NONE){
            SmsSendObservation item=one.beginSms();item.fail(failure);item.finish(false);
            check(validate(status("ims",1,11),"ims",1,11).getString("sms_send_failure").equals(failure.name()),"typed failure "+failure);
        }
        SmsSendObservation newer=one.beginSms();long attempt=(Long)newer.snapshot().get("sms_send_attempt");
        rpError.onRp(true,-1);rpError.finish(true);
        check(validate(status("ims",1,11),"ims",1,11).getLong("sms_send_attempt")==attempt,"old SMS cannot replace newer observation");
        newer.submitted();newer.onSip(202);newer.onSip(100);
        check((Integer)newer.snapshot().get("sms_send_sip_status")==202,"late provisional cannot erase acceptance");
        JSONObject good=status("ims",1,11);
        reject(new JSONObject(good.toString()).put("sms_send_state","private@example.invalid"),"ims",1,11,"free text");
        reject(new JSONObject(good.toString()).put("sms_send_attempt",1.5),"ims",1,11,"fractional attempt");
        reject(new JSONObject(good.toString()).put("sms_send_updated_elapsed",1001),"ims",1,11,"future event");
        reject(new JSONObject(good.toString()).put("sms_send_rp_cause",28),"ims",1,11,"cause without RP rejection");
        reject(new JSONObject(good.toString()).put("sms_send_sip_status",999),"ims",1,11,"invalid SIP");
        reject(new JSONObject(good.toString()).put("sms_send_schema",2),"ims",1,11,"unknown schema");
        JSONObject partial=new JSONObject(good.toString());partial.remove("sms_send_failure");reject(partial,"ims",1,11,"incomplete optional extension");
        reject(new JSONObject(good.toString()).put("sms_send_state","ACCEPTED"),"ims",1,11,"202 without RP cannot claim accepted");
        reject(new JSONObject(good.toString()).put("sms_send_state","FAILED"),"ims",1,11,"failed without cause");
        for(String key:SmsSendObservation.unobserved().keySet())good.remove(key);
        check(!validate(good,"ims",1,11).has("sms_send_schema"),"old producer remains supported");
        StackTelemetry.Owner iwlan=StackTelemetry.begin("iwlan",1,11,clock::get);check(iwlan.beginSms()==null,"nonIMS cannot submit");
        JSONObject foreign=status("iwlan",1,11);for(Map.Entry<String,Object> e:SmsSendObservation.unobserved().entrySet())foreign.put(e.getKey(),e.getValue());
        reject(foreign,"iwlan",1,11,"nonIMS extension refused");
        one.end(false);check(one.beginSms()==null,"retired owner cannot start");
        StackTelemetry.Owner replacement=StackTelemetry.begin("ims",1,11,clock::get);
        newer.onRp(true,-1);newer.finish(true);
        check(validate(status("ims",1,11),"ims",1,11).getLong("sms_send_attempt")==0,"retired feature cannot mutate replacement");
        check(StackTelemetry.snapshot("ims",0,22).get("sms_send_state").equals("ACCEPTED"),"second SIM persists across first retirement");
        System.out.println("SMS send observation PASS checks="+checks+" (production Java/JSON contracts; no carrier traffic)");
    }
}
