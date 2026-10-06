// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.Looper;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.data.*;
import android.util.Log;
import dev.codex.vowifi.common.StackProfile;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** API31+ EPC IMS adapter. Not an N1/5G, slicing or URSP implementation. */
public final class ModernIwlanDataService extends DataService {
    private static final String TAG="ModernIwlanData";
    private static final AtomicInteger NEXT_CID=new AtomicInteger(1000);
    @Override public void onCreate(){
        super.onCreate();
        String channel="vowifi_connection";
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(channel,"Wi-Fi 通话连接",NotificationManager.IMPORTANCE_LOW));
        startForeground(30,new Notification.Builder(this,channel)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call).setContentTitle("Wi-Fi 通话连接")
            .setContentText("正在保持运营商 Wi-Fi 连接").setOngoing(true).setOnlyAlertOnce(true).build());
    }
    @Override public DataServiceProvider onCreateDataServiceProvider(int slot){return new Provider(slot);}

    private static final class Request {
        final int rat,reason,pdu,protocol;
        final boolean roaming,allowRoaming,matchAll;
        final String apn;
        final LinkProperties source;
        final NetworkSliceInfo slice;
        final TrafficDescriptor traffic;
        Request(int rat,DataProfile profile,boolean roaming,boolean allowRoaming,int reason,
                LinkProperties source,int pdu,NetworkSliceInfo slice,TrafficDescriptor traffic,boolean matchAll){
            this.rat=rat;this.reason=reason;this.pdu=pdu;this.roaming=roaming;this.allowRoaming=allowRoaming;
            this.apn=profile==null?null:profile.getApn();
            this.protocol=profile==null?-1:roaming?profile.getRoamingProtocolType():profile.getProtocolType();
            this.source=source==null?null:new LinkProperties(source);
            this.slice=slice;this.traffic=traffic;this.matchAll=matchAll;
        }
        EpdgAddressRequest addressIntent(){
            if(reason!=REQUEST_REASON_HANDOVER)return new EpdgAddressRequest(protocol,null,null,0);
            if(source==null)throw new IllegalArgumentException("missing-handover-source");
            Inet4Address v4=null;Inet6Address v6=null;int prefix=0;
            for(LinkAddress la:source.getLinkAddresses()){
                InetAddress a=la.getAddress();
                if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isMulticastAddress())continue;
                if(a instanceof Inet4Address){
                    if(v4!=null)throw new IllegalArgumentException("multiple-source-ipv4");
                    v4=(Inet4Address)a;
                }else if(a instanceof Inet6Address){
                    if(v6!=null)throw new IllegalArgumentException("multiple-source-ipv6");
                    v6=(Inet6Address)a;prefix=la.getPrefixLength();
                }
            }
            if(v4==null&&v6==null)throw new IllegalArgumentException("empty-handover-source");
            return new EpdgAddressRequest(protocol,v4,v6,prefix);
        }
    }
    private final class Provider extends DataServiceProvider {
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final Object lifetimeLock=new Object();
        private final AtomicBoolean retired=new AtomicBoolean();
        private int cid=-1;
        private int generation,sessionSub=-1;
        private boolean outgoingHandover;
        private EpdgSession session;
        private EpdgAddressRequest addressIntent;
        private Request sessionRequest;
        private DataCallResponse active;
        private DataServiceCallback pending;
        Provider(int slot){super(slot);}
        private void dispatch(Runnable action){handler.post(()->{synchronized(lifetimeLock){action.run();}});}

        @Override public void setupDataCall(int rat,DataProfile profile,boolean roaming,boolean allowRoaming,int reason,
                LinkProperties source,DataServiceCallback callback){
            setupDataCall(rat,profile,roaming,allowRoaming,reason,source,0,null,null,true,callback);
        }
        @Override public void setupDataCall(int rat,DataProfile profile,boolean roaming,boolean allowRoaming,int reason,
                LinkProperties source,int pdu,NetworkSliceInfo slice,TrafficDescriptor traffic,boolean matchAll,
                DataServiceCallback callback){
            Request request=new Request(rat,profile,roaming,allowRoaming,reason,source,pdu,slice,traffic,matchAll);
            dispatch(()->setup(request,callback));
        }
        private void reply(DataServiceCallback cb,int result,DataCallResponse response){if(cb!=null)cb.onSetupDataCallComplete(result,response);}
        private DataCallResponse failure(Request request,int cause,int retry){
            return new DataCallResponse.Builder().setId(cid).setCause(cause).setSuggestedRetryTime(retry)
                .setHandoverFailureMode(request.reason==REQUEST_REASON_HANDOVER
                    ?DataCallResponse.HANDOVER_FAILURE_MODE_DO_FALLBACK:DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY).build();
        }
        private void reject(Request request,DataServiceCallback cb,int cause){reply(cb,DataServiceCallback.RESULT_SUCCESS,failure(request,cause,-1));}
        private void setup(Request request,DataServiceCallback cb){
            if(retired.get()){reply(cb,DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE,null);return;}
            SubscriptionInfo selected=StackProfile.selectedSubscription(ModernIwlanDataService.this,getSlotIndex());
            if(selected==null){reply(cb,DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE,null);return;}
            if(request.rat!=AccessNetworkType.IWLAN||!"ims".equalsIgnoreCase(request.apn)){
                reply(cb,DataServiceCallback.RESULT_ERROR_UNSUPPORTED,null);return;
            }
            if(request.reason!=REQUEST_REASON_NORMAL&&request.reason!=REQUEST_REASON_HANDOVER||request.pdu<0||request.pdu>15){
                reply(cb,DataServiceCallback.RESULT_ERROR_INVALID_ARG,null);return;
            }
            // There is no URSP rule database. Do not invent a non-match-all rule.
            if(!request.matchAll){reject(request,cb,DataFailCause.MATCH_ALL_RULE_NOT_ALLOWED);return;}
            if(request.slice!=null){reject(request,cb,DataFailCause.SLICE_REJECTED);return;}
            if(request.pdu!=0||request.traffic!=null){reject(request,cb,DataFailCause.SERVICE_OPTION_NOT_SUPPORTED);return;}
            if(request.roaming&&!request.allowRoaming){reject(request,cb,DataFailCause.DATA_ROAMING_SETTINGS_DISABLED);return;}
            final EpdgAddressRequest wanted;
            try{wanted=request.addressIntent();}catch(IllegalArgumentException invalid){
                reply(cb,DataServiceCallback.RESULT_ERROR_INVALID_ARG,null);return;
            }
            if(sessionSub>=0&&sessionSub!=selected.getSubscriptionId())reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
            if(active!=null){
                if(sessionRequest.protocol!=request.protocol){reply(cb,DataServiceCallback.RESULT_ERROR_BUSY,null);return;}
                List<InetAddress> addresses=new ArrayList<>();for(LinkAddress a:active.getAddresses())addresses.add(a.getAddress());
                try{wanted.verifyAssigned(addresses);}catch(IllegalStateException changed){reject(request,cb,DataFailCause.ERROR_UNSPECIFIED);return;}
                reply(cb,DataServiceCallback.RESULT_SUCCESS,active);return;
            }
            if(session!=null){reply(cb,DataServiceCallback.RESULT_ERROR_BUSY,null);return;}
            cid=NEXT_CID.getAndIncrement();pending=cb;sessionRequest=request;addressIntent=wanted;
            sessionSub=selected.getSubscriptionId();final int expected=++generation;
            session=new EpdgSession(ModernIwlanDataService.this,getSlotIndex(),sessionSub,wanted,new EpdgSession.Listener(){
                @Override public void opened(String iface,List<LinkAddress> addresses,List<InetAddress> dns,List<InetAddress> pcscf){
                    dispatch(()->Provider.this.opened(expected,iface,addresses,dns,pcscf));
                }
                @Override public void closed(String reason){dispatch(()->failed(expected));}
            });
            Log.i(TAG,"slot="+getSlotIndex()+" setup=STARTED reason="+request.reason+" protocol="+request.protocol);
            session.start();
        }
        private void opened(int expected,String iface,List<LinkAddress> addresses,List<InetAddress> dns,List<InetAddress> pcscf){
            if(retired.get()||expected!=generation)return;
            SubscriptionInfo selected=StackProfile.selectedSubscription(ModernIwlanDataService.this,getSlotIndex());
            if(selected==null||selected.getSubscriptionId()!=sessionSub){reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);return;}
            try{
                List<InetAddress> assigned=new ArrayList<>(),gateways=new ArrayList<>();
                boolean v4=false,v6=false;
                for(LinkAddress a:addresses){assigned.add(a.getAddress());v4|=a.getAddress() instanceof Inet4Address;v6|=a.getAddress() instanceof Inet6Address;}
                addressIntent.verifyAssigned(assigned);
                if(iface==null||iface.isEmpty()||pcscf.isEmpty())throw new IllegalStateException("missing-network-parameters");
                if(v4)gateways.add(InetAddress.getByName("0.0.0.0"));if(v6)gateways.add(InetAddress.getByName("::"));
                int protocol=v4&&v6?ApnSetting.PROTOCOL_IPV4V6:v4?ApnSetting.PROTOCOL_IP:ApnSetting.PROTOCOL_IPV6;
                active=new DataCallResponse.Builder().setId(cid).setCause(DataFailCause.NONE)
                    .setLinkStatus(DataCallResponse.LINK_STATUS_ACTIVE).setProtocolType(protocol).setInterfaceName(iface)
                    .setAddresses(addresses).setDnsAddresses(dns).setPcscfAddresses(pcscf).setGatewayAddresses(gateways)
                    .setMtu(1280).setMtuV4(v4?1280:0).setMtuV6(v6?1280:0).setPduSessionId(0)
                    .setSuggestedRetryTime(-1).build();
                DataServiceCallback cb=pending;pending=null;reply(cb,DataServiceCallback.RESULT_SUCCESS,active);
                notifyDataCallListChanged(Collections.singletonList(active));
            }catch(Exception failure){failed(expected);}
        }
        private void failed(int expected){
            if(retired.get()||expected!=generation)return;
            DataServiceCallback cb=pending;Request request=sessionRequest;
            pending=null;reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
            if(request!=null)reply(cb,DataServiceCallback.RESULT_SUCCESS,failure(request,DataFailCause.ERROR_UNSPECIFIED,30000));
        }
        private void reset(int pendingResult){
            generation++;EpdgSession old=session;session=null;active=null;sessionSub=-1;sessionRequest=null;addressIntent=null;
            outgoingHandover=false;
            DataServiceCallback cb=pending;pending=null;if(old!=null)old.close();reply(cb,pendingResult,null);
            notifyDataCallListChanged(Collections.emptyList());
        }
        @Override public void deactivateDataCall(int id,int reason,DataServiceCallback cb){dispatch(()->{
            int result=retired.get()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:id==cid&&active!=null?DataServiceCallback.RESULT_SUCCESS:DataServiceCallback.RESULT_ERROR_INVALID_ARG;
            if(result==DataServiceCallback.RESULT_SUCCESS&&outgoingHandover&&reason!=REQUEST_REASON_HANDOVER&&reason!=REQUEST_REASON_SHUTDOWN)
                result=DataServiceCallback.RESULT_ERROR_BUSY;
            if(result==DataServiceCallback.RESULT_SUCCESS)reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);
            if(cb!=null)cb.onDeactivateDataCallComplete(result);
        });}
        private boolean activeOwned(){
            if(retired.get()||active==null)return false;
            SubscriptionInfo selected=StackProfile.selectedSubscription(ModernIwlanDataService.this,getSlotIndex());
            if(selected==null||selected.getSubscriptionId()!=sessionSub){reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE);return false;}
            return true;
        }
        @Override public void startHandover(int id,DataServiceCallback cb){dispatch(()->{
            int result=!activeOwned()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:id!=cid?DataServiceCallback.RESULT_ERROR_INVALID_ARG
                :outgoingHandover?DataServiceCallback.RESULT_ERROR_BUSY:DataServiceCallback.RESULT_SUCCESS;
            if(result==DataServiceCallback.RESULT_SUCCESS)outgoingHandover=true;
            if(cb!=null)cb.onHandoverStarted(result);
        });}
        @Override public void cancelHandover(int id,DataServiceCallback cb){dispatch(()->{
            int result=!activeOwned()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:id!=cid?DataServiceCallback.RESULT_ERROR_INVALID_ARG
                :!outgoingHandover?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:DataServiceCallback.RESULT_SUCCESS;
            if(result==DataServiceCallback.RESULT_SUCCESS)outgoingHandover=false;
            if(cb!=null)cb.onHandoverCancelled(result);
        });}
        @Override public void requestDataCallList(DataServiceCallback cb){dispatch(()->{
            if(active!=null)activeOwned();
            if(cb!=null)cb.onRequestDataCallListComplete(retired.get()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:DataServiceCallback.RESULT_SUCCESS,
                retired.get()||active==null?Collections.emptyList():Collections.singletonList(active));
        });}
        @Override public void setInitialAttachApn(DataProfile p,boolean r,DataServiceCallback cb){dispatch(()->{if(cb!=null)cb.onSetInitialAttachApnComplete(retired.get()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:DataServiceCallback.RESULT_SUCCESS);});}
        @Override public void setDataProfile(List<DataProfile> p,boolean r,DataServiceCallback cb){dispatch(()->{if(cb!=null)cb.onSetDataProfileComplete(retired.get()?DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:DataServiceCallback.RESULT_SUCCESS);});}
        @Override public void close(){synchronized(lifetimeLock){if(retired.compareAndSet(false,true))dispatch(()->reset(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE));}}
    }
}
