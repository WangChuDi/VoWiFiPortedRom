import android.content.Context;
import android.content.ContextWrapper;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IIntentReceiver;
import android.app.ActivityManager;
import android.net.*;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.UserHandle;
import android.system.OsConstants;
import android.telephony.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One bounded, independent ePDG session. Does not replace framework providers. */
public final class IndependentEpdgProbe {
    /** app_process has no AMS application record; register its root-authorized receiver directly. */
    private static final class ProbeContext extends ContextWrapper implements AutoCloseable {
        private final Map<BroadcastReceiver,IIntentReceiver> receivers = new HashMap<>();
        private final HandlerThread events = new HandlerThread("EpdgProbeEvents");
        ProbeContext(Context base) { super(base); events.start(); }
        @Override public Context getApplicationContext() { return this; }
        @Override public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) {
            return registerReceiver(r,f,null,null,0);
        }
        @Override public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, int flags) {
            return registerReceiver(r,f,null,null,flags);
        }
        @Override public Intent registerReceiver(BroadcastReceiver r, IntentFilter f,String p,Handler h) {
            return registerReceiver(r,f,p,h,0);
        }
        @Override public synchronized Intent registerReceiver(BroadcastReceiver r, IntentFilter f,String p,Handler h,int flags) {
            Handler handler = h != null ? h : new Handler(events.getLooper());
            IIntentReceiver bridge = r == null ? null : new IIntentReceiver.Stub() {
                @Override public void performReceive(Intent intent,int code,String data,Bundle extras,
                        boolean ordered,boolean sticky,int sendingUser) {
                    handler.post(() -> {
                        try { r.onReceive(ProbeContext.this,intent); }
                        finally { if (ordered) try {
                            ActivityManager.getService().finishReceiver(asBinder(),code,data,extras,false,intent.getFlags());
                        } catch (Exception ignored) {} }
                    });
                }
            };
            try {
                Intent result = ActivityManager.getService().registerReceiverWithFeature(null,getPackageName(),null,
                        bridge,f,p,UserHandle.myUserId(),flags);
                if (r != null) receivers.put(r,bridge);
                return result;
            } catch (Exception e) { throw new IllegalStateException("Probe receiver registration failed",e); }
        }
        @Override public synchronized void unregisterReceiver(BroadcastReceiver r) {
            IIntentReceiver bridge = receivers.remove(r);
            if (bridge != null) try { ActivityManager.getService().unregisterReceiver(bridge); }
                catch (Exception e) { throw new IllegalStateException("Probe receiver cleanup failed",e); }
        }
        @Override public synchronized void close() {
            for (BroadcastReceiver r : new ArrayList<>(receivers.keySet())) unregisterReceiver(r);
            events.quitSafely();
        }
    }
    private static final String IKE = "android.net.ipsec.ike.";
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static final CountDownLatch closed = new CountDownLatch(1);
    private static final List<IpSecTransform> transforms = new CopyOnWriteArrayList<>();
    private static volatile boolean success;
    private static String stage = "initialization";
    private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        return o.getClass().getMethod(name, types).invoke(o, args);
    }
    private static Class<?> cls(String name) throws Exception { return Class.forName(name); }
    private static Object make(String name) throws Exception { return cls(name).getConstructor().newInstance(); }
    private static Object get(Object o, String method) throws Exception { return call(o, method, new Class<?>[0]); }
    private static void integer(Object o, String name, int value) throws Exception {
        call(o, name, new Class<?>[]{int.class}, value);
    }
    private static int constant(String name) throws Exception { return cls(IKE + "SaProposal").getField(name).getInt(null); }
    private static void failure(Throwable e) {
        if (e instanceof InvocationTargetException && e.getCause() != null) e = e.getCause();
        System.out.println("failure-type=" + e.getClass().getSimpleName());
        System.out.println("failure-stage=" + stage);
        if (e instanceof SecurityException && e.getMessage() != null)
            System.out.println("permission-error=" + e.getMessage().replaceAll("[0-9]{10,}", "REDACTED"));
        try { System.out.println("ike-error-type=" + get(e, "getErrorType")); } catch (Exception ignored) { }
        if (e.getCause() != null) System.out.println("cause-type=" + e.getCause().getClass().getSimpleName());
    }
    private static Object proposal(boolean child) throws Exception {
        Object b = make(IKE + (child ? "ChildSaProposal$Builder" : "IkeSaProposal$Builder"));
        for (int bits : new int[]{128, 256}) call(b, "addEncryptionAlgorithm", new Class<?>[]{int.class,int.class},
                constant("ENCRYPTION_ALGORITHM_AES_CBC"), bits);
        for (String name : new String[]{"INTEGRITY_ALGORITHM_HMAC_SHA2_256_128", "INTEGRITY_ALGORITHM_HMAC_SHA1_96"})
            integer(b, "addIntegrityAlgorithm", constant(name));
        if (!child) {
            integer(b, "addDhGroup", constant("DH_GROUP_2048_BIT_MODP"));
            integer(b, "addDhGroup", constant("DH_GROUP_1024_BIT_MODP"));
            integer(b, "addPseudorandomFunction", constant("PSEUDORANDOM_FUNCTION_SHA2_256"));
            integer(b, "addPseudorandomFunction", constant("PSEUDORANDOM_FUNCTION_HMAC_SHA1"));
        }
        return get(b, "build");
    }
    public static void main(String[] args) {
        Object session = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        IpSecManager.IpSecTunnelInterface tunnel = null;
        ProbeContext probeContext = null;
        try {
            if (args.length != 1 || !"--connect-once".equals(args[0]))
                throw new IllegalArgumentException("Explicit --connect-once required");
            Looper.prepareMainLooper();
            Class<?> at = cls("android.app.ActivityThread");
            probeContext = new ProbeContext((Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null)));
            Context context = probeContext;
            Class<?> init = cls("android.telephony.TelephonyFrameworkInitializer");
            if (init.getMethod("getTelephonyServiceManager").invoke(null) == null) {
                Class<?> manager = cls("android.os.TelephonyServiceManager");
                init.getMethod("setTelephonyServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            }
            int subId = -1;
            for (SubscriptionInfo info : context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList())
                if (info.getSimSlotIndex() == 1) subId = info.getSubscriptionId();
            if (subId < 0) throw new IllegalStateException("No target SIM");
            TelephonyManager tm = context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
            if (!"23415".equals(tm.getSimOperator())) throw new IllegalStateException("Wrong operator");
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            Network wifi = null;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { wifi = n; break; }
            }
            if (wifi == null) throw new IllegalStateException("No physical Wi-Fi");
            String host = "epdg.epc.mnc015.mcc234.pub.3gppnetwork.org";
            InetAddress remote = null, local = null;
            for (InetAddress a : wifi.getAllByName(host)) if (a instanceof Inet4Address) { remote = a; break; }
            for (LinkAddress a : cm.getLinkProperties(wifi).getLinkAddresses())
                if (a.getAddress() instanceof Inet4Address) { local = a.getAddress(); break; }
            if (remote == null || local == null) throw new IllegalStateException("IPv4 unavailable");
            System.out.println("wifi-dns=OK; target-slot=1; provider-overrides=NONE");
            stage = "SIM-identity";
            String imsi = tm.getSubscriberId();
            if (imsi == null || !imsi.matches("[0-9]{14,16}")) throw new IllegalStateException("SIM identity unavailable");
            String nai = "0" + imsi + "@nai.epc.mnc015.mcc234.3gppnetwork.org";
            Object eb = make("android.net.eap.EapSessionConfig$Builder");
            stage = "IKE-parameters";
            call(eb,"setEapAkaConfig",new Class<?>[]{int.class,int.class},subId,TelephonyManager.APPTYPE_USIM);
            call(eb,"setEapIdentity",new Class<?>[]{byte[].class},(Object)nai.getBytes(StandardCharsets.US_ASCII));
            Object ib = cls(IKE+"IkeSessionParams$Builder").getConstructor(Context.class).newInstance(context);
            call(ib,"setServerHostname",new Class<?>[]{String.class},remote.getHostAddress());
            call(ib,"setNetwork",new Class<?>[]{Network.class},wifi);
            call(ib,"setLocalIdentification",new Class<?>[]{cls(IKE+"IkeIdentification")},
                    cls(IKE+"IkeRfc822AddrIdentification").getConstructor(String.class).newInstance(nai));
            call(ib,"setRemoteIdentification",new Class<?>[]{cls(IKE+"IkeIdentification")},
                    cls(IKE+"IkeFqdnIdentification").getConstructor(String.class).newInstance("ims"));
            call(ib,"setAuthEap",new Class<?>[]{java.security.cert.X509Certificate.class,cls("android.net.eap.EapSessionConfig")},null,get(eb,"build"));
            call(ib,"addSaProposal",new Class<?>[]{cls(IKE+"IkeSaProposal")},proposal(false));
            integer(ib,"addIkeOption",cls(IKE+"IkeSessionParams").getField("IKE_OPTION_EAP_ONLY_AUTH").getInt(null));
            integer(ib,"addIkeOption",cls(IKE+"IkeSessionParams").getField("IKE_OPTION_ACCEPT_ANY_REMOTE_ID").getInt(null));
            integer(ib,"addPcscfServerRequest",OsConstants.AF_INET);
            call(ib,"setRetransmissionTimeoutsMillis",new Class<?>[]{int[].class},(Object)new int[]{1000,2000,4000,8000});
            Object cb = make(IKE+"TunnelModeChildSessionParams$Builder");
            call(cb,"addSaProposal",new Class<?>[]{cls(IKE+"ChildSaProposal")},proposal(true));
            integer(cb,"addInternalAddressRequest",OsConstants.AF_INET);
            integer(cb,"addInternalDnsServerRequest",OsConstants.AF_INET);
            IpSecManager ipsec = context.getSystemService(IpSecManager.class);
            stage = "create-tunnel-interface";
            tunnel = ipsec.createIpSecTunnelInterface(local,remote,wifi);
            final IpSecManager.IpSecTunnelInterface ownedTunnel = tunnel;
            Object ikeCallback = java.lang.reflect.Proxy.newProxyInstance(IndependentEpdgProbe.class.getClassLoader(),
                new Class<?>[]{cls(IKE+"IkeSessionCallback")}, (proxy,method,a) -> {
                    String name = method.getName();
                    if (method.getDeclaringClass() == Object.class) return objectMethod(proxy,name,a);
                    System.out.println("ike-event="+name);
                    if ("onOpened".equals(name))
                        System.out.println("pcscf-count="+((List<?>)get(a[0],"getPcscfServers")).size());
                    if ("onClosedExceptionally".equals(name)) { failure((Throwable)a[0]); finished.countDown(); closed.countDown(); }
                    if ("onClosed".equals(name)) { finished.countDown(); closed.countDown(); }
                    return null;
                });
            Object childCallback = java.lang.reflect.Proxy.newProxyInstance(IndependentEpdgProbe.class.getClassLoader(),
                new Class<?>[]{cls(IKE+"ChildSessionCallback")}, (proxy,method,a) -> {
                    String name = method.getName();
                    if (method.getDeclaringClass() == Object.class) return objectMethod(proxy,name,a);
                    System.out.println("child-event="+name);
                    try {
                        if ("onIpSecTransformCreated".equals(name)) {
                            IpSecTransform transform = (IpSecTransform)a[0];
                            transforms.add(transform);
                            ipsec.applyTunnelModeTransform(ownedTunnel,(Integer)a[1],transform);
                        }
                        if ("onIpSecTransformDeleted".equals(name)) ((IpSecTransform)a[0]).close();
                        if ("onOpened".equals(name)) {
                            List<?> addresses = (List<?>)get(a[0],"getInternalAddresses");
                            for (Object address : addresses) {
                                LinkAddress la = (LinkAddress)address;
                                ownedTunnel.addAddress(la.getAddress(),la.getPrefixLength());
                            }
                            System.out.println("inner-address-count="+addresses.size());
                            success = !addresses.isEmpty(); finished.countDown();
                        }
                        if ("onClosedExceptionally".equals(name)) { failure((Throwable)a[0]); finished.countDown(); }
                    } catch (Throwable e) { failure(e); finished.countDown(); }
                    return null;
                });
            stage = "create-IKE-session";
            session = cls(IKE+"IkeSession").getConstructor(Context.class,cls(IKE+"IkeSessionParams"),
                cls(IKE+"ChildSessionParams"),Executor.class,cls(IKE+"IkeSessionCallback"),cls(IKE+"ChildSessionCallback"))
                .newInstance(context,get(ib,"build"),get(cb,"build"),executor,ikeCallback,childCallback);
            System.out.println("independent-ike=STARTED; deadline-seconds=35; sms-and-calls=NOT_ATTEMPTED");
            if (!finished.await(35,TimeUnit.SECONDS)) System.out.println("independent-ike=TIMEOUT");
            System.out.println("independent-child-session="+(success?"OPENED":"NOT_OPENED"));
        } catch (Throwable e) { failure(e); }
        finally {
            if (session != null) {
                try { get(session,"close"); if (!closed.await(5,TimeUnit.SECONDS)) get(session,"kill"); }
                catch (Throwable e) { failure(e); try { get(session,"kill"); } catch (Throwable ignored) {} }
            }
            for (IpSecTransform t : transforms) try { t.close(); } catch (Exception ignored) { }
            if (tunnel != null) try { tunnel.close(); } catch (Exception ignored) { }
            if (probeContext != null) try { probeContext.close(); } catch (Exception ignored) { }
            executor.shutdownNow();
            System.out.println("owned-resources=CLOSED; original-providers=UNCHANGED");
        }
        System.exit(success ? 0 : 2);
    }
    private static Object objectMethod(Object proxy,String name,Object[] args) {
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("equals".equals(name)) return proxy==args[0];
        return "ProbeCallback";
    }
}
