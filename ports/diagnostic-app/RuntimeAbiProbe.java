// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lookup only: no class initialization, Binder invocation, IKE or SIM authentication. */
public final class RuntimeAbiProbe {
    private RuntimeAbiProbe() {}
    public static Map<String, Object> inspect(int sdk, ClassLoader loader) {
        Map<String, String> checks = new LinkedHashMap<>();
        String ike = "android.net.ipsec.ike.";
        String builder = ike + "IkeSessionParams$Builder";
        check(checks, loader, "eap_builder", "android.net.eap.EapSessionConfig$Builder", "<init>");
        check(checks, loader, "eap_aka", "android.net.eap.EapSessionConfig$Builder", "setEapAkaConfig", "int", "int");
        check(checks, loader, "eap_identity", "android.net.eap.EapSessionConfig$Builder", "setEapIdentity", "byte[]");
        alternative(checks, loader, "ike_builder", new String[][] {
            {builder, "<init>", "android.content.Context"}, {builder, "<init>"}});
        check(checks, loader, "ike_hostname", builder, "setServerHostname", "java.lang.String");
        check(checks, loader, "ike_network", builder, "setNetwork", "android.net.Network");
        check(checks, loader, "ike_local_id", builder, "setLocalIdentification", ike + "IkeIdentification");
        check(checks, loader, "ike_remote_id", builder, "setRemoteIdentification", ike + "IkeIdentification");
        check(checks, loader, "ike_eap", builder, "setAuthEap", "java.security.cert.X509Certificate", "android.net.eap.EapSessionConfig");
        alternative(checks, loader, "ike_proposal", new String[][] {
            {builder, "addIkeSaProposal", ike + "IkeSaProposal"},
            {builder, "addSaProposal", ike + "IkeSaProposal"}});
        check(checks, loader, "ike_options", builder, "addIkeOption", "int");
        check(checks, loader, "ike_retransmit", builder, "setRetransmissionTimeoutsMillis", "int[]");
        check(checks, loader, "ike_session", ike + "IkeSession", "<init>", "android.content.Context",
            ike + "IkeSessionParams", ike + "ChildSessionParams", "java.util.concurrent.Executor",
            ike + "IkeSessionCallback", ike + "ChildSessionCallback");
        check(checks, loader, "ike_close", ike + "IkeSession", "close");
        check(checks, loader, "ike_kill", ike + "IkeSession", "kill");
        check(checks, loader, "ike_pcscf", ike + "IkeSessionConfiguration", "getPcscfServers");
        check(checks, loader, "child_addresses", ike + "ChildSessionConfiguration", "getInternalAddresses");
        check(checks, loader, "child_dns", ike + "ChildSessionConfiguration", "getInternalDnsServers");
        check(checks, loader, "ipsec_create", "android.net.IpSecManager", "createIpSecTunnelInterface",
            "java.net.InetAddress", "java.net.InetAddress", "android.net.Network");
        check(checks, loader, "ipsec_apply", "android.net.IpSecManager", "applyTunnelModeTransform",
            "android.net.IpSecManager$IpSecTunnelInterface", "int", "android.net.IpSecTransform");
        String carrier = "com.android.internal.telephony.ICarrierConfigLoader";
        check(checks, loader, "carrier_stub", carrier + "$Stub", "asInterface", "android.os.IBinder");
        alternative(checks, loader, "carrier_read", new String[][] {
            {carrier, "getConfigForSubId", "int", "java.lang.String"},
            {carrier, "getConfigForSubIdWithFeature", "int", "java.lang.String", "java.lang.String"},
            {carrier, "getConfigForSubId", "int", "java.lang.String", "java.lang.String"}});
        check(checks, loader, "carrier_override", carrier, "overrideConfig", "int", "android.os.PersistableBundle", "boolean");
        Map<String, Object> result = new LinkedHashMap<>();
        int visible = 0, missing = 0, inaccessible = 0, linkage = 0;
        for (String state : checks.values()) {
            if (state.startsWith("visible:")) visible++;
            else if (state.equals("missing")) missing++;
            else if (state.equals("inaccessible")) inaccessible++;
            else linkage++;
        }
        result.put("schema", 1);
        result.put("sdk", sdk);
        result.put("modern_candidate", sdk >= 31 && sdk <= 37);
        result.put("scope", "root_app_process_core_lookup");
        result.put("checks", checks);
        result.put("visible", visible);
        result.put("missing", missing);
        result.put("inaccessible", inaccessible);
        result.put("linkage_errors", linkage);
        result.put("total", checks.size());
        result.put("installation_verified", false);
        result.put("binding_verified", false);
        result.put("permissions_verified", false);
        result.put("carrier_verified", false);
        return result;
    }

    private static void check(Map<String, String> checks, ClassLoader loader, String key,
                              String type, String member, String... args) {
        String[] signature = new String[args.length + 2];
        signature[0] = type; signature[1] = member;
        System.arraycopy(args, 0, signature, 2, args.length);
        alternative(checks, loader, key, new String[][] {signature});
    }

    static void alternative(Map<String, String> checks, ClassLoader loader, String key,
                            String[][] signatures) {
        String state = "missing";
        for (int index = 0; index < signatures.length; index++) {
            String[] signature = signatures[index];
            try {
                Class<?> type = resolve(signature[0], loader);
                Class<?>[] args = new Class<?>[signature.length - 2];
                for (int i = 0; i < args.length; i++) args[i] = resolve(signature[i + 2], loader);
                if ("<init>".equals(signature[1])) type.getConstructor(args);
                else type.getMethod(signature[1], args);
                state = "visible:" + index;
                break;
            } catch (ClassNotFoundException | NoSuchMethodException absent) {
                // Match production alias selection: only absence permits another spelling.
            } catch (SecurityException denied) {
                state = "inaccessible"; break;
            } catch (LinkageError broken) {
                state = "linkage_error"; break;
            }
        }
        checks.put(key, state);
    }

    private static Class<?> resolve(String name, ClassLoader loader) throws ClassNotFoundException {
        if ("int".equals(name)) return int.class;
        if ("boolean".equals(name)) return boolean.class;
        if ("byte[]".equals(name)) return byte[].class;
        if ("int[]".equals(name)) return int[].class;
        return Class.forName(name, false, loader);
    }
}
