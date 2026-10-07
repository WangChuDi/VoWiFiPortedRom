// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import org.json.JSONObject;

/** Render only validated, fixed SIP transport fields; no endpoint or auth data. */
final class SipTransportObservation {
    private static String stage(String value){switch(value){
        case "WAITING_NETWORK":return "等待 IMS 网络";
        case "PLAIN_CONNECT":return "连接 P-CSCF";
        case "INITIAL_REGISTER":return "初始 REGISTER";
        case "AKA":return "SIM 认证";
        case "IPSEC_PARAMETERS":return "协商 SIP IPsec 参数";
        case "SECURE_CONNECT":return "建立受保护的 SIP 连接";
        case "AUTH_REGISTER":return "认证 REGISTER";
        case "REGISTERED":return "该次 REGISTER 已成功";
        case "DOWN":return "连接已断开";
        case "RETRY_WAIT":return "等待自动重试";
        case "STOPPED":return "实例已结束";
        default:return "尚未观测连接尝试";
    }}
    private static String failure(String value){switch(value){
        case "TIMEOUT":return "连接或响应超时";
        case "NETWORK_IO":return "网络读写失败";
        case "SIP_REJECTED":return "SIP 响应未满足注册要求";
        case "INVALID_RESPONSE":return "未取得有效 SIP 响应";
        case "AKA_ERROR":return "SIM 认证未完成";
        case "PARAMETER_ERROR":return "连接参数处理失败";
        case "UNAVAILABLE":return "所需接口或网络参数不可用";
        case "CANCELED":return "网络变更或实例结束，旧尝试取消";
        case "OTHER":return "其他连接错误";
        default:return "未记录失败";
    }}
    static String describe(JSONObject value){
        if(value.optInt("sip_connect_schema")!=1)return "尚未取得 SIP 连接阶段；历史响应码不能确认当前注册状态。";
        long sample=value.optLong("sample_elapsed"),attempt=value.optLong("sip_attempt"),response=value.optLong("sip_response_elapsed");
        StringBuilder text=new StringBuilder("尝试 ").append(attempt).append(" · ").append(value.optString("sip_transport")).append("\n阶段：").append(stage(value.optString("sip_stage")));
        if(response>0)text.append("\n该次尝试最近 SIP 响应：").append(value.optInt("sip_status")).append(" · ").append(Math.max(0,sample-response)/1000).append(" 秒前");
        else text.append("\n该次尝试尚无已观测 SIP 响应");
        if(!"NONE".equals(value.optString("sip_failure"))){text.append("\n失败阶段：").append(stage(value.optString("sip_failure_stage"))).append("\n原因：").append(failure(value.optString("sip_failure"))).append(" · ").append(Math.max(0,sample-value.optLong("sip_failure_elapsed"))/1000).append(" 秒前");}
        long due=value.optLong("sip_retry_due_elapsed");if(due>0)text.append("\n计划重试：").append(Math.max(0,due-sample)/1000).append(" 秒后（计划时间，不代表已执行）");
        return text.append("\n当前注册状态以 IMS 注册卡片为准。").toString();
    }
}
