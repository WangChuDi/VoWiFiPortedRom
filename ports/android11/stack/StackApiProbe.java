import java.lang.reflect.*;
import java.util.*;

/** Read-only API inventory: no subscriber reads, service overrides or network requests. */
public final class StackApiProbe {
    public static void main(String[] args) {
        String[] names = {
            "android.telephony.data.QualifiedNetworksService$NetworkAvailabilityProvider",
            "android.telephony.data.DataService$DataServiceProvider",
            "android.telephony.ims.stub.ImsSmsImplBase",
            "android.net.ipsec.ike.IkeSession",
            "android.net.ipsec.ike.IkeSessionParams",
            "android.net.ipsec.ike.IkeSessionParams$Builder",
            "android.net.eap.EapSessionConfig$Builder",
            "android.net.ipsec.ike.IkeSessionConfiguration",
            "android.net.ipsec.ike.ChildSessionConfiguration",
            "android.net.ipsec.ike.TunnelModeChildSessionParams$Builder",
            "android.net.ipsec.ike.ike3gpp.Ike3gppExtension"
        };
        for (String name : names) {
            System.out.println("CLASS " + name);
            try {
                Class<?> type = Class.forName(name, false, StackApiProbe.class.getClassLoader());
                TreeSet<String> signatures = new TreeSet<>();
                for (Constructor<?> c : type.getConstructors()) signatures.add(c.toString());
                for (Method m : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(m.getModifiers())) signatures.add(m.toString());
                }
                for (Field f : type.getFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class)
                        signatures.add("constant " + f.getName() + "=" + f.getInt(null));
                }
                for (String signature : signatures) System.out.println("  " + signature);
            } catch (Throwable e) {
                System.out.println("  UNAVAILABLE " + e.getClass().getSimpleName());
            }
        }
    }
}
