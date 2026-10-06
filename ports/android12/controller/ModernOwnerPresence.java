// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.telephony.*;
import java.util.*;

/** Missing inventory is unknown. Recovery may accept only an explicitly empty slot. */
final class ModernOwnerPresence {
    enum State { LIVE, ABSENT }
    interface Source { State observe() throws Exception; }
    private ModernOwnerPresence(){}
    static State observe(Context context,int slot,int sub,boolean test)throws Exception {
        List<SubscriptionInfo> active=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        int[][] tuples=null;
        if(active!=null){tuples=new int[active.size()][2];for(int i=0;i<active.size();i++){SubscriptionInfo info=active.get(i);if(info==null)throw new SecurityException("owner-inventory-unavailable");tuples[i][0]=info.getSimSlotIndex();tuples[i][1]=info.getSubscriptionId();}}
        TelephonyManager phone=context.getSystemService(TelephonyManager.class);
        int state=phone.getSimState(slot);
        // An absent slot needs no lookup through a stale subscription ID.
        String operator=state==TelephonyManager.SIM_STATE_READY?phone.createForSubscriptionId(sub).getSimOperator():null;
        return classify(tuples,state,operator,slot,sub,test);
    }
    static State classify(int[][] tuples,int simState,String operator,int slot,int sub,boolean test) {
        if(tuples==null||slot<0||slot>7||sub<0)throw new SecurityException("owner-inventory-unavailable");
        Set<Integer> slots=new HashSet<>(),subscriptions=new HashSet<>();boolean exact=false,conflict=false;
        for(int[] tuple:tuples){
            if(tuple==null||tuple.length!=2||tuple[0]<0||tuple[0]>7||tuple[1]<0||!slots.add(tuple[0])||!subscriptions.add(tuple[1]))throw new SecurityException("owner-inventory-ambiguous");
            if(tuple[0]==slot&&tuple[1]==sub)exact=true;
            else if(tuple[0]==slot||tuple[1]==sub)conflict=true;
        }
        if(conflict)throw new SecurityException("selected-subscription-changed");
        if(exact&&simState==TelephonyManager.SIM_STATE_READY&&operator!=null&&(test?operator.matches("[0-9]{5,6}")&&!"23415".equals(operator):"23415".equals(operator)))return State.LIVE;
        if(!exact&&simState==TelephonyManager.SIM_STATE_ABSENT)return State.ABSENT;
        throw new SecurityException("modern-owner-profile-refused");
    }
    static void requireLive(State state){if(state!=State.LIVE)throw new SecurityException("ready-owner-required");}
}
