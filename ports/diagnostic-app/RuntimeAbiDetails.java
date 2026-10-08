// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.*;

/** Explanations for the fixed lookup list, not an API selector. */
final class RuntimeAbiDetails {
    static final String[] GROUPS={"EAP／SIM 鉴权配置 · 3 项","IKE 会话参数 · 9 项","IKE 生命周期 · 3 项","隧道协商结果 · 3 项","IPsec 数据面 · 2 项","CarrierConfig · 3 项"};
    private static final String[][] ITEMS={
        {"0","eap_builder","EapSessionConfig.Builder()","创建 EAP 配置"},
        {"0","eap_aka","setEapAkaConfig(int,int)","选择订阅和 AKA 应用"},
        {"0","eap_identity","setEapIdentity(byte[])","设置 EAP 身份"},
        {"1","ike_builder","IkeSessionParams.Builder(Context) / Builder()","创建 IKE 参数；生产有签名回退"},
        {"1","ike_hostname","setServerHostname(String)","设置 ePDG 服务器"},
        {"1","ike_network","setNetwork(Network)","选择底层网络"},
        {"1","ike_local_id","setLocalIdentification(IkeIdentification)","设置本端身份"},
        {"1","ike_remote_id","setRemoteIdentification(IkeIdentification)","设置对端身份"},
        {"1","ike_eap","setAuthEap(X509Certificate,EapSessionConfig)","设置 EAP 鉴权方式"},
        {"1","ike_proposal","addIkeSaProposal(IkeSaProposal) / addSaProposal(...) ","设置加密提案；生产有签名回退"},
        {"1","ike_options","addIkeOption(int)","设置会话选项"},
        {"1","ike_retransmit","setRetransmissionTimeoutsMillis(int[])","设置重传时间"},
        {"2","ike_session","IkeSession(Context,IkeSessionParams,ChildSessionParams,Executor,IkeSessionCallback,ChildSessionCallback)","创建 IKE 会话和回调"},
        {"2","ike_close","IkeSession.close()","正常关闭"},
        {"2","ike_kill","IkeSession.kill()","强制结束"},
        {"3","ike_pcscf","IkeSessionConfiguration.getPcscfServers()","读取 P-CSCF 地址"},
        {"3","child_addresses","ChildSessionConfiguration.getInternalAddresses()","读取隧道内地址"},
        {"3","child_dns","ChildSessionConfiguration.getInternalDnsServers()","读取隧道 DNS"},
        {"4","ipsec_create","IpSecManager.createIpSecTunnelInterface(InetAddress,InetAddress,Network)","创建 IPsec 接口"},
        {"4","ipsec_apply","IpSecManager.applyTunnelModeTransform(IpSecTunnelInterface,int,IpSecTransform)","安装数据面变换"},
        {"5","carrier_stub","ICarrierConfigLoader.Stub.asInterface(IBinder)","取得配置 Binder 接口"},
        {"5","carrier_read","getConfigForSubId(int,String) / getConfigForSubIdWithFeature(int,String,String) / getConfigForSubId(int,String,String)","读取配置；生产有签名回退"},
        {"5","carrier_override","overrideConfig(int,PersistableBundle,boolean)","覆盖运营商配置；本检查不会调用"}
    };
    static String describe(int group,Map<String,String> checks){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<ITEMS.length;i++){String[] item=ITEMS[i];if(Integer.parseInt(item[0])!=group)continue;
            if(s.length()>0)s.append("\n\n");s.append(i+1).append(". ").append(item[2]).append("\n").append(item[3]).append(" · ").append(state(checks.get(item[1])));
        }
        return s.toString();
    }
    static Set<String> keys(){LinkedHashSet<String> keys=new LinkedHashSet<>();for(String[] item:ITEMS)keys.add(item[1]);return keys;}
    private static String state(String value){if(value==null)return "未观测";if(value.startsWith("visible:"))return "签名可见（候选 "+value.substring(8)+"）";
        if("missing".equals(value))return "本加载器未找到";if("inaccessible".equals(value))return "访问受限";if("linkage_error".equals(value))return "加载错误";return "未确认";}
}
