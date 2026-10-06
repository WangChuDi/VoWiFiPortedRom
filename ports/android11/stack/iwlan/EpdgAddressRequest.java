// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

/** Immutable EPC address intent, independent of the Android12 data-service types. */
public final class EpdgAddressRequest {
    // ApnSetting.PROTOCOL_IP/IPV6/IPV4V6; these wire values are stable on API30+.
    public static final int IPV4=0, IPV6=1, DUAL=2;
    public static final EpdgAddressRequest LEGACY=new EpdgAddressRequest(IPV4,null,null,0);
    public final int protocol;
    public final Inet4Address ipv4;
    public final Inet6Address ipv6;
    public final int ipv6Prefix;

    public EpdgAddressRequest(int protocol,Inet4Address ipv4,Inet6Address ipv6,int prefix) {
        if(protocol!=IPV4&&protocol!=IPV6&&protocol!=DUAL)throw new IllegalArgumentException("unsupported-protocol");
        if(ipv4!=null&&protocol==IPV6||ipv6!=null&&protocol==IPV4)throw new IllegalArgumentException("source-family-mismatch");
        if(ipv6!=null&&(prefix<0||prefix>128))throw new IllegalArgumentException("invalid-ipv6-prefix");
        this.protocol=protocol;this.ipv4=ipv4;this.ipv6=ipv6;this.ipv6Prefix=prefix;
    }
    public boolean handover(){return ipv4!=null||ipv6!=null;}
    public boolean wants4(){return handover()?ipv4!=null:protocol!=IPV6;}
    public boolean wants6(){return handover()?ipv6!=null:protocol!=IPV4;}

    public void configurePcscf(Object builder,int af4,int af6)throws Exception {
        if(wants4())invoke(builder,"addPcscfServerRequest",new Class<?>[]{int.class},af4);
        if(wants6())invoke(builder,"addPcscfServerRequest",new Class<?>[]{int.class},af6);
    }
    public void configureChild(Object builder,int af4,int af6)throws Exception {
        if(wants4()){
            if(ipv4!=null)invoke(builder,"addInternalAddressRequest",new Class<?>[]{Inet4Address.class},ipv4);
            else invoke(builder,"addInternalAddressRequest",new Class<?>[]{int.class},af4);
            invoke(builder,"addInternalDnsServerRequest",new Class<?>[]{int.class},af4);
        }
        if(wants6()){
            if(ipv6!=null)invoke(builder,"addInternalAddressRequest",new Class<?>[]{Inet6Address.class,int.class},ipv6,ipv6Prefix);
            else invoke(builder,"addInternalAddressRequest",new Class<?>[]{int.class},af6);
            invoke(builder,"addInternalDnsServerRequest",new Class<?>[]{int.class},af6);
        }
    }
    private static void invoke(Object target,String method,Class<?>[] types,Object...args)throws Exception {
        target.getClass().getMethod(method,types).invoke(target,args);
    }
    /** A delegated IPv6 prefix can retain the source IID, as in AOSP IWLAN. */
    public InetAddress preserveIpv6(InetAddress assigned,int prefix) {
        if(ipv6==null||!(assigned instanceof Inet6Address)||prefix!=ipv6Prefix)return assigned;
        byte[] source=ipv6.getAddress(),received=assigned.getAddress();
        int whole=prefix/8,bits=prefix%8;
        for(int i=0;i<whole;i++)if(source[i]!=received[i])return assigned;
        int mask=(0xff<<(8-bits))&0xff;
        if(bits!=0&&((source[whole]&mask)!=(received[whole]&mask)))return assigned;
        return ipv6;
    }
    /** Handover success requires the source addresses, not merely a new tunnel. */
    public void verifyAssigned(List<InetAddress> addresses) {
        boolean v4=false,v6=false;
        for(InetAddress a:addresses){
            if(a instanceof Inet4Address)v4=true;
            if(a instanceof Inet6Address)v6=true;
        }
        if(!v4&&!v6||v4&&!wants4()||v6&&!wants6())throw new IllegalStateException("assigned-family-mismatch");
        if(ipv4!=null&&!addresses.contains(ipv4)||ipv6!=null&&!addresses.contains(ipv6))
            throw new IllegalStateException("handover-address-not-preserved");
    }
}
