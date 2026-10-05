// SPDX-License-Identifier: GPL-2.0
// Android11 lifecycle adapter for phhusson/ims; feature-query approach informed by Suiying6023.
package me.phh.ims

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.telephony.ims.ImsService
import android.telephony.ims.feature.ImsFeature
import android.telephony.ims.feature.MmTelFeature
import android.telephony.ims.stub.ImsConfigImplBase
import android.telephony.ims.stub.ImsFeatureConfiguration
import android.telephony.ims.stub.ImsRegistrationImplBase

class PhhImsService : ImsService() {
    companion object { var instance: PhhImsService? = null }
    private val receiver = PhhImsBroadcastReceiver()
    private val features = mutableMapOf<Int,PhhMmTelFeature>()
    val mmTelFeature:PhhMmTelFeature? get()=features[1]
    private val registrations = mutableMapOf<Int,ImsRegistrationImplBase>()
    private val configs = mutableMapOf<Int,PhhImsConfig>()
    override fun onCreate() {
        super.onCreate()
        instance=this
        registerReceiver(receiver,IntentFilter(receiver.ALARM_PERIODIC_REGISTER),android.Manifest.permission.MODIFY_PHONE_STATE,null)
        android.util.Log.i("Api30PhhIms","service-created")
    }
    override fun querySupportedImsFeatures(): ImsFeatureConfiguration = ImsFeatureConfiguration.Builder()
        .addFeature(1,ImsFeature.FEATURE_MMTEL).build()
    override fun createMmTelFeature(slotId:Int):MmTelFeature {
        require(slotId==1)
        android.util.Log.i("Api30PhhIms","create-mmtel slot=$slotId")
        return features.getOrPut(slotId){PhhMmTelFeature(slotId)}
    }
    override fun getRegistration(slotId:Int):ImsRegistrationImplBase = registrations.getOrPut(slotId){ImsRegistrationImplBase()}
    override fun getConfig(slotId:Int):ImsConfigImplBase = configs.getOrPut(slotId){PhhImsConfig()}
    override fun readyForFeatureCreation(){instance=this}
    fun armPeriodicRegisterAlarm(){
        val pi=PendingIntent.getBroadcast(this,0,Intent(receiver.ALARM_PERIODIC_REGISTER).setPackage(packageName),PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+900_000,pi)
    }
    override fun onDestroy(){
        features.values.forEach{it.onFeatureRemoved()}
        unregisterReceiver(receiver)
        val pi=PendingIntent.getBroadcast(this,0,Intent(receiver.ALARM_PERIODIC_REGISTER).setPackage(packageName),PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java).cancel(pi)
        instance=null
        super.onDestroy()
    }
}
