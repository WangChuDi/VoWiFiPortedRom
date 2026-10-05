// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import android.content.Context;
import android.net.*;
import android.system.OsConstants;
import android.telephony.*;
import dev.codex.vowifi.common.StackProfile;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** API30 IKE backend. Owns only its session, interface and transforms. */
public final class EpdgSession {
    public interface Listener {
        void opened(String iface,List<LinkAddress> addresses,List<InetAddress> dns,List<InetAddress> pcscf);
        void closed(String reason);
    }
    private static final String IKE="android.net.ipsec.ike.";
    private final Context context;
    private final int slot;
    private final int expectedSub;
    private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean stopping=new AtomicBoolean(),notified=new AtomicBoolean(),cleaned=new AtomicBoolean();
    private final Object resourceLock=new Object();
    private final List<IpSecTransform> transforms=new CopyOnWriteArrayList<>();
    private volatile Object session;
    private volatile IpSecManager.IpSecTunnelInterface tunnel;
    private List<InetAddress> pcscf=Collections.emptyList();
    private volatile boolean opened;
    private String stage="initialization";
    public EpdgSession(Context context,int slot,Listener listener) {
        this(context,slot,-1,listener);
    }
    public EpdgSession(Context context,int slot,int expectedSub,Listener listener) {
        this.context=context; this.slot=slot; this.expectedSub=expectedSub; this.listener=listener;
    }
    private static Class<?> type(String name)throws Exception{return Class.forName(name);}
    private static Object call(Object o,String method,Class<?>[] types,Object...args)throws Exception {
        return o.getClass().getMethod(method,types).invoke(o,args);
    }
    private static Object get(Object o,String method)throws Exception{return call(o,method,new Class<?>[0]);}
    private static Object make(String name)throws Exception{return type(name).getConstructor().newInstance();}
    private static void integer(Object o,String name,int value)throws Exception{call(o,name,new Class<?>[]{int.class},value);}
    private static int constant(String name)throws Exception{return type(IKE+"SaProposal").getField(name).getInt(null);}
    private static Object proposal(boolean child)throws Exception {
        Object b=make(IKE+(child?"ChildSaProposal$Builder":"IkeSaProposal$Builder"));
        for(int bits:new int[]{128,256}) call(b,"addEncryptionAlgorithm",new Class<?>[]{int.class,int.class},constant("ENCRYPTION_ALGORITHM_AES_CBC"),bits);
        for(String s:new String[]{"INTEGRITY_ALGORITHM_HMAC_SHA2_256_128","INTEGRITY_ALGORITHM_HMAC_SHA1_96"})integer(b,"addIntegrityAlgorithm",constant(s));
        if(!child){
            integer(b,"addDhGroup",constant("DH_GROUP_2048_BIT_MODP"));
            integer(b,"addDhGroup",constant("DH_GROUP_1024_BIT_MODP"));
            integer(b,"addPseudorandomFunction",constant("PSEUDORANDOM_FUNCTION_SHA2_256"));
            integer(b,"addPseudorandomFunction",constant("PSEUDORANDOM_FUNCTION_HMAC_SHA1"));
        }
        return get(b,"build");
    }
    public void start(){
        timer.schedule(()->{if(!opened)fail("negotiation-timeout");},40,TimeUnit.SECONDS);
        worker.execute(()->{try{connect();}catch(Throwable e){fail(stage+"/"+errorType(e));}});
    }
    private void connect()throws Exception {
        SubscriptionInfo info=StackProfile.selectedSubscription(context,slot);
        if(info==null)throw new IllegalStateException("no-sim");
        int subId=info.getSubscriptionId();
        if(expectedSub>=0&&expectedSub!=subId)throw new IllegalStateException("subscription-changed");
        TelephonyManager tm=context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        if(!"23415".equals(tm.getSimOperator()))throw new IllegalStateException("unsupported-operator");
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        Network wifi=null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities nc=cm.getNetworkCapabilities(n);
            if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){wifi=n;break;}
        }
        if(wifi==null)throw new IllegalStateException("no-wifi");
        InetAddress remote=null,local=null;
        for(InetAddress a:wifi.getAllByName("epdg.epc.mnc015.mcc234.pub.3gppnetwork.org"))if(a instanceof Inet4Address){remote=a;break;}
        LinkProperties lp=cm.getLinkProperties(wifi);
        if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address){local=a.getAddress();break;}
        if(remote==null||local==null)throw new IllegalStateException("no-ipv4");
        if(stopping.get())return;
        stage="SIM-identity";
        String imsi=tm.getSubscriberId();
        if(imsi==null||!imsi.matches("[0-9]{14,16}"))throw new IllegalStateException("sim-identity-unavailable");
        String nai="0"+imsi+"@nai.epc.mnc015.mcc234.3gppnetwork.org";
        stage="IKE-parameters";
        Object eb=make("android.net.eap.EapSessionConfig$Builder");
        call(eb,"setEapAkaConfig",new Class<?>[]{int.class,int.class},subId,TelephonyManager.APPTYPE_USIM);
        call(eb,"setEapIdentity",new Class<?>[]{byte[].class},(Object)nai.getBytes(StandardCharsets.US_ASCII));
        Object ib=IkeApiCompat.newIkeBuilder(type(IKE+"IkeSessionParams$Builder"),Context.class,context);
        call(ib,"setServerHostname",new Class<?>[]{String.class},remote.getHostAddress());
        call(ib,"setNetwork",new Class<?>[]{Network.class},wifi);
        call(ib,"setLocalIdentification",new Class<?>[]{type(IKE+"IkeIdentification")},type(IKE+"IkeRfc822AddrIdentification").getConstructor(String.class).newInstance(nai));
        call(ib,"setRemoteIdentification",new Class<?>[]{type(IKE+"IkeIdentification")},type(IKE+"IkeFqdnIdentification").getConstructor(String.class).newInstance("ims"));
        call(ib,"setAuthEap",new Class<?>[]{java.security.cert.X509Certificate.class,type("android.net.eap.EapSessionConfig")},null,get(eb,"build"));
        IkeApiCompat.addIkeProposal(ib,proposal(false),type(IKE+"IkeSaProposal"));
        integer(ib,"addIkeOption",type(IKE+"IkeSessionParams").getField("IKE_OPTION_EAP_ONLY_AUTH").getInt(null));
        integer(ib,"addIkeOption",type(IKE+"IkeSessionParams").getField("IKE_OPTION_ACCEPT_ANY_REMOTE_ID").getInt(null));
        integer(ib,"addPcscfServerRequest",OsConstants.AF_INET);
        call(ib,"setRetransmissionTimeoutsMillis",new Class<?>[]{int[].class},(Object)new int[]{1000,2000,4000,8000});
        Object cb=make(IKE+"TunnelModeChildSessionParams$Builder");
        call(cb,"addSaProposal",new Class<?>[]{type(IKE+"ChildSaProposal")},proposal(true));
        integer(cb,"addInternalAddressRequest",OsConstants.AF_INET);
        integer(cb,"addInternalDnsServerRequest",OsConstants.AF_INET);
        IpSecManager ipsec=context.getSystemService(IpSecManager.class);
        stage="tunnel-interface";
        synchronized(resourceLock){
            if(stopping.get())return;
            tunnel=ipsec.createIpSecTunnelInterface(local,remote,wifi);
        }
        Object ikeCb=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type(IKE+"IkeSessionCallback")},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class)return objectMethod(p,m.getName(),a);
            switch(m.getName()){
                case "onOpened": pcscf=new ArrayList<>((List<InetAddress>)get(a[0],"getPcscfServers"));break;
                case "onClosedExceptionally":
                case "onClosedWithException": fail(errorType((Throwable)a[0]));cleanup();break;
                case "onClosed": fail("session-closed");cleanup();break;
            }
            return null;
        });
        Object childCb=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type(IKE+"ChildSessionCallback")},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class)return objectMethod(p,m.getName(),a);
            try{
                switch(m.getName()){
                    case "onIpSecTransformCreated":
                        synchronized(resourceLock){
                            IpSecTransform transform=(IpSecTransform)a[0];
                            if(stopping.get()){transform.close();break;}
                            transforms.add(transform);
                            ipsec.applyTunnelModeTransform(tunnel,(Integer)a[1],transform);
                        }
                        break;
                    case "onIpSecTransformDeleted": transforms.remove((IpSecTransform)a[0]);((IpSecTransform)a[0]).close();break;
                    case "onOpened":
                        if(stopping.get())break;
                        SubscriptionInfo current=StackProfile.selectedSubscription(context,slot);
                        if(current==null||current.getSubscriptionId()!=subId)throw new IllegalStateException("subscription-changed");
                        List<LinkAddress> addresses=new ArrayList<>((List<LinkAddress>)get(a[0],"getInternalAddresses"));
                        List<InetAddress> dns=new ArrayList<>((List<InetAddress>)get(a[0],"getInternalDnsServers"));
                        if(addresses.isEmpty()||pcscf.isEmpty())throw new IllegalStateException("missing-network-parameters");
                        for(LinkAddress la:addresses)tunnel.addAddress(la.getAddress(),la.getPrefixLength());
                        opened=true;listener.opened(tunnel.getInterfaceName(),addresses,dns,new ArrayList<>(pcscf));break;
                    case "onClosedExceptionally":
                    case "onClosedWithException": fail(errorType((Throwable)a[0]));break;
                    case "onClosed": fail("child-closed");break;
                }
            }catch(Throwable e){fail(errorType(e));}
            return null;
        });
        if(stopping.get()){cleanup();return;}
        stage="IKE-session";
        synchronized(resourceLock){
            if(stopping.get())return;
            SubscriptionInfo current=StackProfile.selectedSubscription(context,slot);
            if(current==null||current.getSubscriptionId()!=subId)throw new IllegalStateException("subscription-changed");
            session=type(IKE+"IkeSession").getConstructor(Context.class,type(IKE+"IkeSessionParams"),type(IKE+"ChildSessionParams"),Executor.class,type(IKE+"IkeSessionCallback"),type(IKE+"ChildSessionCallback"))
                    .newInstance(context,get(ib,"build"),get(cb,"build"),worker,ikeCb,childCb);
        }
        if(stopping.get())close();
    }
    private static Object objectMethod(Object p,String n,Object[] a){
        if("hashCode".equals(n))return System.identityHashCode(p);
        if("equals".equals(n))return p==a[0];return "EpdgCallback";
    }
    private static String errorType(Throwable e){
        if(e instanceof InvocationTargetException&&e.getCause()!=null)e=e.getCause();
        String s=e.getClass().getSimpleName();
        if(e.getCause()!=null)s+="/cause="+e.getCause().getClass().getSimpleName();
        if(e instanceof SecurityException&&e.getMessage()!=null){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("android\\.permission\\.[A-Z_]+|Caller is not a VPN").matcher(e.getMessage());
            while(m.find())s+="/"+m.group();
        }
        try{s+="/"+get(e,"getErrorType");}catch(Exception ignored){}
        return s;
    }
    private void fail(String reason){if(notified.compareAndSet(false,true))listener.closed(reason);close();}
    public void close(){
        final Object current;
        synchronized(resourceLock){stopping.set(true);current=session;}
        if(current==null){cleanup();return;}
        try{get(current,"close");}catch(Exception ignored){}
        if(!timer.isShutdown())try{timer.schedule(()->{try{get(current,"kill");}catch(Exception ignored){}cleanup();},3,TimeUnit.SECONDS);}catch(RejectedExecutionException ignored){}
    }
    private void cleanup(){
        synchronized(resourceLock){
            if(!cleaned.compareAndSet(false,true))return;
            stopping.set(true);
            for(IpSecTransform t:transforms)try{t.close();}catch(Exception ignored){}
            transforms.clear();
            if(tunnel!=null)try{tunnel.close();}catch(Exception ignored){}
        }
        timer.shutdownNow();worker.shutdown();
    }
}
