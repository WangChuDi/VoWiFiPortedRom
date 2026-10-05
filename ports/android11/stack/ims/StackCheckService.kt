// SPDX-License-Identifier: GPL-2.0
package me.phh.ims

import android.app.Service
import android.app.PendingIntent
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.ims.ImsMmTelManager
import android.telephony.ims.feature.MmTelFeature
import android.util.Log

/** Root/phone-privileged entry; network SMS only for the user-authorized fixed INFO test. */
class StackCheckService:Service(){
    private val handler=Handler(Looper.getMainLooper())
    private var manager:ImsMmTelManager?=null
    private var resultRegistered=false
    private var registered=false
    private var smsCapable=false
    private var pendingInfo=false
    private var selectedSub=-1
    private val registration=object:ImsMmTelManager.RegistrationCallback(){
        override fun onRegistered(transport:Int){
            registered=transport==2
            Log.i("Api30StackCheck","framework-registered transport=$transport")
            maybeSendInfo()
        }
        override fun onRegistering(transport:Int){registered=false;Log.i("Api30StackCheck","framework-registering transport=$transport")}
        override fun onUnregistered(reason:android.telephony.ims.ImsReasonInfo){registered=false;Log.i("Api30StackCheck","framework-unregistered code="+reason.code)}
    }
    private val capability=object:ImsMmTelManager.CapabilityCallback(){
        override fun onCapabilitiesStatusChanged(c:MmTelFeature.MmTelCapabilities){
            smsCapable=c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS)
            Log.i("Api30StackCheck","framework-capabilities voice="+c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)+" sms="+c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS))
            maybeSendInfo()
        }
    }
    private val result=object:BroadcastReceiver(){
        override fun onReceive(context:Context,intent:Intent){
            Log.i("Api30StackCheck","sms-send-result=$resultCode network-error="+intent.getIntExtra("errorCode",0))
            stopSelf()
        }
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        val info=getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfoForSimSlotIndex(1)
        require(info!=null)
        val tm=getSystemService(TelephonyManager::class.java).createForSubscriptionId(info.subscriptionId)
        require(tm.simOperator=="23415")
        selectedSub=info.subscriptionId
        pendingInfo=intent?.action=="me.phh.ims.TEST_INFO"
        detachCallbacks()
        manager=ImsMmTelManager.createForSubscriptionId(info.subscriptionId)
        attachCallbacks()
        handler.postDelayed(object:Runnable{
            override fun run(){
                if(pendingInfo&&(!registered||!smsCapable)){
                    detachCallbacks();attachCallbacks()
                    handler.postDelayed(this,15000)
                }
            }
        },15000)
        maybeSendInfo()
        handler.postDelayed({stopSelf(startId)},if(pendingInfo||resultRegistered)180000 else 60000)
        return START_NOT_STICKY
    }
    private fun detachCallbacks(){
        manager?.let{try{it.unregisterImsRegistrationCallback(registration);it.unregisterMmTelCapabilityCallback(capability)}catch(_:Throwable){}}
        registered=false;smsCapable=false
    }
    private fun attachCallbacks(){
        try{
            manager?.registerImsRegistrationCallback(mainExecutor,registration)
            manager?.registerMmTelCapabilityCallback(mainExecutor,capability)
        }catch(t:Throwable){Log.w("Api30StackCheck","callback-attach-error="+t.javaClass.simpleName)}
    }
    private fun maybeSendInfo(){
        if(pendingInfo&&registered&&smsCapable){
            val prefs=getSharedPreferences("test-consent-use",MODE_PRIVATE)
            val last=prefs.getLong("last-info",0)
            val remaining=120000-(System.currentTimeMillis()-last)
            if(remaining>0){handler.postDelayed({maybeSendInfo()},remaining+50);return}
            pendingInfo=false
            prefs.edit().putLong("last-info",System.currentTimeMillis()).commit()
            registerReceiver(result,IntentFilter("me.phh.ims.TEST_INFO_RESULT"),android.Manifest.permission.MODIFY_PHONE_STATE,null)
            resultRegistered=true
            val sent=PendingIntent.getBroadcast(this,0,Intent("me.phh.ims.TEST_INFO_RESULT").setPackage(packageName),PendingIntent.FLAG_IMMUTABLE)
            SmsManager.getSmsManagerForSubscriptionId(selectedSub).sendTextMessage("85075",null,"INFO",sent,null)
            Log.i("Api30StackCheck","sms-send-requested destination=85075")
        }
    }
    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        detachCallbacks()
        if(resultRegistered)unregisterReceiver(result)
        super.onDestroy()
    }
}
