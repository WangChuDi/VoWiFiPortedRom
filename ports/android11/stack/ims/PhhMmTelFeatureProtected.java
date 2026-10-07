// SPDX-License-Identifier: GPL-2.0
// Adapted from the preserved phhusson/ims snapshot; modified 2026-10-07.
package me.phh.ims;

import android.os.RemoteCallbackList;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Java bridge for the protected capability proxy; no subscriber or SMS data. */
public class PhhMmTelFeatureProtected extends MmTelFeature {
    private final int selectedSlot;
    private int enabledMask=MmTelCapabilities.CAPABILITY_TYPE_VOICE|MmTelCapabilities.CAPABILITY_TYPE_SMS;
    private boolean registered;
    private int lastNotifiedMask;
    private long notificationReturns,smsReadyEvents,enableEvents,disableEvents;
    public PhhMmTelFeatureProtected(int slot){selectedSlot=slot;}

    public synchronized void reportRegistrationCapabilities(boolean active){
        registered=active;
        MmTelCapabilities value=new MmTelCapabilities();
        if(active)value.addCapabilities(enabledMask);
        notifyCapabilitiesStatusChanged(value);
        lastNotifiedMask=active?enabledMask:0;
        notificationReturns++;
    }
    public synchronized void republishRegistrationCapabilities(){
        reportRegistrationCapabilities(registered);
        android.util.Log.i("Api30PhhIms","capability-replay slot="+selectedSlot+" registered="+registered+" mask="+enabledMask);
    }
    public synchronized void recordSmsReady(){smsReadyEvents++;}
    public synchronized boolean hasActiveRegistration(){return registered;}
    public synchronized Map<String,Object> capabilitySnapshot(){
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("registered",registered);value.put("enabled_mask",enabledMask);
        value.put("last_notified_mask",lastNotifiedMask);value.put("notification_returns",notificationReturns);
        value.put("sms_ready_events",smsReadyEvents);value.put("enable_events",enableEvents);value.put("disable_events",disableEvents);
        int count=-1;
        try{
            Field field=ImsFeature.class.getDeclaredField("mCapabilityCallbacks");field.setAccessible(true);
            Object callbacks=field.get(this);
            if(callbacks instanceof RemoteCallbackList)count=((RemoteCallbackList<?>)callbacks).getRegisteredCallbackCount();
        }catch(ReflectiveOperationException|RuntimeException unavailable){}
        value.put("callback_count_observed",count>=0);
        if(count>=0)value.put("capability_callback_count",count);
        // A returned notification call does not prove each remote client received it.
        return value;
    }
    @Override public synchronized void changeEnabledCapabilities(CapabilityChangeRequest request,
            ImsFeature.CapabilityCallbackProxy callback){
        for(CapabilityChangeRequest.CapabilityPair pair:request.getCapabilitiesToEnable())
            if(pair.getRadioTech()==ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN){enabledMask|=pair.getCapability();enableEvents++;}
        for(CapabilityChangeRequest.CapabilityPair pair:request.getCapabilitiesToDisable())
            if(pair.getRadioTech()==ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN){enabledMask&=~pair.getCapability();disableEvents++;}
        reportRegistrationCapabilities(registered);
        android.util.Log.i("Api30PhhIms","capability-change slot="+selectedSlot+" mask="+enabledMask+" registered="+registered);
    }
}
