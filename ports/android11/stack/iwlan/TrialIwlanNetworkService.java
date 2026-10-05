// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import android.net.*;
import android.telephony.*;
import java.util.Collections;

public final class TrialIwlanNetworkService extends NetworkService {
    @Override public NetworkServiceProvider onCreateNetworkServiceProvider(int slot){return new Provider(slot);}
    private final class Provider extends NetworkServiceProvider {
        final ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        final ConnectivityManager.NetworkCallback watcher=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){notifyNetworkRegistrationInfoChanged();}
            @Override public void onLost(Network n){notifyNetworkRegistrationInfoChanged();}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities nc){notifyNetworkRegistrationInfoChanged();}
        };
        Provider(int slot){super(slot);cm.registerNetworkCallback(new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),watcher);}
        @Override public void requestNetworkRegistrationInfo(int domain,NetworkServiceCallback callback){
            if(domain!=NetworkRegistrationInfo.DOMAIN_PS){callback.onRequestNetworkRegistrationInfoComplete(NetworkServiceCallback.RESULT_ERROR_UNSUPPORTED,null);return;}
            boolean wifi=false;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities nc=cm.getNetworkCapabilities(n);
                if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){wifi=true;break;}
            }
            NetworkRegistrationInfo info=new NetworkRegistrationInfo.Builder().setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setRegistrationState(wifi?NetworkRegistrationInfo.REGISTRATION_STATE_HOME:NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .setAccessNetworkTechnology(wifi?TelephonyManager.NETWORK_TYPE_IWLAN:TelephonyManager.NETWORK_TYPE_UNKNOWN)
                .setEmergencyOnly(false).setAvailableServices(wifi?Collections.singletonList(NetworkRegistrationInfo.SERVICE_TYPE_DATA):Collections.emptyList()).build();
            callback.onRequestNetworkRegistrationInfoComplete(NetworkServiceCallback.RESULT_SUCCESS,info);
        }
        @Override public void close(){cm.unregisterNetworkCallback(watcher);}
    }
}
