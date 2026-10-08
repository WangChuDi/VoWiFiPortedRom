// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.*;

/** Role-specific, lookup-only catalogue shared by producers and their validator. */
public final class ServiceAbiCatalog {
    private ServiceAbiCatalog(){}
    public static final class Entry {
        public final String key,group,label;
        final String[][] signatures;
        Entry(String key,String group,String label,String[]... signatures){this.key=key;this.group=group;this.label=label;this.signatures=signatures;}
    }
    private static Entry e(String key,String group,String type,String member,String... args){
        String[] s=new String[args.length+2];s[0]=type;s[1]=member;System.arraycopy(args,0,s,2,args.length);
        return new Entry(key,group,type.substring(type.lastIndexOf('.')+1)+"."+member+"("+String.join(",",args)+")",s);
    }
    public static List<Entry> entries(String role){
        List<Entry> out=new ArrayList<>();String ike="android.net.ipsec.ike.",builder=ike+"IkeSessionParams$Builder",eap="android.net.eap.EapSessionConfig$Builder";
        if("iwlan".equals(role)){
            out.add(e("data_service","服务框架","android.telephony.data.DataService","<class>"));
            out.add(e("network_service","服务框架","android.telephony.NetworkService","<class>"));
            out.add(e("eap_builder","EAP",eap,"<init>"));out.add(e("eap_aka","EAP",eap,"setEapAkaConfig","int","int"));out.add(e("eap_identity","EAP",eap,"setEapIdentity","byte[]"));
            out.add(new Entry("ike_builder","IKE 参数","IkeSessionParams.Builder(Context) / Builder()",new String[]{builder,"<init>","android.content.Context"},new String[]{builder,"<init>"}));
            out.add(e("ike_hostname","IKE 参数",builder,"setServerHostname","java.lang.String"));out.add(e("ike_network","IKE 参数",builder,"setNetwork","android.net.Network"));
            out.add(e("ike_local_id","IKE 参数",builder,"setLocalIdentification",ike+"IkeIdentification"));out.add(e("ike_remote_id","IKE 参数",builder,"setRemoteIdentification",ike+"IkeIdentification"));
            out.add(e("ike_eap","IKE 参数",builder,"setAuthEap","java.security.cert.X509Certificate","android.net.eap.EapSessionConfig"));
            out.add(new Entry("ike_proposal","IKE 参数","addIkeSaProposal(IkeSaProposal) / addSaProposal(IkeSaProposal)",new String[]{builder,"addIkeSaProposal",ike+"IkeSaProposal"},new String[]{builder,"addSaProposal",ike+"IkeSaProposal"}));
            out.add(e("ike_options","IKE 参数",builder,"addIkeOption","int"));out.add(e("ike_retransmit","IKE 参数",builder,"setRetransmissionTimeoutsMillis","int[]"));
            out.add(e("ike_session","IKE 生命周期",ike+"IkeSession","<init>","android.content.Context",ike+"IkeSessionParams",ike+"ChildSessionParams","java.util.concurrent.Executor",ike+"IkeSessionCallback",ike+"ChildSessionCallback"));
            out.add(e("ike_close","IKE 生命周期",ike+"IkeSession","close"));out.add(e("ike_kill","IKE 生命周期",ike+"IkeSession","kill"));
            out.add(e("ike_pcscf","协商结果",ike+"IkeSessionConfiguration","getPcscfServers"));out.add(e("child_addresses","协商结果",ike+"ChildSessionConfiguration","getInternalAddresses"));out.add(e("child_dns","协商结果",ike+"ChildSessionConfiguration","getInternalDnsServers"));
            out.add(e("ipsec_create","IPsec","android.net.IpSecManager","createIpSecTunnelInterface","java.net.InetAddress","java.net.InetAddress","android.net.Network"));
            out.add(e("ipsec_apply","IPsec","android.net.IpSecManager","applyTunnelModeTransform","android.net.IpSecManager$IpSecTunnelInterface","int","android.net.IpSecTransform"));
        }else if("qns".equals(role)){
            String q="android.telephony.data.QualifiedNetworksService",p=q+"$NetworkAvailabilityProvider";
            out.add(e("qns_service","接入网络选择",q,"<class>"));out.add(e("qns_create","接入网络选择",q,"onCreateNetworkAvailabilityProvider","int"));
            out.add(e("qns_provider","接入网络选择",p,"<class>"));out.add(e("qns_update","接入网络选择",p,"updateQualifiedNetworkTypes","int","java.util.List"));out.add(e("qns_close","接入网络选择",p,"close"));
        }else if("ims".equals(role)){
            String service="android.telephony.ims.ImsService",feature="android.telephony.ims.feature.MmTelFeature",registration="android.telephony.ims.stub.ImsRegistrationImplBase",call="android.telephony.ims.stub.ImsCallSessionImplBase",sms="android.telephony.ims.stub.ImsSmsImplBase";
            out.add(e("ims_service","IMS 服务",service,"<class>"));out.add(e("ims_mmtel","IMS 服务",service,"createMmTelFeature","int"));out.add(e("ims_feature","IMS 服务",feature,"<class>"));out.add(e("ims_capabilities","IMS 服务",feature,"notifyCapabilitiesStatusChanged",feature+"$MmTelCapabilities"));
            out.add(e("ims_registration","注册回调",registration,"<class>"));out.add(e("ims_registered","注册回调",registration,"onRegistered","int"));
            out.add(e("ims_call_session","语音会话",call,"<class>"));out.add(e("ims_call_start","语音会话",call,"start","java.lang.String","android.telephony.ims.ImsCallProfile"));out.add(e("ims_call_terminate","语音会话",call,"terminate","int"));
            out.add(e("ims_sms","系统短信",sms,"<class>"));out.add(e("ims_sms_ready","系统短信",sms,"onReady"));out.add(e("ims_sms_send","系统短信",sms,"sendSms","int","int","java.lang.String","java.lang.String","boolean","byte[]"));
            out.add(e("ims_sms_ack","系统短信",sms,"acknowledgeSms","int","int","int"));out.add(e("ims_sms_received","系统短信",sms,"onSmsReceived","int","java.lang.String","byte[]"));out.add(e("ims_sms_result","系统短信",sms,"onSendSmsResult","int","int","int","int"));
        }else throw new IllegalArgumentException("abi-role");
        return Collections.unmodifiableList(out);
    }
    public static Map<String,String> inspect(String role,ClassLoader loader){
        Map<String,String> result=new LinkedHashMap<>();for(Entry e:entries(role))result.put(e.key,lookup(e,loader));return result;
    }
    private static String lookup(Entry e,ClassLoader loader){
        for(int index=0;index<e.signatures.length;index++)try{
            String[] s=e.signatures[index];Class<?> type=resolve(s[0],loader);Class<?>[] args=new Class<?>[s.length-2];for(int i=0;i<args.length;i++)args[i]=resolve(s[i+2],loader);
            if("<init>".equals(s[1]))type.getConstructor(args);
            else if(!"<class>".equals(s[1])){Class<?> current=type;boolean found=false;while(current!=null){try{current.getDeclaredMethod(s[1],args);found=true;break;}catch(NoSuchMethodException absent){current=current.getSuperclass();}}if(!found)throw new NoSuchMethodException();}
            return "visible:"+index;
        }catch(ClassNotFoundException|NoSuchMethodException absent){}
        catch(SecurityException denied){return "inaccessible";}catch(LinkageError broken){return "linkage_error";}
        return "missing";
    }
    private static Class<?> resolve(String name,ClassLoader loader)throws ClassNotFoundException{
        if("int".equals(name))return int.class;if("boolean".equals(name))return boolean.class;if("byte[]".equals(name))return byte[].class;if("int[]".equals(name))return int[].class;
        return Class.forName(name,false,loader);
    }
    public static int[] counts(Map<String,String> checks){int[] c=new int[4];for(String state:checks.values()){if(state.startsWith("visible:"))c[0]++;else if("missing".equals(state))c[1]++;else if("inaccessible".equals(state))c[2]++;else if("linkage_error".equals(state))c[3]++;else throw new IllegalArgumentException("abi-state");}return c;}
}
