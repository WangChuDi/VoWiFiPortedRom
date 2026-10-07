// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONObject;

/** Closed receive metadata schema; no endpoint, token, PDU or exception text. */
public final class SipReceiveStatus {
    private static final String[] COUNTS={"messages_main","messages_tcp","messages_udp","message_requests","udp_datagrams","tcp_accepted","rp_data","rp_ack","rp_error","rp_other","rp_decode_error","rp_unmatched"};
    private static final String[] CHANNELS={"main","tcp","udp"};
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList("schema","attempt","updated_elapsed","last_message_elapsed","retired","tcp_active","main","tcp","udp"));
    static{KEYS.addAll(Arrays.asList(COUNTS));}
    private SipReceiveStatus(){}
    private static long number(JSONObject value,String key)throws Exception{
        Object n=value.get(key);if(!(n instanceof Number))throw new IllegalArgumentException("receive-number");
        long v=((Number)n).longValue();if(((Number)n).doubleValue()!=v)throw new IllegalArgumentException("receive-integer");return v;
    }
    public static JSONObject validate(JSONObject value,long sipAttempt,long start,long sample,boolean ownerRetired)throws Exception{
        for(Iterator<String> i=value.keys();i.hasNext();)if(!KEYS.contains(i.next()))throw new IllegalArgumentException("receive-schema");
        for(String key:KEYS)if(!value.has(key))throw new IllegalArgumentException("receive-incomplete");
        if(number(value,"schema")!=1)throw new IllegalArgumentException("receive-version");
        Object flag=value.get("retired");if(!(flag instanceof Boolean))throw new IllegalArgumentException("receive-flag");boolean retired=(Boolean)flag;
        long attempt=number(value,"attempt"),at=number(value,"updated_elapsed"),last=number(value,"last_message_elapsed"),active=number(value,"tcp_active");
        if(attempt<0||at<0||last<0||active<0||active>4||at>sample)throw new IllegalArgumentException("receive-range");
        long total=0,rp=0;
        for(String key:COUNTS){long n=number(value,key);if(n<0||n>Integer.MAX_VALUE)throw new IllegalArgumentException("receive-count");if(key.startsWith("messages_"))total+=n;if(key.startsWith("rp_")&&!key.equals("rp_unmatched"))rp+=n;if(attempt==0&&n!=0)throw new IllegalArgumentException("receive-unobserved");}
        for(String key:CHANNELS){Object state=value.get(key);if(!(state instanceof String)||!Arrays.asList("UNOBSERVED","RUNNING","ENDED","FAILED").contains(state))throw new IllegalArgumentException("receive-state");if(retired&&state.equals("RUNNING")||attempt==0&&!state.equals("UNOBSERVED"))throw new IllegalArgumentException("receive-lifetime");}
        if(attempt==0){if(at!=0||last!=0||active!=0||retired)throw new IllegalArgumentException("receive-unobserved");}
        else if(attempt!=sipAttempt||at<start||ownerRetired&&!retired)throw new IllegalArgumentException("receive-owner");
        if(total==0?last!=0:last<start||last>at)throw new IllegalArgumentException("receive-message-time");
        if(retired&&active!=0||active>number(value,"tcp_accepted")||number(value,"message_requests")>total||rp>number(value,"message_requests")||number(value,"rp_unmatched")>number(value,"rp_ack")+number(value,"rp_error"))throw new IllegalArgumentException("receive-consistency");
        return new JSONObject(value.toString());
    }
    public static String describe(JSONObject status){
        JSONObject v=status==null?null:status.optJSONObject("sip_receive");
        if(v==null)return "当前 IMS 未提供入站通道观测";
        if(v.optLong("attempt")==0)return "本代 IMS 尚未启动已观测的入站接收任务";
        String text="主连接："+state(v.optString("main"))+" · TCP 接收："+state(v.optString("tcp"))+" · UDP 接收："+state(v.optString("udp"));
        text+="\nSIP 消息 主连接／TCP／UDP："+v.optLong("messages_main")+"／"+v.optLong("messages_tcp")+"／"+v.optLong("messages_udp");
        text+="\nTCP 活动连接 "+v.optLong("tcp_active")+"，累计接受 "+v.optLong("tcp_accepted")+" · UDP 数据报 "+v.optLong("udp_datagrams");
        text+="\nMESSAGE "+v.optLong("message_requests")+" · RP 数据／确认／拒绝："+v.optLong("rp_data")+"／"+v.optLong("rp_ack")+"／"+v.optLong("rp_error");
        text+="\n解码失败 "+v.optLong("rp_decode_error")+" · 无匹配事务的 RP "+v.optLong("rp_unmatched")+" · 其他 RP "+v.optLong("rp_other");
        return text+"\n运行表示接收任务已启动；计数属于本次 IMS 连接，不能单独证明网络送达或系统短信投递成功。";
    }
    private static String state(String value){switch(value){case "RUNNING":return "运行中";case "ENDED":return "已结束";case "FAILED":return "异常退出";default:return "尚未启动";}}
}
