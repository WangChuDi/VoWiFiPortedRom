import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.LinkProperties;
import android.net.TelephonyNetworkSpecifier;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Diagnostic and supervised receiver entry point; native IMS bindings are untouched. */
public final class ImsApi30Probe {
    public static void main(String[] args) throws Exception {
        try { probe(args); } catch (Throwable failure) { failure.printStackTrace(System.out); System.exit(1); }
    }
    private static void probe(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        if("1".equals(System.getenv("CODEX_SMS_RESIDENT"))) {
            try(java.io.FileWriter pid=new java.io.FileWriter("/data/local/tmp/codex-vowifi-sms/state/client.pid")) {
                pid.write(Integer.toString(android.os.Process.myPid()));
            }
        }
        // app_process lacks the Zygote initialization used by this MIUI build.
        Class<?> initializer = Class.forName("android.telephony.TelephonyFrameworkInitializer");
        Class<?> manager = Class.forName("android.os.TelephonyServiceManager");
        if (initializer.getMethod("getTelephonyServiceManager").invoke(null) == null)
            initializer.getMethod("setTelephonyServiceManager", manager).invoke(null, manager.getConstructor().newInstance());
        System.out.println("sdk=" + android.os.Build.VERSION.SDK_INT + " uid=" + android.os.Process.myUid());
        for (String permission : new String[]{"android.permission.READ_PRIVILEGED_PHONE_STATE",
                "android.permission.MODIFY_PHONE_STATE", "android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS"}) {
            System.out.println(permission + "=" + (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED));
        }
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subscriptions = sm.getActiveSubscriptionInfoList();
        int subId = -1;
        if (subscriptions != null) for (SubscriptionInfo info : subscriptions) {
            // Deliberately do not print phone number, ICCID, IMSI or auth material.
            if (info.getSimSlotIndex() == 1) subId = info.getSubscriptionId();
        }
        if (subId < 0) throw new IllegalStateException("No active SIM in target slot1");
        TelephonyManager tm = context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        if (!"23415".equals(tm.getSimOperator())) throw new IllegalStateException("Target is not VOXI/Vodafone UK");
        System.out.println("targetSlot=1 subId=" + subId + " simState=" + tm.getSimState() + " simPresent=" + tm.hasIccCard());
        System.out.println("authentication=NOT_ATTEMPTED; permission checks are not an AKA success test");
        if (args.length > 0 && "smsconfig".equals(args[0])) {
            System.out.println("smsc-field=" + android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId).getSmscAddress());
            System.exit(0); return;
        }
        if (args.length > 0 && "core".equals(args[0])) {
            Class<?> sip = Class.forName("me.phh.sip.SipHandler");
            Object instance = sip.getConstructor(Context.class, int.class).newInstance(context, 1);
            System.out.println("sip-handler-constructor=OK; registration=NOT_ATTEMPTED");
            Class<?> codec = Class.forName("me.phh.sip.SmsKt");
            byte[] encoded = (byte[]) codec.getMethod("SipSmsEncodeSms", byte.class, String.class, byte[].class)
                .invoke(null, (byte)42, "+447785016005", new byte[]{1,2});
            byte[] expected = new byte[]{0,42,0,7,(byte)0x91,0x44,0x77,0x58,0x10,0x06,0x50,2,1,2};
            if (!java.util.Arrays.equals(encoded, expected)) throw new AssertionError("RP-DATA encoding mismatch");
            Object error = codec.getMethod("SipSmsDecode", byte[].class).invoke(null, (Object)new byte[]{5,42,1,38});
            if (!Integer.valueOf(38).equals(error.getClass().getMethod("getCause").invoke(error)))
                throw new AssertionError("RP cause mismatch");
            System.out.println("android-sms-codec=OK; sms-transmission=NOT_ATTEMPTED");
            android.os.Handler handler = (android.os.Handler) sip.getMethod("getMyHandler").invoke(instance);
            handler.getLooper().quitSafely();
            System.exit(0); return;
        }
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        for (Network n : cm.getAllNetworks()) {
            describe(cm, n, "existing");
            if (args.length > 0 && (args[0].startsWith("challenge") || "register".equals(args[0]))) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                    && nc.getNetworkSpecifier() instanceof TelephonyNetworkSpecifier
                    && ((TelephonyNetworkSpecifier)nc.getNetworkSpecifier()).getSubscriptionId() == subId) {
                    ImsRegistrationProbe.run(context, tm, n, "challenge2".equals(args[0]) ? 1 : 0, "register".equals(args[0]));
                    System.exit(0); return;
                }
            }
            if (args.length > 0 && "transport".equals(args[0])) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                LinkProperties lp = cm.getLinkProperties(n);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS) && lp != null) {
                    int index = 0;
                    for (java.net.InetAddress address : lp.getPcscfServers()) {
                        if (++index > 2) break;
                        try (java.net.Socket socket = n.getSocketFactory().createSocket()) {
                            socket.connect(new java.net.InetSocketAddress(address, 5060), 3000);
                            System.out.println("pcscf-" + index + " tcp5060=CONNECTED");
                        } catch (Exception e) {
                            // Print exception type only to avoid private address leakage.
                            System.out.println("pcscf-" + index + " tcp5060=" + e.getClass().getSimpleName());
                        }
                    }
                }
            }
        }
        if (args.length == 0 || !"request".equals(args[0])) { System.exit(0); return; }
        final CountDownLatch done = new CountDownLatch(1);
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { describe(cm,n,"requested-available"); done.countDown(); }
            @Override public void onUnavailable() { System.out.println("requested-unavailable"); done.countDown(); }
        };
        boolean registered = false;
        try {
            NetworkRequest req = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder().setSubscriptionId(subId).build()).build();
            cm.requestNetwork(req, cb, 8000);
            registered = true;
            System.out.println("request-accepted");
            System.out.println("callback-completed=" + done.await(10, TimeUnit.SECONDS));
        } finally {
            if (registered) { cm.unregisterNetworkCallback(cb); System.out.println("request-released"); }
        }
        System.exit(0);
    }
    private static void describe(ConnectivityManager cm, Network n, String label) {
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        if (c == null) return;
        boolean ims=c.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        System.out.println(label + " ims=" + ims + " cellular=" + c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            + " wifi=" + c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        if (ims) {
            LinkProperties lp=cm.getLinkProperties(n);
            System.out.println("ims-interface=" + (lp==null?"none":lp.getInterfaceName())
                + " pcscf-count=" + (lp==null?0:lp.getPcscfServers().size()));
        }
    }
}
