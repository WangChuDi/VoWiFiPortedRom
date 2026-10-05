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
import java.util.UUID

/** Privileged entry. TEST_INFO sends only the authorized INFO->85075 probe. */
class StackCheckService:Service(){
    private var current:CheckRun?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        // Repeated starts cannot replace an in-flight test or send a second SMS.
        if(current?.testing==true){Log.i(TAG,"request-rejected=test-active");return START_NOT_STICKY}
        current?.close();current=null
        try{
            val action=intent?.action
            require(action==null||action==READ||action==TEST_INFO)
            val testing=action==TEST_INFO
            if(testing)require(intent!!.hasExtra("slot")&&intent.hasExtra("expectedSubId"))
            // Legacy read-only callers may inspect SIM2; network traffic never defaults.
            val slot=intent?.getIntExtra("slot",1)?:1
            require(slot in 0..7)
            val info=getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfoForSimSlotIndex(slot)
            require(info!=null)
            val sub=intent?.getIntExtra("expectedSubId",info.subscriptionId)?:info.subscriptionId
            require(sub>=0&&info.subscriptionId==sub)
            val next=CheckRun(slot,sub,testing)
            require(next.identityMatches())
            current=next;next.start()
        }catch(t:Throwable){
            Log.w(TAG,"request-rejected="+t.javaClass.simpleName)
            current?.close();current=null;stopSelf()
        }
        return START_NOT_STICKY
    }

    private inner class CheckRun(val slot:Int,val sub:Int,val testing:Boolean){
        private val handler=Handler(Looper.getMainLooper())
        private val manager=ImsMmTelManager.createForSubscriptionId(sub)
        private var registration:ImsMmTelManager.RegistrationCallback?=null
        private var capability:ImsMmTelManager.CapabilityCallback?=null
        private var epoch=0
        private var alive=true
        private var registered=false
        private var smsCapable=false
        private var pendingInfo=testing
        private var awaitingResult=false
        private var resultRegistered=false
        private var sent:PendingIntent?=null
        private val resultAction=TEST_INFO_RESULT+"."+UUID.randomUUID().toString()
        private val result=object:BroadcastReceiver(){
            override fun onReceive(context:Context,intent:Intent){
                if(!active()||!awaitingResult||intent.action!=resultAction)return
                if(!identityMatches()){finish("subscription-changed");return}
                Log.i(TAG,"sms-send-result=$resultCode network-error="+intent.getIntExtra("errorCode",0)+" slot=$slot sub=$sub")
                finish("result-received")
            }
        }
        fun identityMatches():Boolean=try{
            val info=getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfoForSimSlotIndex(slot)
            val tm=getSystemService(TelephonyManager::class.java)
            info!=null&&info.subscriptionId==sub&&tm.getSimState(slot)==TelephonyManager.SIM_STATE_READY&&
                tm.createForSubscriptionId(sub).simOperator=="23415"
        }catch(_:Throwable){false}
        private fun active()=alive&&current===this
        private fun callbackAllowed(value:Int):Boolean{
            if(!active()||value!=epoch)return false
            if(!identityMatches()){finish("subscription-changed");return false}
            return true
        }
        fun start(){
            attachCallbacks()
            handler.postDelayed(object:Runnable{
                override fun run(){
                    if(!active())return
                    if(!identityMatches()){finish("subscription-changed");return}
                    if(pendingInfo&&(!registered||!smsCapable)){detachCallbacks();attachCallbacks()}
                    handler.postDelayed(this,15000)
                }
            },15000)
            handler.postDelayed({if(active())finish("timeout")},if(testing)180000 else 60000)
        }
        private fun detachCallbacks(){
            ++epoch
            registration?.let{try{manager.unregisterImsRegistrationCallback(it)}catch(_:Throwable){}}
            capability?.let{try{manager.unregisterMmTelCapabilityCallback(it)}catch(_:Throwable){}}
            registration=null;capability=null;registered=false;smsCapable=false
        }
        private fun attachCallbacks(){
            val value=++epoch
            val r=object:ImsMmTelManager.RegistrationCallback(){
                override fun onRegistered(transport:Int){
                    if(!callbackAllowed(value))return
                    registered=transport==2
                    Log.i(TAG,"framework-registered transport=$transport slot=$slot sub=$sub")
                    maybeSendInfo()
                }
                override fun onRegistering(transport:Int){
                    if(!callbackAllowed(value))return
                    registered=false;Log.i(TAG,"framework-registering transport=$transport slot=$slot sub=$sub")
                }
                override fun onUnregistered(reason:android.telephony.ims.ImsReasonInfo){
                    if(!callbackAllowed(value))return
                    registered=false;Log.i(TAG,"framework-unregistered code="+reason.code+" slot=$slot sub=$sub")
                }
            }
            val c=object:ImsMmTelManager.CapabilityCallback(){
                override fun onCapabilitiesStatusChanged(c:MmTelFeature.MmTelCapabilities){
                    if(!callbackAllowed(value))return
                    smsCapable=c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS)
                    Log.i(TAG,"framework-capabilities voice="+c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)+" sms=$smsCapable slot=$slot sub=$sub")
                    maybeSendInfo()
                }
            }
            registration=r;capability=c
            try{
                manager.registerImsRegistrationCallback(mainExecutor,r)
                manager.registerMmTelCapabilityCallback(mainExecutor,c)
            }catch(t:Throwable){Log.w(TAG,"callback-attach-error="+t.javaClass.simpleName);detachCallbacks()}
        }
        private fun maybeSendInfo(){
            if(!active()||!pendingInfo||!registered||!smsCapable)return
            if(!identityMatches()){finish("subscription-changed");return}
            val prefs=getSharedPreferences("test-consent-use",MODE_PRIVATE)
            val remaining=120000-(System.currentTimeMillis()-prefs.getLong("last-info",0))
            if(remaining>0){handler.postDelayed({maybeSendInfo()},remaining+50);return}
            pendingInfo=false // Set before Binder calls: this run cannot send twice.
            try{
                check(prefs.edit().putLong("last-info",System.currentTimeMillis()).commit())
                registerReceiver(result,IntentFilter(resultAction),android.Manifest.permission.MODIFY_PHONE_STATE,null)
                resultRegistered=true
                sent=PendingIntent.getBroadcast(this@StackCheckService,0,Intent(resultAction).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
                check(identityMatches())
                awaitingResult=true
                SmsManager.getSmsManagerForSubscriptionId(sub).sendTextMessage("85075",null,"INFO",sent,null)
                Log.i(TAG,"sms-send-requested destination=85075 slot=$slot sub=$sub")
            }catch(t:Throwable){Log.w(TAG,"sms-send-error="+t.javaClass.simpleName);finish("send-error")}
        }
        private fun finish(reason:String){
            if(!active())return
            Log.i(TAG,"check-finished=$reason slot=$slot sub=$sub")
            close();current=null;stopSelf()
        }
        fun close(){
            if(!alive)return
            alive=false;handler.removeCallbacksAndMessages(null);detachCallbacks()
            if(resultRegistered){try{unregisterReceiver(result)}catch(_:Throwable){};resultRegistered=false}
            sent?.cancel();sent=null
        }
    }
    override fun onDestroy(){current?.close();current=null;super.onDestroy()}
    companion object{
        private const val TAG="Api30StackCheck"
        private const val READ="me.phh.ims.CHECK"
        private const val TEST_INFO="me.phh.ims.TEST_INFO"
        private const val TEST_INFO_RESULT="me.phh.ims.TEST_INFO_RESULT"
    }
}
