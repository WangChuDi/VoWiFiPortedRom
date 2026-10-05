// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.*;
import java.util.*;

/** Root-authorized, same-boot per-slot selection. Contains no SIM identity. */
public final class StackProfile {
    private StackProfile(){}
    public static SubscriptionInfo subscription(Context context,int slot){
        SubscriptionManager manager=context.getSystemService(SubscriptionManager.class);
        if(manager==null)return null;
        List<SubscriptionInfo> active=manager.getActiveSubscriptionInfoList();
        if(active!=null)for(SubscriptionInfo info:active)if(info.getSimSlotIndex()==slot)return info;
        return null;
    }
    public static boolean enabled(Context context,int slot){return selectedSubscription(context,slot)!=null;}
    public static SubscriptionInfo selectedSubscription(Context context,int slot){
        SubscriptionInfo info=authorizedSubscription(context,slot);
        if(info==null)return null;
        try{
            TelephonyManager manager=context.getSystemService(TelephonyManager.class);
            return manager!=null&&manager.getSimState(slot)==TelephonyManager.SIM_STATE_READY
                &&"23415".equals(manager.createForSubscriptionId(info.getSubscriptionId()).getSimOperator())?info:null;
        }catch(RuntimeException error){return null;}
    }
    /** Identity/lease authorization without transient radio-readiness state.
     * Feature advertisement uses this; SIM authentication must use selectedSubscription.
     */
    public static SubscriptionInfo authorizedSubscription(Context context,int slot){
        if(slot<0||slot>7)return null;
        try{
            SubscriptionInfo info=subscription(context,slot);
            if(info==null)return null;
            String prefix="codex_wfc_stack_slot_"+slot+"_";
            int selected=Settings.Global.getInt(context.getContentResolver(),prefix+"sub",-1);
            long deadline;int selectedBoot;
            if(selected>=0){
                if(selected!=info.getSubscriptionId())return null;
                deadline=Settings.Global.getLong(context.getContentResolver(),prefix+"until",0);
                selectedBoot=Settings.Global.getInt(context.getContentResolver(),prefix+"boot",-1);
            }else{
                // Preserve the already-tested controller until per-slot transactions
                // are implemented; the legacy gate authorizes only slot1/sub1.
                if(slot!=1||info.getSubscriptionId()!=1)return null;
                deadline=Settings.Global.getLong(context.getContentResolver(),"codex_wfc_stack_trial_until",0);
                selectedBoot=Settings.Global.getInt(context.getContentResolver(),"codex_wfc_stack_trial_boot",-1);
            }
            if(!ProfileGate.permits(slot,info.getSubscriptionId(),selected,SystemClock.elapsedRealtime(),deadline,
                Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-2),selectedBoot))return null;
            return "234".equals(info.getMccString())&&"15".equals(info.getMncString())?info:null;
        }catch(RuntimeException error){return null;}
    }
    public static List<Integer> selectedSlots(Context context){
        List<Integer> result=new ArrayList<>();
        for(int slot=0;slot<8;slot++)if(enabled(context,slot))result.add(slot);
        return result;
    }
    public static List<Integer> authorizedSlots(Context context){
        List<Integer> result=new ArrayList<>();
        for(int slot=0;slot<8;slot++)if(authorizedSubscription(context,slot)!=null)result.add(slot);
        return result;
    }
}
