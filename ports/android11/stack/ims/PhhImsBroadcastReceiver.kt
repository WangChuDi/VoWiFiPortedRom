// SPDX-License-Identifier: GPL-2.0
package me.phh.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Per-slot refresh; a missing slot never silently selects another SIM. */
class PhhImsBroadcastReceiver : BroadcastReceiver(){
    val ALARM_PERIODIC_REGISTER="me.phh.ims.ALARM_PERIODIC_REGISTER"
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=ALARM_PERIODIC_REGISTER)return
        val slot=intent.getIntExtra("slot_id",-1)
        if(slot !in 0..7)return
        PhhImsService.instance?.refreshRegistration(slot)
    }
}
