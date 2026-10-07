// SPDX-License-Identifier: GPL-2.0
// Android11 lifecycle adapter for phhusson/ims; feature-query approach informed by Suiying6023.
package me.phh.ims

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.telephony.ims.ImsService
import android.telephony.ims.ImsReasonInfo
import android.telephony.ims.feature.ImsFeature
import android.telephony.ims.feature.MmTelFeature
import android.telephony.ims.stub.ImsConfigImplBase
import android.telephony.ims.stub.ImsFeatureConfiguration
import android.telephony.ims.stub.ImsRegistrationImplBase
import dev.codex.vowifi.common.StackProfile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class RegistrationPhase { REGISTERING, REGISTERED, DOWN }

class PhhImsService : ImsService() {
    companion object {
        @Volatile var instance: PhhImsService? = null
        @JvmStatic fun clientCapabilityStatus(slot:Int,sub:Int,rebind:Boolean):Map<String,Any> =
            instance?.inspectClientCapabilities(slot,sub,rebind) ?: throw IllegalStateException("ims-service-unavailable")
    }
    private val receiver = PhhImsBroadcastReceiver()
    private val features = ConcurrentHashMap<Int,PhhMmTelFeature>()
    private val registrations = ConcurrentHashMap<Int,ImsRegistrationImplBase>()
    private val configs = ConcurrentHashMap<Int,PhhImsConfig>()
    private val subscriptions = ConcurrentHashMap<Int,Int>()
    private val handler=Handler(Looper.getMainLooper())
    private var controllerReady=false
    private var advertised=""
    private val updateFeatures=object:Runnable{
        override fun run(){
            if(controllerReady){
                val signature=StackProfile.authorizedSlots(this@PhhImsService).joinToString(","){
                    "$it:"+StackProfile.authorizedSubscription(this@PhhImsService,it)?.subscriptionId
                }
                if(signature!=advertised){
                    try{onUpdateSupportedImsFeatures(querySupportedImsFeatures());advertised=signature}
                    catch(error:Throwable){android.util.Log.w("Api30PhhIms","feature-update-error="+error.javaClass.simpleName)}
                }
            }
            handler.postDelayed(this,2000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        instance=this
        // Cancel the old unscoped requestCode0 alarm during the migration.
        cancelPeriodicRegisterAlarm(0)
        registerReceiver(receiver,IntentFilter(receiver.ALARM_PERIODIC_REGISTER),android.Manifest.permission.MODIFY_PHONE_STATE,null)
        android.util.Log.i("Api30PhhIms","service-created")
        handler.post(updateFeatures)
    }
    override fun querySupportedImsFeatures(): ImsFeatureConfiguration {
        val builder=ImsFeatureConfiguration.Builder()
        StackProfile.authorizedSlots(this).forEach{builder.addFeature(it,ImsFeature.FEATURE_MMTEL)}
        return builder.build()
    }
    @Synchronized
    override fun createMmTelFeature(slotId:Int):MmTelFeature? {
        val info=StackProfile.authorizedSubscription(this,slotId) ?: return null
        selectSubscription(slotId,info.subscriptionId)
        android.util.Log.i("Api30PhhIms","create-mmtel slot=$slotId")
        return features.getOrPut(slotId){PhhMmTelFeature(slotId,info.subscriptionId)}
    }
    @Synchronized
    override fun getRegistration(slotId:Int):ImsRegistrationImplBase = registrations.getOrPut(slotId){ImsRegistrationImplBase()}
    @Synchronized
    override fun getConfig(slotId:Int):ImsConfigImplBase = configs.getOrPut(slotId){PhhImsConfig()}
    // These signatures participate in virtual dispatch on newer ImsService
    // versions. API30 has no corresponding methods to annotate with override.
    @Synchronized
    fun createMmTelFeatureForSubscription(slotId:Int,subId:Int):MmTelFeature? {
        selectSubscription(slotId,subId)
        return createMmTelFeature(slotId)
    }
    @Synchronized
    fun getRegistrationForSubscription(slotId:Int,subId:Int):ImsRegistrationImplBase {
        selectSubscription(slotId,subId)
        return getRegistration(slotId)
    }
    @Synchronized
    fun getConfigForSubscription(slotId:Int,subId:Int):ImsConfigImplBase {
        selectSubscription(slotId,subId)
        return getConfig(slotId)
    }
    @Synchronized
    private fun selectSubscription(slotId:Int,subId:Int){
        require(StackProfile.authorizedSubscription(this,slotId)?.subscriptionId==subId)
        val previous=subscriptions.put(slotId,subId)
        if(previous!=null&&previous!=subId){
            features.remove(slotId)?.onFeatureRemoved()
            registrations.remove(slotId)?.onDeregistered(ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN,0,"subscription-ended"))
            configs.remove(slotId)
            cancelPeriodicRegisterAlarm(slotId)
        }
    }
    @Synchronized
    fun releaseFeature(slotId:Int,feature:PhhMmTelFeature){
        if(features.remove(slotId,feature)){
            registrations[slotId]?.onDeregistered(ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN,0,"feature-ended"))
            feature.reportRegistrationCapabilities(false)
            cancelPeriodicRegisterAlarm(slotId)
        }
    }
    // Preserve the same-subscription framework binder, but retire its old producer.
    @Synchronized
    fun publishRegistration(slotId:Int,subId:Int,feature:PhhMmTelFeature,
                            registration:ImsRegistrationImplBase,phase:RegistrationPhase):Boolean {
        if(features[slotId]!==feature || subscriptions[slotId]!=subId ||
           registrations[slotId]!==registration ||
           StackProfile.authorizedSubscription(this,slotId)?.subscriptionId!=subId){
            android.util.Log.i("Api30PhhIms","registration-callback=STALE slot=$slotId")
            return false
        }
        return feature.publishActiveRegistration(Runnable {
            when(phase){
                RegistrationPhase.REGISTERING -> registration.onRegistering(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN)
                RegistrationPhase.REGISTERED -> registration.onRegistered(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN)
                RegistrationPhase.DOWN -> registration.onDeregistered(ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN,0,"transport-ended"))
            }
            feature.reportRegistrationCapabilities(phase==RegistrationPhase.REGISTERED)
            if(phase==RegistrationPhase.REGISTERED)feature.scheduleCapabilityReplay()
            feature.telemetry.phase(dev.codex.vowifi.common.StackTelemetry.Phase.valueOf(phase.name))
            if(phase==RegistrationPhase.REGISTERED)armPeriodicRegisterAlarm(slotId)
            android.util.Log.i("Api30PhhIms","registration="+phase.name+
                (if(phase==RegistrationPhase.REGISTERED)" tech=IWLAN" else "")+" slot=$slotId")
        })
    }
    // Replay actual service state after SMS binding settles; never invent registration.
    @Synchronized
    fun republishCapabilities(slotId:Int,subId:Int,feature:PhhMmTelFeature):Boolean {
        if(features[slotId]!==feature||subscriptions[slotId]!=subId||
           StackProfile.authorizedSubscription(this,slotId)?.subscriptionId!=subId)return false
        return feature.publishActiveRegistration(Runnable { feature.republishRegistrationCapabilities() })
    }
    @Synchronized
    fun canRebindClients(slotId:Int,subId:Int,feature:PhhMmTelFeature):Boolean {
        if(features[slotId]!==feature||subscriptions[slotId]!=subId||
           StackProfile.selectedSubscription(this,slotId)?.subscriptionId!=subId||!feature.hasActiveRegistration())return false
        try{
            val tm=getSystemService(android.telephony.TelephonyManager::class.java) ?: return false
            val active=getSystemService(android.telephony.SubscriptionManager::class.java)?.activeSubscriptionInfoList ?: return false
            if(active.any{tm.createForSubscriptionId(it.subscriptionId).callState!=android.telephony.TelephonyManager.CALL_STATE_IDLE})return false
            if(features.values.any{it.clientCapabilities()["sms_session_idle"]!=true})return false
        }catch(unavailable:Throwable){return false}
        return true
    }
    @Synchronized
    private fun inspectClientCapabilities(slot:Int,sub:Int,rebind:Boolean):Map<String,Any>{
        require(StackProfile.selectedSubscription(this,slot)?.subscriptionId==sub&&subscriptions[slot]==sub)
        val feature=features[slot] ?: throw IllegalStateException("ims-feature-unavailable")
        var accepted=false
        if(rebind){
            require(android.os.Build.VERSION.SDK_INT==30&&android.os.Build.DEVICE=="raphael")
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            java.io.FileInputStream("/system/framework/telephony-common.jar").use{input->
                val block=ByteArray(16384)
                while(true){val n=input.read(block);if(n<0)break;digest.update(block,0,n)}
            }
            require(digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}=="6cc255f3cd8fe8f11191a1d2ec0bfddfdccf31d3851cdb3cd9282564871c3f74")
            require(canRebindClients(slot,sub,feature))
            accepted=feature.requestClientRebind()
        }
        return feature.clientCapabilities().also{it["generation"]=feature.telemetry.generation();it["rebind_requested"]=rebind;it["rebind_accepted"]=accepted}
    }
    override fun readyForFeatureCreation(){instance=this;controllerReady=true;handler.removeCallbacks(updateFeatures);handler.post(updateFeatures)}
    fun armPeriodicRegisterAlarm(slotId:Int){
        require(slotId in 0..7)
        val pi=PendingIntent.getBroadcast(this,slotId,Intent(receiver.ALARM_PERIODIC_REGISTER).setPackage(packageName).putExtra("slot_id",slotId),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+900_000,pi)
    }
    private fun cancelPeriodicRegisterAlarm(slotId:Int){
        val pi=PendingIntent.getBroadcast(this,slotId,Intent(receiver.ALARM_PERIODIC_REGISTER).setPackage(packageName),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return
        getSystemService(AlarmManager::class.java).cancel(pi);pi.cancel()
    }
    fun refreshRegistration(slotId:Int){
        val selected=StackProfile.selectedSubscription(this,slotId) ?: return
        if(subscriptions[slotId]!=selected.subscriptionId)return
        val feature=features[slotId] ?: return
        armPeriodicRegisterAlarm(slotId)
        CoroutineScope(Dispatchers.IO).launch{
            try{feature.sipHandler.refreshRegistration()}
            catch(error:Throwable){android.util.Log.w("Api30PhhIms","refresh-error slot=$slotId type="+error.javaClass.simpleName)}
        }
    }
    @Synchronized
    override fun onDestroy(){
        controllerReady=false
        handler.removeCallbacksAndMessages(null)
        features.values.toList().forEach{it.onFeatureRemoved()}
        features.clear();registrations.clear();configs.clear();subscriptions.clear()
        unregisterReceiver(receiver)
        for(slot in 0..7)cancelPeriodicRegisterAlarm(slot)
        if(instance===this)instance=null
        super.onDestroy()
    }
}
