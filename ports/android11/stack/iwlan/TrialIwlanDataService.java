// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import android.net.*;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telephony.DataFailCause;
import android.telephony.data.*;
import android.util.Log;
import java.net.*;
import java.util.*;

public final class TrialIwlanDataService extends DataService {
    private static final String TAG="Api30IwlanData";
    private EpdgSession diagnostic;
    @Override public void onCreate(){
        super.onCreate();
        // MIUI AutoLockOffClean can kill even a bound telephony service.
        // A visible foreground service plus the root supervisor protects the
        // process that owns the tunnel; no unrelated power policy is changed.
        String channel="vowifi_connection";
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(channel,"Wi-Fi 通话连接",NotificationManager.IMPORTANCE_LOW));
        startForeground(30,new Notification.Builder(this,channel)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Wi-Fi 通话连接")
            .setContentText("正在保持运营商 Wi-Fi 连接")
            .setOngoing(true).setOnlyAlertOnce(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"dev.codex.vowifi.iwlan.PROBE".equals(intent.getAction())&&diagnostic==null){
            diagnostic=new EpdgSession(this,1,new EpdgSession.Listener(){
                @Override public void opened(String iface,List<LinkAddress> a,List<InetAddress> d,List<InetAddress> p){
                    Log.i(TAG,"standalone-probe child=OPENED addresses="+a.size()+" pcscf="+p.size());
                    diagnostic.close();
                }
                @Override public void closed(String reason){Log.i(TAG,"standalone-probe closed="+reason);diagnostic=null;stopSelf(startId);}
            });
            diagnostic.start();
        }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){if(diagnostic!=null)diagnostic.close();super.onDestroy();}
    @Override public DataServiceProvider onCreateDataServiceProvider(int slot){return new Provider(slot);}
    private final class Provider extends DataServiceProvider {
        final Handler handler=new Handler(Looper.getMainLooper());
        final int cid;
        EpdgSession session;
        DataCallResponse active;
        DataServiceCallback pending;
        int generation;
        boolean closed;
        Provider(int slot){super(slot);cid=1000+slot;}
        @Override public void setupDataCall(int rat,DataProfile profile,boolean roaming,boolean allowRoaming,int reason,LinkProperties source,DataServiceCallback callback){
            handler.post(()->setup(profile,callback));
        }
        private void setup(DataProfile profile,DataServiceCallback callback){
            if(closed||getSlotIndex()!=1||profile==null||!"ims".equalsIgnoreCase(profile.getApn())){
                callback.onSetupDataCallComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED,null);return;
            }
            if(active!=null){callback.onSetupDataCallComplete(DataServiceCallback.RESULT_SUCCESS,active);return;}
            if(pending!=null){callback.onSetupDataCallComplete(DataServiceCallback.RESULT_ERROR_BUSY,null);return;}
            pending=callback;final int expected=++generation;
            session=new EpdgSession(TrialIwlanDataService.this,getSlotIndex(),new EpdgSession.Listener(){
                @Override public void opened(String iface,List<LinkAddress> addresses,List<InetAddress> dns,List<InetAddress> pcscf){
                    handler.post(()->{
                        if(closed||expected!=generation)return;
                        try{
                            active=new DataCallResponse.Builder().setId(cid).setCause(DataFailCause.NONE)
                                .setLinkStatus(DataCallResponse.LINK_STATUS_ACTIVE).setProtocolType(ApnSetting.PROTOCOL_IP)
                                .setInterfaceName(iface).setAddresses(addresses).setDnsAddresses(dns).setPcscfAddresses(pcscf)
                                .setGatewayAddresses(Collections.singletonList(InetAddress.getByName("0.0.0.0")))
                                .setMtu(1280).setSuggestedRetryTime(-1).build();
                            Log.i(TAG,"slot="+getSlotIndex()+" child=OPENED iface="+iface+" pcscf-count="+pcscf.size());
                            DataServiceCallback cb=pending;pending=null;
                            if(cb!=null)cb.onSetupDataCallComplete(DataServiceCallback.RESULT_SUCCESS,active);
                            notifyDataCallListChanged(Collections.singletonList(active));
                        }catch(Exception e){failed(expected,e.getClass().getSimpleName());}
                    });
                }
                @Override public void closed(String reason){handler.post(()->failed(expected,reason));}
            });
            Log.i(TAG,"slot="+getSlotIndex()+" setup=STARTED");session.start();
        }
        private void failed(int expected,String reason){
            if(expected!=generation||closed)return;
            Log.w(TAG,"slot="+getSlotIndex()+" closed="+reason);
            DataServiceCallback cb=pending;pending=null;active=null;
            EpdgSession old=session;session=null;generation++;
            if(old!=null)old.close();
            if(cb!=null)cb.onSetupDataCallComplete(DataServiceCallback.RESULT_SUCCESS,new DataCallResponse.Builder()
                .setId(cid).setCause(DataFailCause.ERROR_UNSPECIFIED).setSuggestedRetryTime(30000).build());
            notifyDataCallListChanged(Collections.emptyList());
        }
        @Override public void deactivateDataCall(int id,int reason,DataServiceCallback callback){handler.post(()->{
            if(id!=cid){callback.onDeactivateDataCallComplete(DataServiceCallback.RESULT_ERROR_INVALID_ARG);return;}
            generation++;if(session!=null)session.close();session=null;active=null;
            if(pending!=null){pending.onSetupDataCallComplete(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE,null);pending=null;}
            callback.onDeactivateDataCallComplete(DataServiceCallback.RESULT_SUCCESS);
            notifyDataCallListChanged(Collections.emptyList());
        });}
        @Override public void requestDataCallList(DataServiceCallback callback){handler.post(()->callback.onRequestDataCallListComplete(
            DataServiceCallback.RESULT_SUCCESS,active==null?Collections.emptyList():Collections.singletonList(active)));}
        @Override public void setInitialAttachApn(DataProfile p,boolean r,DataServiceCallback cb){cb.onSetInitialAttachApnComplete(DataServiceCallback.RESULT_SUCCESS);}
        @Override public void setDataProfile(List<DataProfile> p,boolean r,DataServiceCallback cb){cb.onSetDataProfileComplete(DataServiceCallback.RESULT_SUCCESS);}
        @Override public void close(){handler.post(()->{closed=true;generation++;if(session!=null)session.close();session=null;active=null;
            if(pending!=null){pending.onSetupDataCallComplete(DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE,null);pending=null;}});}
    }
}
