// SPDX-License-Identifier: GPL-2.0
import java.io.IOException;

public final class ModernIwlanObservationTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("observation-contract-"+checks);}
    private static void refused(String dump,int slot)throws Exception {
        try{ModernIwlanObservation.read(dump,slot);}catch(IOException expected){checks++;return;}
        throw new AssertionError("ambiguous-observation-accepted");
    }
    public static void main(String[] args)throws Exception {
        String nested="TransportManager-0\n  mAvailableTransports=[WWAN,WLAN]\n  isInLegacy=false\n  AccessNetworksManager-0:\n    mAvailableNetworks={}\n  Local logs=\n    isInLegacy=true\n";
        ModernIwlanObservation.Result a=ModernIwlanObservation.read(nested,0);
        check(Boolean.FALSE.equals(a.legacy));check(Boolean.TRUE.equals(a.cachedWlan));check(a.transportManager&&a.accessNetworksManager);
        String two="AccessNetworksManager-0:\n  isInLegacy=false\nAccessNetworksManager-1:\n  isInLegacy=true\n";
        check(Boolean.FALSE.equals(ModernIwlanObservation.read(two,0).legacy));
        check(Boolean.TRUE.equals(ModernIwlanObservation.read(two,1).legacy));
        ModernIwlanObservation.Result modern=ModernIwlanObservation.read("AccessNetworksManager-0:\n  Local logs=\n    isInLegacy=true\n",0);
        check(modern.legacy==null&&modern.cachedWlan==null);
        check(ModernIwlanObservation.read("AccessNetworksManager-0:\n  isInLegacy=false\n  Local logs=\n    isInLegacy=true\n",0).legacy.equals(false));
        refused("AccessNetworksManager-0:\n  isInLegacy=false\nAccessNetworksManager-0:\n  isInLegacy=false\n",0);
        refused("TransportManager-0\n  isInLegacy=true\n  AccessNetworksManager-0:\n    isInLegacy=false\n",0);
        refused("AccessNetworksManager-0:\n  isInLegacy=FALSE\n",0);
        refused("AccessNetworksManager-0:\n  isInLegacy=false\n  isInLegacy=false\n",0);
        refused("TransportManager-0\n  mAvailableTransports=[WLAN]\n",0);
        refused("TransportManager-0\n  mAvailableTransports=[WWAN,WWAN]\n",0);
        refused("TransportManager-0\n  mAvailableTransports=[WWAN,UNKNOWN]\n",0);
        refused("AccessNetworksManager-1:\n  isInLegacy=false\n",0);
        refused("unrelated log: AccessNetworksManager-0: isInLegacy=false\n",0);
        refused("AccessNetworksManager-999999999999999999:\n",0);
        refused(two,-1);refused(two,8);
        ModernIwlanObservation.Result cached=ModernIwlanObservation.read("TransportManager-0\n  mAvailableTransports=[WWAN]\n  isInLegacy=false\n",0);
        check(Boolean.FALSE.equals(cached.legacy)&&Boolean.FALSE.equals(cached.cachedWlan));
        check(Boolean.FALSE.equals(ModernIwlanObservation.read(two.replace("\n","\r\n"),0).legacy));
        System.out.println(checks+" IWLAN observation contracts passed");
    }
}
