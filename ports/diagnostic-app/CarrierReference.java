// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.*;

/** Public reference data only. Never writes APNs, policy or engine selection. */
final class CarrierReference {
    static final class Profile {
        final String id,label,scope,policy,source;
        final Map<String,String> fields;
        Profile(String id,String label,String scope,String policy,String source,String... pairs){
            this.id=id;this.label=label;this.scope=scope;this.policy=policy;this.source=source;
            LinkedHashMap<String,String> values=new LinkedHashMap<>();
            for(int i=0;i<pairs.length;i+=2)values.put(pairs[i],pairs[i+1]);
            fields=Collections.unmodifiableMap(values);
        }
        String describe(){
            StringBuilder s=new StringBuilder(scope).append("\n\n");
            if(fields.isEmpty())s.append("官方 APN 字段尚未核实；以当前卡配置或运营商客服为准。\n");
            for(Map.Entry<String,String> field:fields.entrySet())s.append(field.getKey()).append(": ").append(field.getValue().isEmpty()?"留空":field.getValue()).append('\n');
            return s.append("\n未列出的字段未核实，不代表留空。\n").append(policy)
                .append("\n互联网／MMS 配置不等于 IMS 配置，也不保证 VoWiFi 注册。\n核实日期：2026-10-08\n官方来源：").append(source).toString();
        }
    }
    private static final String UNKNOWN="VoWiFi 官方支持状态尚未核实；当前替换引擎仍仅开放 VOXI 配置。";
    private static final String GLOBAL="https://www.ctexcel.com/global/globalBusiness-en.html";
    private static final List<Profile> ALL=Collections.unmodifiableList(Arrays.asList(
        new Profile("giffgaff-internet","giffgaff 英国 · 互联网","O2 网络。官方互联网 APN；名称和 APN 类型未列出。",
            "官方 Wi-Fi Calling 要求英国境内、兼容固件和账户条件；国外不支持。替换引擎尚未适配此卡。",
            "https://help.giffgaff.com/en/articles/245215-internet-apn-settings-guide\nhttps://help.giffgaff.com/en/articles/258841-understanding-wifi-calling-and-volte",
            "APN","giffgaff.com","用户名","gg","密码","p","代理","","MCC","234","MNC","10","APN 协议","IPv4v6","漫游协议","IPv4","认证类型","PAP"),
        new Profile("giffgaff-mms","giffgaff 英国 · MMS","独立 MMS 参考；官方用户名／密码与互联网配置不同，不能直接合并。",
            "普通 SMS over IMS 不使用这个 MMS 接入点。替换引擎尚未适配此卡。",
            "https://help.giffgaff.com/en/articles/245265-mms-apn-settings-guide",
            "APN","giffgaff.com","用户名","","密码","","服务器","http://mmsc.mediamessaging.co.uk:8002","MMSC","http://mmsc.mediamessaging.co.uk:8002","代理","","MMS 代理","","MMS 端口","","MCC","234","MNC","10","认证类型","","APN 类型","mms"),
        new Profile("ctexcel-uk","CTExcel 英国","官方资料确认与 EE 合作；不能据此推定此卡的 MNC 或 IMS 配置。",UNKNOWN,GLOBAL),
        new Profile("ctexcel-us","CTExcel 美国","本次未查到可引用的美国本地卡官方 APN 字段。",UNKNOWN,"https://www.ctexcel.ca/aboutus.jspx"),
        new Profile("ctexcel-ca","CTExcel 加拿大 · 本地卡","加拿大本地套餐 APN 未核实；不能套用陆港畅游卡的设置。",UNKNOWN,"https://www.ctexcel.ca/faq.jspx"),
        new Profile("ctexcel-fr","CTExcel 法国","法国本地卡 APN 未核实；不能套用英国或香港配置。",UNKNOWN,GLOBAL),
        new Profile("ctexcel-hk","CTExcel 香港 · 指定月费卡","仅对应官方 planId=1609 月费卡网络设置；其他香港产品需分别核实。",UNKNOWN,
            "https://www.ctexcel.com.hk/planDetail?planId=1609&plantype=23","名称","CTExcel","APN","CTExcel"),
        new Profile("ctexcel-it","CTExcel 意大利","意大利本地卡 APN 未核实。",UNKNOWN,"https://www.ctexcel.com/global/globalArticle-italy-publish-en.html"),
        new Profile("ctexcel-travel","CTExcel 陆港畅游卡 · 加拿大官网指引","仅适用于 FAQ 的陆港畅游卡条目，不是加拿大本地套餐默认配置。",UNKNOWN,
            "https://www.ctexcel.ca/faq.jspx","名称","CTExcel","APN","CTExcel")
    ));
    static List<Profile> all(){return ALL;}
    static int suggested(String carrier,String simName,String plmn){
        String names=(String.valueOf(carrier)+" "+String.valueOf(simName)).toLowerCase(Locale.ROOT);
        // Shared host PLMN alone cannot identify an MVNO brand or product.
        return names.contains("giffgaff")&&"23410".equals(plmn)?0:-1;
    }
    static String identityNote(String carrier,String simName,String plmn){
        String names=(String.valueOf(carrier)+" "+String.valueOf(simName)).toLowerCase(Locale.ROOT);
        if(names.contains("ctexcel")||names.contains("ctexel"))return "名称提示 CTExcel；发行地区和套餐尚未确认，请手动选择参考。不要用当前漫游地区推断发卡地区。";
        if(names.contains("giffgaff"))return "名称提示 giffgaff"+("23410".equals(plmn)?"，归属 PLMN 与官方 APN 参考一致。":"；归属 PLMN 尚未与官方参考匹配，请核实卡配置。")+"名称和 PLMN 不代表运营商已接受 IMS 注册。";
        return "按实际 SIM 名称、归属 PLMN 和可见 CarrierConfig 检测，不限于 Vodafone。共享 PLMN 不能唯一识别虚拟运营商品牌。";
    }
}
