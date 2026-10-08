// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import dev.codex.vowifi.common.ServiceAbiCatalog;
import org.json.JSONObject;
import java.util.*;

/** Separate closed protocol: never reuse root lookup as service-process evidence. */
final class ServiceAbiSnapshot {
    static JSONObject validate(JSONObject v,String role,int slot,int sub,String nonce,int pid,int boot,int sdk,long began,long now)throws Exception{
        Set<String> allowed=new HashSet<>(Arrays.asList("schema","channel","slot","sub","nonce","pid","boot","scope","read_only","initialization_performed","calls_verified","authorized","observed","catalogue","sdk","sample_elapsed"));
        if(!(v.opt("observed") instanceof Boolean)||!(v.opt("authorized") instanceof Boolean))throw new IllegalArgumentException("abi-flags");
        boolean observed=v.getBoolean("observed");
        if(observed)allowed.addAll(Arrays.asList("generation","checks","total","visible","missing","inaccessible","linkage_errors"));
        Iterator<String> keys=v.keys();while(keys.hasNext())if(!allowed.contains(keys.next()))throw new IllegalArgumentException("abi-extra");
        if(v.length()!=allowed.size())throw new IllegalArgumentException("abi-incomplete");
        if(v.getInt("schema")!=1||v.getInt("catalogue")!=1||!role.equals(v.getString("channel"))||v.getInt("slot")!=slot||v.getInt("sub")!=sub||!nonce.equals(v.getString("nonce"))||v.getInt("pid")!=pid||boot<0||v.getInt("boot")!=boot||v.getInt("sdk")!=sdk)throw new IllegalArgumentException("abi-source");
        if(!"active_service_process_lookup".equals(v.getString("scope"))||!Boolean.TRUE.equals(v.opt("read_only"))||!Boolean.FALSE.equals(v.opt("initialization_performed"))||!Boolean.FALSE.equals(v.opt("calls_verified"))||observed&&!v.getBoolean("authorized"))throw new IllegalArgumentException("abi-scope");
        long sampled=v.getLong("sample_elapsed");if(sampled<began||sampled>now||now-sampled>5000)throw new IllegalArgumentException("abi-freshness");
        if(observed){
            if(v.getLong("generation")<=0)throw new IllegalArgumentException("abi-generation");
            JSONObject checks=v.getJSONObject("checks");List<ServiceAbiCatalog.Entry> entries=ServiceAbiCatalog.entries(role);
            if(checks.length()!=entries.size())throw new IllegalArgumentException("abi-checks");Map<String,String> states=new LinkedHashMap<>();
            for(ServiceAbiCatalog.Entry e:entries){Object state=checks.opt(e.key);if(!(state instanceof String)||!((String)state).matches("visible:[01]|missing|inaccessible|linkage_error"))throw new IllegalArgumentException("abi-state");if("visible:1".equals(state)&&!e.key.equals("ike_builder")&&!e.key.equals("ike_proposal"))throw new IllegalArgumentException("abi-alias");states.put(e.key,(String)state);}
            int[] counts=ServiceAbiCatalog.counts(states);if(v.getInt("total")!=entries.size()||v.getInt("visible")!=counts[0]||v.getInt("missing")!=counts[1]||v.getInt("inaccessible")!=counts[2]||v.getInt("linkage_errors")!=counts[3])throw new IllegalArgumentException("abi-counts");
        }
        return new JSONObject(v.toString());
    }
    static String describe(JSONObject v,String role){
        if(v==null)return "未取得当前服务进程的接口结果；不能用诊断进程的计数代替。";
        String state=v.optString("status");
        if(!"OBSERVED".equals(state))return "NOT_SELECTED".equals(state)?"当前配置未选择本工具的 "+role.toUpperCase(Locale.ROOT)+" 服务；原厂／第三方服务没有本诊断入口，不推测其接口可见性。":
            "NO_ACTIVE_OWNER".equals(state)?"未观测到所选 SIM 的有效服务实例；不启动新服务来代替实际实例。":
            "PROCESS_UNAVAILABLE".equals(state)?"当前服务进程未运行或身份不可读；未调用接口诊断。":
            "INSTANCE_CHANGED".equals(state)?"服务实例在采样期间变化，本次接口结果作废，请刷新。":
            "服务未提供接口诊断，或权限／调用失败；结果未确定。不会用 root 进程结果冒充服务结果。";
        StringBuilder text=new StringBuilder("实际 ").append(role.toUpperCase(Locale.ROOT)).append(" 服务进程 · 会话代次 ").append(v.optLong("generation"))
            .append("\n签名可见 ").append(v.optInt("visible")).append('/').append(v.optInt("total")).append(" · 本加载器缺失 ").append(v.optInt("missing"))
            .append(" · 访问受限 ").append(v.optInt("inaccessible")).append(" · 加载错误 ").append(v.optInt("linkage_errors"));
        JSONObject checks=v.optJSONObject("checks");String group="";
        for(ServiceAbiCatalog.Entry entry:ServiceAbiCatalog.entries(role)){
            if(!group.equals(entry.group)){group=entry.group;text.append("\n\n").append(group);}
            String value=checks==null?"":checks.optString(entry.key);String label=value.startsWith("visible:")?"签名可见":value.equals("missing")?"本加载器未找到":value.equals("inaccessible")?"访问受限":value.equals("linkage_error")?"加载错误":"未确认";
            text.append("\n").append(entry.label).append("\n  ").append(label);
        }
        return text.append("\n\n这是当前服务进程的有限类／方法签名检查，不调用被检查接口，不代表 Binder 权限、实际调用或运营商链路成功。").toString();
    }
}
