package dev.codex.vowifi.qns;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.QualifiedNetworksService;
import android.telephony.ims.ImsMmTelManager;
import dev.codex.vowifi.common.StackProfile;
import dev.codex.vowifi.common.StackTelemetry;
import android.util.Log;
import java.util.Collections;

/** API30 experimental QNS. Reports only in a short, explicit, same-boot trial window. */
public final class TrialQnsService extends QualifiedNetworksService {
    private static final String TAG = "Api30TrialQns";
    @Override public NetworkAvailabilityProvider onCreateNetworkAvailabilityProvider(int slot) {
        return new Provider(slot);
    }
    private boolean eligible(SubscriptionInfo info) {
        try {
            if (info == null) return false;
            TelephonyManager tm = getSystemService(TelephonyManager.class).createForSubscriptionId(info.getSubscriptionId());
            if (!"23415".equals(tm.getSimOperator())) return false;
            if (!ImsMmTelManager.createForSubscriptionId(info.getSubscriptionId()).isVoWiFiSettingEnabled()) return false;
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (RuntimeException e) { Log.w(TAG,"Eligibility unavailable: "+e.getClass().getSimpleName()); }
        return false;
    }
    private final class Provider extends NetworkAvailabilityProvider implements Runnable {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private volatile boolean closed;
        private Boolean previous;
        private StackTelemetry.Owner telemetry;
        private int observedSub=-1;
        Provider(int slot) { super(slot); handler.post(this); }
        @Override public synchronized void run() {
            if (closed) return;
            SubscriptionInfo selected=StackProfile.selectedSubscription(TrialQnsService.this,getSlotIndex());
            boolean active = eligible(selected);
            if (closed) return;
            SubscriptionInfo checked=StackProfile.selectedSubscription(TrialQnsService.this,getSlotIndex());
            if((selected==null)!=(checked==null)||(selected!=null&&selected.getSubscriptionId()!=checked.getSubscriptionId())){
                if(telemetry!=null)telemetry.end(false);telemetry=null;observedSub=-1;previous=null;
                handler.postDelayed(this,2000);return;
            }
            if(selected==null){if(telemetry!=null)telemetry.end(false);telemetry=null;observedSub=-1;previous=null;}
            else if(telemetry==null||observedSub!=selected.getSubscriptionId()){
                if(telemetry!=null)telemetry.end(false);
                observedSub=selected.getSubscriptionId();previous=null;
                telemetry=StackTelemetry.begin("qns",getSlotIndex(),observedSub,SystemClock::elapsedRealtime);
            }
            if (previous == null || previous != active) {
                updateQualifiedNetworkTypes(ApnSetting.TYPE_IMS, active
                    ? Collections.singletonList(AccessNetworkConstants.AccessNetworkType.IWLAN)
                    : Collections.emptyList());
                previous = active;
                Log.i(TAG,"slot="+getSlotIndex()+" trial-iwlan="+active);
            }
            if(telemetry!=null)telemetry.phase(active?StackTelemetry.Phase.IWLAN_SELECTED:StackTelemetry.Phase.NO_IWLAN);
            handler.postDelayed(this,2000);
        }
        @Override public synchronized void close() { closed=true; handler.removeCallbacks(this);if(telemetry!=null)telemetry.end(false); }
    }
}
