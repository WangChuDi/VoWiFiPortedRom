// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import org.json.JSONObject;

/** Describe metadata already validated against the selected IMS process. */
public final class SmsSendStatus {
    private SmsSendStatus(){}
    public static String describe(JSONObject value){
        if(value==null||value.optInt("sms_send_schema")!=1)return "当前 IMS 未提供发送阶段观测";
        String state=value.optString("sms_send_state"),rp=value.optString("sms_send_rp_state"),failure=value.optString("sms_send_failure");
        if("UNOBSERVED".equals(state))return "本代 IMS 尚未观测到短信发送请求";
        int sip=value.optInt("sms_send_sip_status"),cause=value.optInt("sms_send_rp_cause",-1);
        String heading="PREPARING".equals(state)?"正在准备短信":"WAITING_NETWORK_ACK".equals(state)?"等待网络确认":"ACCEPTED".equals(state)?"网络已确认接受":"FAILED".equals(state)?"发送失败":"状态未知";
        String details="\nSIP 响应："+(sip==0?"尚未观测":Integer.toString(sip))+" · RP 回执："+("ACCEPTED".equals(rp)?"接受":"REJECTED".equals(rp)?"拒绝":"尚未观测");
        if(cause>=0)details+=" · RP 原因码 "+cause;
        if(!"NONE".equals(failure))details+="\n"+failureName(failure);
        return heading+details+"\n最近一次发送的历史观测；网络接受不代表收件人收到，也不证明收件箱或通知正常。";
    }
    private static String failureName(String value){
        switch(value){
            case "FORMAT_UNSUPPORTED":return "短信格式不支持";case "NOT_READY":return "IMS 发送接口尚未就绪";
            case "NO_SMSC":return "没有可用的短信中心配置";case "REF_IN_USE":return "短信事务引用仍被占用";
            case "SIP_REJECTED":return "SIP 请求被拒绝";case "RP_REJECTED":return "网络返回 RP 拒绝";
            case "TRANSPORT_IO":return "SIP 传输写入失败";case "SIP_TIMEOUT":return "等待最终 SIP 确认超时";
            case "RP_TIMEOUT":return "SIP 已接受，但等待 RP 回执超时";case "FRAMEWORK_ERROR":return "发送准备或框架接口异常";
            default:return "发送失败，原因未分类";
        }
    }
}
