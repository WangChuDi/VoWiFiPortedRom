"""Generate experimental sources from the preserved, attributed API30 snapshot."""
from pathlib import Path
import argparse, shutil, difflib
B=Path(__file__).resolve().parent
src=B.parent/'vendor/phhusson-ims/app/src/main/java'
arguments=argparse.ArgumentParser(description=__doc__)
arguments.add_argument('--variant',choices=('api30','api31'),default='api30')
variant=arguments.parse_args().variant
dest=(B/'out/ims-src') if variant=='api30' else (B.parent.parent/'android12/out/ims-src')
# Fixed variant destinations keep modern generation outside the API30 build.
# Refuse directory aliases before the recursive generated-source replacement.
if dest.resolve()!=dest.absolute():raise RuntimeError('IMS-generated-output-alias-refused')
dest.parent.mkdir(parents=True,exist_ok=True)
if dest.exists(): shutil.rmtree(dest)
shutil.copytree(src,dest)
for p in dest.rglob('*'):
    if not p.is_file(): continue
    t=p.read_text(encoding='utf-8')
    t=t.replace('import android.telephony.Rlog','import me.phh.ims.PortLog as Rlog' if p.suffix=='.kt' else 'import me.phh.ims.PortLog')
    if p.suffix=='.java':t=t.replace('Rlog.','PortLog.')
    t=t.replace('android.util.Log.e(', 'me.phh.ims.PortLog.e(')
    p.write_text(t,encoding='utf-8',newline='\n')
for name in ('PhhImsService.kt','PhhImsBroadcastReceiver.kt','Rnnoise.kt','PortLog.java','StackCheckService.kt','RegistrationCallbackGate.java'):
    shutil.copyfile(B/'ims'/name,dest/'me/phh/ims'/name)
shutil.copyfile(B/'ims/Api30SipTcpServer.kt',dest/'me/phh/sip/Api30SipTcpServer.kt')
shutil.copyfile(B/'ims/RpDeliveryError.kt',dest/'me/phh/sip/RpDeliveryError.kt')
shutil.copytree(B/'common',dest/'dev/codex/vowifi/common')

def edit(rel,old,new,expected=1):
    p=dest/rel;t=p.read_text(encoding='utf-8')
    if t.count(old)!=expected:raise RuntimeError('Patch context changed: '+rel+' '+old[:60])
    p.write_text(t.replace(old,new,expected),encoding='utf-8',newline='\n')

feature='me/phh/ims/PhhMmTelFeature.kt'
edit(feature,'class PhhMmTelFeature(val slotId: Int) :','class PhhMmTelFeature(val slotId: Int, private val selectedSubId:Int) :')
edit(feature,'return PhhMmTelFeature(slotId)','require(slotId==this.slotId)\n        return PhhMmTelFeature(slotId,selectedSubId)')
edit(feature,'sipHandler = SipHandler(imsService, slotId)','sipHandler = SipHandler(imsService, slotId, selectedSubId)')
edit(feature,'val imsService = PhhImsService.Companion.instance!!','''val imsService = PhhImsService.Companion.instance!!
        val imsRegistration=imsService.getRegistrationForSubscription(slotId,selectedSubId)''')
edit(feature,'imsService.getRegistration(slotId)','imsRegistration',expected=3)
edit(feature,'import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE','import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN')
p=dest/feature;t=p.read_text(encoding='utf-8').replace('REGISTRATION_TECH_LTE','REGISTRATION_TECH_IWLAN')
t=t.replace('imsRegistration.onDeregistered(null)', 'imsService.publishRegistration(slotId,selectedSubId,this,imsRegistration,RegistrationPhase.DOWN)')
t=t.replace('imsRegistration.onRegistered(REGISTRATION_TECH_IWLAN)', 'imsService.publishRegistration(slotId,selectedSubId,this,imsRegistration,RegistrationPhase.REGISTERED)')
t=t.replace('imsRegistration.onRegistering(REGISTRATION_TECH_IWLAN)', 'imsService.publishRegistration(slotId,selectedSubId,this,imsRegistration,RegistrationPhase.REGISTERING)')
t=t.replace('Rlog.d(TAG, "$slotId onFeatureRemoved")','Rlog.d(TAG, "$slotId onFeatureRemoved")\n        if(this::sipHandler.isInitialized) sipHandler.shutdown()\n        PhhImsService.instance?.releaseFeature(slotId,this)')
p.write_text(t,encoding='utf-8',newline='\n')
edit(feature,'    lateinit var sipHandler: SipHandler','''    lateinit var sipHandler: SipHandler
    val telemetry=dev.codex.vowifi.common.StackTelemetry.begin("ims",slotId,selectedSubId,java.util.function.LongSupplier { android.os.SystemClock.elapsedRealtime() })
    init { imsSms.telemetry=telemetry }
    private val readinessHandler=android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var removed=false
    private val registrationCallbacks=RegistrationCallbackGate()
    fun publishActiveRegistration(action:Runnable):Boolean=registrationCallbacks.publish(action)
    private var initialized=false
    private val retryReady=Runnable{if(!removed)onFeatureReady()}
    private var callListener: ImsCallSessionListener? = null
    private var outgoingCallProfile: ImsCallProfile? = null
    private var activeCallState = ImsCallSessionImplBase.State.IDLE
    private fun ended(reason:ImsReasonInfo) {
        if(activeCallState==ImsCallSessionImplBase.State.TERMINATED) return
        activeCallState=ImsCallSessionImplBase.State.TERMINATED
        callListener?.callSessionTerminated(reason)
    }''')
edit(feature,'                sipHandler.call(callee)','''                callListener=mListener
                outgoingCallProfile=profile
                activeCallState=State.INITIATED
                sipHandler.call(callee)''')
edit(feature,'            private val mCallId = randomBytes(12).toHex()','''            private val mCallId = randomBytes(12).toHex()
            override fun getCallProfile():ImsCallProfile=profile
            override fun getLocalCallProfile():ImsCallProfile=profile
            override fun getRemoteCallProfile():ImsCallProfile=profile''')
edit(feature,'                return State.ESTABLISHED','                return activeCallState')
edit(feature,'                return true','                return activeCallState==State.ESTABLISHED')
edit(feature,'                mListener.callSessionTerminated(ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0, "Kikoo"))','''                sipHandler.terminateCall()
                ended(ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED,0,"terminated"))''')
edit(feature,'        var callListener: ImsCallSessionListener? = null','''        sipHandler.onCallConnected = {
            activeCallState=ImsCallSessionImplBase.State.ESTABLISHED
            callListener?.callSessionInitiated(outgoingCallProfile ?: ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,ImsCallProfile.CALL_TYPE_VOICE))
        }''')
edit(feature,'        sipHandler.onCallConnected = {','''        sipHandler.onCallProgressing = {
            callListener?.callSessionProgressing(outgoingCallProfile?.mediaProfile ?: ImsStreamMediaProfile())
        }
        sipHandler.onCallConnected = {''')
edit(feature,'                    Rlog.d(TAG, "Rejecting call $reason")','''                    Rlog.d(TAG, "Rejecting call $reason")
                    mState=State.TERMINATED
                    ended(ImsReasonInfo(ImsReasonInfo.CODE_USER_DECLINE,0,"declined"))''')
edit(feature,'                    Rlog.d(TAG, "Terminating call")','''                    Rlog.d(TAG, "Terminating call")
                    mState=State.TERMINATED
                    ended(ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED,0,"terminated"))''')
edit(feature,'            callProfile.setCallExtra(ImsCallProfile.EXTRA_OI, from)','''            activeCallState=ImsCallSessionImplBase.State.INITIATED
            callProfile.setCallExtra(ImsCallProfile.EXTRA_OI, from)''')
edit(feature,'                    mState = State.ESTABLISHED','''                    mState = State.ESTABLISHED
                    activeCallState=State.ESTABLISHED''')
edit(feature,'                callListener?.callSessionTerminated(ImsReasonInfo(ImsReasonInfo.CODE_NETWORK_REJECT, 0, statusMessage))','                ended(ImsReasonInfo(ImsReasonInfo.CODE_NETWORK_REJECT,0,statusMessage))')
edit(feature,'                callListener?.callSessionTerminated(\n                    ImsReasonInfo(','                ended(\n                    ImsReasonInfo(')
edit(feature,'        sipHandler.onSmsReceived = imsSms::onSmsReceived','''        sipHandler.onSmsReceived = { token,format,pdu ->
            telemetry.add(dev.codex.vowifi.common.StackTelemetry.Counter.SMS_RX,1)
            android.util.Log.i("Api30PhhIms","sms-received token=$token bytes=${pdu.size}")
            imsSms.onSmsReceived(token,format,pdu)
        }''')
edit(feature,'        if(this::sipHandler.isInitialized) return','''        if(removed||initialized)return
        readinessHandler.removeCallbacks(retryReady)
        val owner=PhhImsService.instance ?: return
        if(dev.codex.vowifi.common.StackProfile.selectedSubscription(owner,slotId)?.subscriptionId!=selectedSubId){
            readinessHandler.postDelayed(retryReady,2000)
            return
        }
        try { onFeatureReadyInner();initialized=true;setFeatureState(ImsFeature.STATE_READY) } catch(t:Throwable) {
            android.util.Log.e("Api30PhhIms","feature-ready-error="+t.javaClass.simpleName)
            setFeatureState(ImsFeature.STATE_UNAVAILABLE)
        }
    }
    private fun onFeatureReadyInner(){
        if(this::sipHandler.isInitialized) return''')
edit(feature,'        if(this::sipHandler.isInitialized) sipHandler.shutdown()','''        removed=true
        readinessHandler.removeCallbacksAndMessages(null)
        registrationCallbacks.close()
        telemetry.end(false)
        if(this::sipHandler.isInitialized) sipHandler.shutdown()''')
sms='me/phh/ims/PhhImsSms.kt'
edit(sms,'    lateinit var sipHandler: SipHandler','    lateinit var sipHandler: SipHandler\n    var telemetry: dev.codex.vowifi.common.StackTelemetry.Owner? = null')
edit(sms,'        try {','''        val recorded=java.util.concurrent.atomic.AtomicBoolean(false)
        fun recordOutcome(success:Boolean) {
            if(recorded.compareAndSet(false,true))telemetry?.add(if(success)dev.codex.vowifi.common.StackTelemetry.Counter.SMS_TX_OK else dev.codex.vowifi.common.StackTelemetry.Counter.SMS_TX_FAILED,1)
        }
        telemetry?.add(dev.codex.vowifi.common.StackTelemetry.Counter.SMS_TX,1)
        try {''')
edit(sms,'            if (format != "3gpp") {','            if (format != "3gpp") {\n                recordOutcome(false)')
edit(sms,'            if (::sipHandler.isInitialized == false) {','            if (::sipHandler.isInitialized == false) {\n                recordOutcome(false)')
edit(sms,'        } catch(t: Throwable) {','        } catch(t: Throwable) {\n            recordOutcome(false)')
edit(sms,'            // called when android tries to send a sms?','''            android.util.Log.i("Api30PhhIms","framework-send-sms token=$token format=$format")
            // called when android tries to send a sms?''')
edit(sms,'                    // success cb','''                    android.util.Log.i("Api30PhhIms","framework-send-sms=SUCCESS token=$token")
                    recordOutcome(true)
                    // success cb''')
edit(sms,'                    // XXX better error code','''                    android.util.Log.i("Api30PhhIms","framework-send-sms=FAILED token=$token")
                    recordOutcome(false)
                    // XXX better error code''')
edit(sms,'        sipHandler.sendSmsAck(token, messageRef, error)','        sipHandler.sendSmsAck(token, messageRef, result)')
edit(sms,'        // called when android acks a received sms','''        android.util.Log.i("Api30PhhIms","framework-sms-ack token=$token result=$result")
        telemetry?.add(if(result==1)dev.codex.vowifi.common.StackTelemetry.Counter.SMS_ACK_OK else dev.codex.vowifi.common.StackTelemetry.Counter.SMS_ACK_FAILED,1)
        // called when android acks a received sms''')
edit(sms,'        // should not do anything before this is called','''        android.util.Log.i("Api30PhhIms","framework-sms=READY")
        // should not do anything before this is called''')
shim='me/phh/ims/PhhMmTelFeatureProtected.java'
p=dest/shim;t=p.read_text(encoding='utf-8').replace('REGISTRATION_TECH_LTE','REGISTRATION_TECH_IWLAN')
t=t.replace('public class PhhMmTelFeatureProtected extends MmTelFeature {','''public class PhhMmTelFeatureProtected extends MmTelFeature {
    private boolean registered;
    public void reportRegistrationCapabilities(boolean active) {
        registered = active;
        MmTelFeature.MmTelCapabilities value = new MmTelFeature.MmTelCapabilities();
        if (active) value.addCapabilities(capabilities);
        notifyCapabilitiesStatusChanged(value);
    }''')
t=t.replace('capabilities.addCapabilities(this.capabilities);','if (registered) capabilities.addCapabilities(this.capabilities);')
t=t.replace('Rlog.d(TAG, "Final capabilities: " + this.capabilities);','android.util.Log.i("Api30PhhIms", "capability-change slot="+slotId+" mask="+this.capabilities+" registered="+registered);')
p.write_text(t,encoding='utf-8',newline='\n')

sip='me/phh/sip/SipHandler.kt'
edit(sip,'class SipHandler(val ctxt: Context, slotId: Int) {','class SipHandler(val ctxt: Context, slotId: Int, expectedSubId:Int) {')
edit(sip,'    private var imsReady = false','    var telemetry: dev.codex.vowifi.common.StackTelemetry.Owner? = null\n    private var imsReady = false')
edit(feature,'sipHandler = SipHandler(imsService, slotId, selectedSubId)','sipHandler = SipHandler(imsService, slotId, selectedSubId)\n        sipHandler.telemetry=telemetry')
edit(sip,'    fun registerCallback(response: SipResponse): Boolean {','    fun registerCallback(response: SipResponse): Boolean {\n        telemetry?.sipResponse(response.statusCode)')
edit(sip,'        registerCounter += 1','        telemetry?.add(dev.codex.vowifi.common.StackTelemetry.Counter.REGISTER_TX,1)\n        registerCounter += 1')
edit(sip,'        if (plainRegReply !is SipResponse || plainRegReply.statusCode != 401) {','        if(plainRegReply is SipResponse)telemetry?.sipResponse(plainRegReply.statusCode)\n        if (plainRegReply !is SipResponse || plainRegReply.statusCode != 401) {')
edit(sip,'        if (regReply !is SipResponse || regReply.statusCode != 200) {','        if(regReply is SipResponse)telemetry?.sipResponse(regReply.statusCode)\n        if (regReply !is SipResponse || regReply.statusCode != 200) {')
edit(sip,'subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotId)','dev.codex.vowifi.common.StackProfile.selectedSubscription(ctxt,slotId)')
edit(sip,'''        telephonyManager = ctxt.getSystemService(TelephonyManager::class.java)''','''        require(activeSubscription.subscriptionId==expectedSubId) { "IMS subscription changed" }
        telephonyManager = ctxt.getSystemService(TelephonyManager::class.java)''')
edit(sip,'    private val smsHeadersMap = mutableMapOf<Int, smsHeaders>()','    private val smsHeadersMap = mutableMapOf<Int, Pair<smsHeaders,Byte>>()')
edit(sip,'        val sms = request.body.SipSmsDecode()','''        val sms = try { request.body.SipSmsDecode() } catch(_:RuntimeException){null}
        if(sms!=null)android.util.Log.i("Api30PhhIms","sms-rp type=${sms.type} ref=${sms.ref.toInt() and 255} cause=${sms.cause}")''')
edit(sip,'                smsHeadersMap[token] = smsHeaders(dest, callId, cseq)','                smsLock.withLock { smsHeadersMap[token] = Pair(smsHeaders(dest,callId,cseq),sms.ref) }')
edit(sip,'                    Rlog.d(TAG, "Failed sending SMS to framework", t);','''                    android.util.Log.w("Api30PhhIms","framework-sms-receive-error="+t.javaClass.simpleName)
                    sendSmsAck(token,0,2)''')
edit(sip,'    fun sendSmsAck(token: Int, ref: Int, error: Boolean): Unit {','    fun sendSmsAck(token: Int, ref: Int, result: Int): Unit {')
edit(sip,'''        val body = SipSmsEncodeAck(ref.toByte())
        val headers = smsHeadersMap.remove(token)''','''        val delivery = smsLock.withLock { smsHeadersMap.remove(token) } ?: return
        val (headers,rpRef)=delivery
        val body=if(result==1) SipSmsEncodeAck(rpRef) else rpDeliveryError(rpRef,result)
        android.util.Log.i("Api30PhhIms","sms-rp-response token=$token rp-ref=${rpRef.toInt() and 255} accepted=${result==1}")''')
edit(sip,'''        // do not send ack on error
        // Should we send an error report?
        if (error) {
            return
        }''','        // Both success and framework rejection receive a matching RP reference.')
edit(sip,'        setResponseCallback(msg.headers["call-id"]!![0], { true })','''        setResponseCallback(msg.headers["call-id"]!![0], { response ->
            android.util.Log.i("Api30PhhIms","sms-rp-response-sip="+response.statusCode)
            response.statusCode>=200
        })''')
edit(sip,'                completeSms(rpRef, pending, pending.state.onSip(resp.statusCode))','''                android.util.Log.i("Api30PhhIms","sms-send-sip="+resp.statusCode)
                completeSms(rpRef, pending, pending.state.onSip(resp.statusCode))''')
edit(sip,'    var onIncomingCall:', '    var onCallConnected: (() -> Unit)? = null\n    var onCallProgressing: (() -> Unit)? = null\n    private val progressReported=AtomicBoolean(false)\n    var onIncomingCall:')
edit(sip,'    var respInFlight: SipResponse? = null','''    @Volatile private var outgoingInvite:SipRequest?=null
    @Volatile private var dialogHeaders:SipHeadersMap?=null
    @Volatile private var dialogTarget:String?=null
    var respInFlight: SipResponse? = null''')
edit(sip,'            setResponseCallback(msg.headers["call-id"]!![0]) { r: SipResponse ->','''            outgoingInvite=msg;dialogHeaders=null;dialogTarget=null
            setResponseCallback(msg.headers["call-id"]!![0]) { r: SipResponse ->''')
edit(sip,'                    callStarted.set(true)','''                    dialogHeaders=msg2.headers - "cseq" - "content-length" - "content-type" - "expires"
                    val route=resp.headers["record-route"]?.reversed()
                    if(!route.isNullOrEmpty())dialogHeaders=dialogHeaders!!+("route" to route)
                    dialogTarget=resp.headers["contact"]?.firstOrNull()?.let{extractDestinationFromContact(it)} ?: to
                    val firstConnection=!callStarted.getAndSet(true)''')
edit(sip,'                    Rlog.d(TAG, "Invite got SUCCESS")','''                    Rlog.d(TAG, "Invite got SUCCESS")
                    android.util.Log.i("Api30PhhIms","voice=CONNECTED")''')
edit(sip,'                    Rlog.d(TAG, "Invite got SUCCESS")','''                    Rlog.d(TAG, "Invite got SUCCESS")
                    if(firstConnection)onCallConnected?.invoke()''')
edit(sip,'    fun call(phoneNumber: String) {\n        thread {','''    fun call(phoneNumber: String) {
        callStopped.set(false); callStarted.set(false); progressReported.set(false)
        thread {''')
edit(sip,'                    if (localNone) {','''                    val confRemote=respSdp.any { it.startsWith("a=conf:qos remote") }
                    if (localNone || remoteNone || confRemote) {''')
edit(sip,'                        val newSdp = respSdp.map { line ->','                        val newSdp = sdp.toString(Charsets.UTF_8).split("[\\r\\n]+".toRegex()).map { line ->')
edit(sip,'                            } else if (line.startsWith("a=des:qos mandatory local")) {','''                            } else if (line.startsWith("a=curr:qos remote")) {
                                "a=curr:qos remote sendrecv"
                            } else if (line.startsWith("a=des:qos") && line.contains(" local ")) {''')
edit(sip,'                                "a=des:qos mandatory local sendrecv"\n                            } else {','''                                "a=des:qos mandatory local sendrecv"
                            } else if(line.startsWith("a=des:qos") && line.contains(" remote ")) {
                                "a=des:qos mandatory remote sendrecv"
                            } else {''')
edit(sip,'                                currentCall!!.callHeaders + ("content-type" to listOf("application/sdp")),','                                (currentCall!!.callHeaders - "route" - "expires") + ("content-type" to listOf("application/sdp")),')
edit(sip,'    private var imsReady = false','''    private var imsReady = false
    @Volatile private var stopped = false
    @Volatile private var selectedNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val attempting=AtomicBoolean(false)
    private var connectionGeneration=0
    private var preferredPcscf=0
    private var retryDelay=2000L
    private var recoverNetwork:((Network)->Unit)?=null
    private fun connectionEnded(connection:SipConnection){
        myHandler.post {
            if(stopped || !this::socket.isInitialized || socket!==connection)return@post
            val found=selectedNetwork ?: return@post
            selectedNetwork=null;closeConnection();imsFailureCallback?.invoke()
            myHandler.postDelayed({recoverNetwork?.invoke(found)},retryDelay)
        }
    }
    fun refreshRegistration(){
        if(stopped)return
        if(imsReady)try{register()}catch(_:Throwable){if(this::socket.isInitialized)connectionEnded(socket)}
        else if(this::network.isInitialized)myHandler.post{recoverNetwork?.invoke(network)}
    }
    fun shutdown() {
        stopped = true
        stopCallMedia()
        networkCallback?.let { try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Throwable) {} }
        networkCallback = null
        closeConnection()
        myHandler.looper.quitSafely()
    }
    private fun closeConnection() {
        imsReady = false
        if(this::plainSocket.isInitialized) try { plainSocket.close() } catch (_: Throwable) {}
        if(this::socket.isInitialized) try { socket.close() } catch (_: Throwable) {}
        if(this::serverSocket.isInitialized) {
            try { serverSocket.serverSocket.close() } catch (_: Throwable) {}
            try { serverSocket.inTransform.close(); serverSocket.outTransform.close() } catch (_: Throwable) {}
        }
        if(this::serverSocketUdp.isInitialized) try { serverSocketUdp.close() } catch (_: Throwable) {}
        if(this::ipsecSettings.isInitialized) {
            try { ipsecSettings.clientSpiC.close() } catch (_: Throwable) {}
            try { ipsecSettings.clientSpiS.close() } catch (_: Throwable) {}
            try { ipsecSettings.serverSpiC?.close() } catch (_: Throwable) {}
            try { ipsecSettings.serverSpiS?.close() } catch (_: Throwable) {}
        }
    }''')
p=dest/sip;t=p.read_text(encoding='utf-8')
start=t.index('        val pcscf = if (pcscfs.isNotEmpty()) {')
end=t.index('\n        localAddr =',start)
t=t[:start]+'''        if (pcscfs.isEmpty()) {
            abandonnedBecauseOfNoPcscf = true
            imsFailureCallback?.invoke()
            return
        }
        val pcscf = pcscfs[preferredPcscf % pcscfs.size] as InetAddress
'''+t[end:]
start=t.index('        val tm = ctxt.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager',t.index('fun register('))
end=t.index('        // XXX samsung rom',start)
t=t[:start]+t[end:]
t=t.replace('expires=600000','expires=1800').replace('Expires: 600000','Expires: 1800')
t=t.replace('P-Access-Network-Info: 3GPP-E-UTRAN-FDD;utran-cell-id-3gpp=20810b8c49752501','P-Access-Network-Info: IEEE-802.11')
t=t.replace('                    Expires: 1800\n','                    P-Access-Network-Info: IEEE-802.11\n                    Expires: 1800\n',1)
t=t.replace('        subscribe()\n        // always keep callback','''        if (!imsReady) { imsReady=true; imsReadyCallback?.invoke() }
        android.util.Log.i("Api30PhhIms","sip-register=200")
        subscribe()
        // always keep callback''')
start=t.index('    fun getVolteNetwork() {')
end=t.index('\n    fun updateCommonHeaders(',start)
t=t[:start]+'''    fun getVolteNetwork() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) {
                if (selectedNetwork == lost) { connectionGeneration++;selectedNetwork=null; closeConnection(); imsFailureCallback?.invoke() }
            }
            override fun onAvailable(found: Network) { maybeConnect(found) }
            override fun onLinkPropertiesChanged(found: Network, lp: LinkProperties) { maybeConnect(found) }
            private fun maybeConnect(found: Network) {
                if(stopped || selectedNetwork == found) return
                val lp=connectivityManager.getLinkProperties(found) ?: return
                // API30 telephony advertises CELLULAR even for a WLAN DataService.
                if(lp.interfaceName?.startsWith("ipsec") != true || lp.pcscfServers.isEmpty()) return
                if(!attempting.compareAndSet(false,true))return
                selectedNetwork=found; network=found
                val generation=++connectionGeneration
                thread(name="Api30SipConnect") {
                    try { connect(); if(!imsReady)throw java.io.IOException("registration-incomplete");retryDelay=2000L } catch(t: Throwable) {
                        val frame=t.stackTrace.firstOrNull { it.className.startsWith("me.phh.") }
                        android.util.Log.w("Api30PhhIms","connect-error="+t.javaClass.simpleName+" at="+frame?.className+":"+frame?.lineNumber)
                        myHandler.post {
                            if(generation==connectionGeneration){
                                selectedNetwork=null;preferredPcscf++;closeConnection();imsFailureCallback?.invoke()
                                myHandler.postDelayed({maybeConnect(found)},retryDelay)
                                retryDelay=(retryDelay*2).coerceAtMost(60000L)
                            }
                        }
                    }finally{
                        attempting.set(false)
                    }
                }
            }
            fun recover(found:Network)=maybeConnect(found)
        }
        recoverNetwork={callback.recover(it)}
        networkCallback=callback
        connectivityManager.requestNetwork(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .setNetworkSpecifier(TelephonyNetworkSpecifier.Builder().setSubscriptionId(subId).build())
            .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS).build(),callback,myHandler)
    }
'''+t[end:]
t=t.replace('''                while (true) {
                    parseMessage(socket.gReader(), socket.gWriter())
                }''','''                val connection=socket
                while (!stopped && parseMessage(connection.gReader(), connection.gWriter())) { }
                connectionEnded(connection)''')
t=t.replace('            socket.close()\n        }','            connectionEnded(socket)\n        }',1)
t=t.replace('''        CoroutineScope(Dispatchers.IO).launch {
            // XXX catch and reconnect''','''        val capturedConnection=socket
        CoroutineScope(Dispatchers.IO).launch {
            // XXX catch and reconnect''',1)
t=t.replace('                val connection=socket','                val connection=capturedConnection',1)
t=t.replace('            connectionEnded(socket)\n        }','            connectionEnded(capturedConnection)\n        }',1)
t=t.replace('''                while (true) {
                    // XXX catch and reconnect on 'java.net.SocketException: Socket closed' ?
                    val client = serverSocket.serverSocket.accept()
                    // there can only be a single client at a time because
                    // both source and destination ports are fixed
                    val reader = client.getInputStream().sipReader()
                    val writer = client.getOutputStream()
                    while (parseMessage(reader, writer)) { }
                    client.close()
                }''','''                val listener=serverSocket
                while (!stopped) {
                    val (reader,writer)=listener.accept()
                    try { while (!stopped && parseMessage(reader,writer)) { } }
                    finally { writer.close() }
                }''')
t=t.replace('''        CoroutineScope(Dispatchers.IO).launch {
            try {
                val listener=serverSocket''','''        val capturedListener=serverSocket
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val listener=capturedListener''',1)
t=t.replace('''        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bufferIn''','''        val capturedUdp=serverSocketUdp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bufferIn''',1)
t=t.replace('serverSocketUdp.socket.receive(dgramPacketIn)','capturedUdp.socket.receive(dgramPacketIn)')
t=t.replace('serverSocketUdp.socket.send(dgramPacketOut)','capturedUdp.socket.send(dgramPacketOut)')
p.write_text(t,encoding='utf-8',newline='\n')
edit(sip,'    val callStopped = AtomicBoolean(false)','''    private val encoding=AtomicBoolean(false)
    private val decoding=AtomicBoolean(false)
    @Volatile private var activeAudioRecord:AudioRecord?=null
    private fun stopCallMedia(){
        callStopped.set(true)
        try { currentCall?.rtpSocket?.close() } catch(_:Throwable){}
        try { activeAudioRecord?.stop() } catch(_:Throwable){}
        currentCall=null
    }
    val callStopped = AtomicBoolean(false)''')
edit(sip,'        // IDK what packet do we send, but at least we\'re close rtp\n        callStopped.set(true)','''        stopCallMedia()''')
edit(sip,'    fun terminateCall() {\n        stopCallMedia()','''    fun terminateCall() {
        val invite=outgoingInvite
        val target=dialogTarget
        val dialog=dialogHeaders
        val request=if(dialog!=null && target!=null){
            val via=commonHeaders["via"]
            var h=dialog - "via" - "cseq" - "content-length" - "content-type" - "expires"
            if(via!=null)h=h+("via" to via)
            SipRequest(SipMethod.BYE,target,h)
        }else if(invite!=null){
            val seq=invite.headers["cseq"]!![0].substringBefore(" ")
            SipRequest(SipMethod.CANCEL,invite.destination,
                (invite.headers - "content-type" - "content-length")+("cseq" to listOf("$seq CANCEL")))
        }else null
        stopCallMedia()
        if(request!=null)thread(name="Api30SipHangup"){
            try{
                if(request.method==SipMethod.BYE)setResponseCallback(request.headers["call-id"]!![0]){response->
                    android.util.Log.i("Api30PhhIms","voice-bye-sip="+response.statusCode)
                    response.statusCode>=200
                }
                synchronized(socket.gWriter()){socket.gWriter().write(request.toByteArray())}
            }catch(t:Throwable){android.util.Log.w("Api30PhhIms","voice-hangup-error="+t.javaClass.simpleName)}
        }
        outgoingInvite=null;dialogHeaders=null;dialogTarget=null''')
edit(sip,'            callStopped.set(true)\n            onCancelledCall?.invoke','            stopCallMedia()\n            onCancelledCall?.invoke')
edit(sip,'    fun handleCancel(request: SipRequest): Int {\n        callStopped.set(true)','    fun handleCancel(request: SipRequest): Int {\n        stopCallMedia()')
edit(sip,'                    if(resp.statusCode >= 400) {','''                    if(resp.statusCode >= 400) {
                        android.util.Log.i("Api30PhhIms","voice-rejected="+resp.statusCode)
                        stopCallMedia()''')
edit(sip,'                var resp = r','''                android.util.Log.i("Api30PhhIms","voice-sip-status="+r.statusCode)
                if((r.statusCode==180 || r.statusCode==183) && progressReported.compareAndSet(false,true))onCallProgressing?.invoke()
                var resp = r''')
p=dest/sip;t=p.read_text(encoding='utf-8')
start=t.index('    fun callEncodeThread() {');end=t.index('    var currentCall:',start)
part=t[start:end]
part=part.replace('        val call = currentCall!!','        val call = currentCall ?: return\n        if(!encoding.compareAndSet(false,true)) return')
part=part.replace('        thread {','''        thread {
            var ownedEncoder:MediaCodec?=null
            var ownedRecorder:AudioRecord?=null
            try {''',1)
part=part.replace('val encoder = MediaCodec.createEncoderByType("audio/3gpp")','val encoder = MediaCodec.createEncoderByType("audio/3gpp").also { ownedEncoder=it }')
part=part.replace('while(!callStarted.get()) {','while(!callStarted.get() && !callStopped.get()) {')
part=part.replace('            val rnnNoise = Rnnoise()','            if(callStopped.get()) return@thread\n            val rnnNoise = Rnnoise()')
part=part.replace('AudioFormat.ENCODING_PCM_16BIT, minBufferSize)\n','AudioFormat.ENCODING_PCM_16BIT, minBufferSize).also { ownedRecorder=it; activeAudioRecord=it }\n')
part=part.replace('                // Convert buffer from ByteArray to ShortArray','                if(nRead<=0) break\n                // Convert buffer from ByteArray to ShortArray')
part=part.replace('            var firstPacket = true','            var firstPacket = true\n            var sentFrames=0')
part=part.replace('                        sequenceNumber++','''                        sentFrames++
                        if(sentFrames==1 || sentFrames==100)android.util.Log.i("Api30PhhIms","voice-rtp-tx frames=$sentFrames")
                        sequenceNumber++''')
part=part.replace('encoder.dequeueInputBuffer(-1)','encoder.dequeueInputBuffer(100000)')
part=part.replace('                val inBuf = encoder.getInputBuffer(inBufIdx)!!','                if(inBufIdx<0) continue\n                val inBuf = encoder.getInputBuffer(inBufIdx)!!')
part=part.replace('''            audioRecord.stop()
            audioRecord.release()
            encoder.stop()
            encoder.release()''','''            }catch(t:Throwable){ if(!callStopped.get()) android.util.Log.w("Api30PhhIms","audio-encode-error="+t.javaClass.simpleName) }
            finally {
                try{ownedRecorder?.stop()}catch(_:Throwable){}
                try{ownedRecorder?.release()}catch(_:Throwable){}
                try{ownedEncoder?.stop()}catch(_:Throwable){}
                try{ownedEncoder?.release()}catch(_:Throwable){}
                if(activeAudioRecord===ownedRecorder)activeAudioRecord=null
                encoding.set(false)
            }''')
t=t[:start]+part+t[end:]
start=t.index('    fun callDecodeThread() {');end=t.index('    fun extractDestinationFromContact(',start)
part=t[start:end]
part=part.replace('        // Receiving thread','        val call=currentCall ?: return\n        if(!decoding.compareAndSet(false,true))return\n        // Receiving thread')
part=part.replace('        thread {','''        thread {
            var ownedDecoder:MediaCodec?=null
            var ownedTrack:AudioTrack?=null
            try {''',1)
part=part.replace('minBufferSize, AudioTrack.MODE_STREAM)','minBufferSize, AudioTrack.MODE_STREAM).also { ownedTrack=it }')
part=part.replace('MediaCodec.createDecoderByType("audio/3gpp")','MediaCodec.createDecoderByType("audio/3gpp").also { ownedDecoder=it }')
part=part.replace('currentCall!!.rtpSocket.receive(dgram)','call.rtpSocket.receive(dgram)\n                if(dgram.length<14)continue')
part=part.replace('            while(true) {','            var playedFrames=0\n            while(true) {')
part=part.replace('                    audioTrack.write(outBuf, outBufInfo.size, AudioTrack.WRITE_BLOCKING)','''                    val written=audioTrack.write(outBuf,outBufInfo.size,AudioTrack.WRITE_BLOCKING)
                    if(written>0){
                        playedFrames++
                        if(playedFrames==1 || playedFrames==100)android.util.Log.i("Api30PhhIms","voice-audio-played frames=$playedFrames")
                    }''')
part=part.replace('decoder.dequeueInputBuffer(-1)','decoder.dequeueInputBuffer(100000)')
part=part.replace('                val inBuf = decoder.getInputBuffer(inBufIndex)!!','                if(inBufIndex<0)continue\n                val inBuf = decoder.getInputBuffer(inBufIndex)!!')
part=part.replace('''            audioTrack.stop()
            audioTrack.release()
            decoder.stop()
            decoder.release()''','''            }catch(t:Throwable){ if(!callStopped.get())android.util.Log.w("Api30PhhIms","audio-decode-error="+t.javaClass.simpleName) }
            finally {
                try{ownedTrack?.stop()}catch(_:Throwable){}
                try{ownedTrack?.release()}catch(_:Throwable){}
                try{ownedDecoder?.stop()}catch(_:Throwable){}
                try{ownedDecoder?.release()}catch(_:Throwable){}
                decoding.set(false)
            }''')
t=t[:start]+part+t[end:]
t=t.replace('            val fakeRtcpSocket = DatagramSocket(0, localAddr) //useless but annoying ImsMediaManager','')
p.write_text(t,encoding='utf-8',newline='\n')
connection='me/phh/sip/SipConnection.kt'
edit(sip,'                        sentFrames++','                        sentFrames++\n                        telemetry?.add(dev.codex.vowifi.common.StackTelemetry.Counter.VOICE_TX_FRAMES,1)')
edit(sip,'                        playedFrames++','                        playedFrames++\n                        telemetry?.add(dev.codex.vowifi.common.StackTelemetry.Counter.VOICE_PLAYED_FRAMES,1)')
p=dest/connection;t=p.read_text(encoding='utf-8')
start=t.index('class SipConnectionTcpServer(')
end=t.index('class SipConnectionUdp(',start)
t=t[:start]+t[end:]
t=t.replace('''        socketFd =
            socket.javaClass.getMethod("getFileDescriptor\\$").invoke(socket)
                as FileDescriptor''','''        socketFd = android.os.ParcelFileDescriptor.fromDatagramSocket(socket).let {
            retainedDescriptor=it; it.fileDescriptor
        }''')
t=t.replace('    val socketFd : FileDescriptor','    val socketFd : FileDescriptor\n    private lateinit var retainedDescriptor: android.os.ParcelFileDescriptor')
t=t.replace('    fun getChannel(): SelectableChannel {','''    fun close(){
        socket.close()
        if(this::retainedDescriptor.isInitialized) retainedDescriptor.close()
    }
    fun getChannel(): SelectableChannel {''')
p.write_text(t,encoding='utf-8',newline='\n')
edit(connection,'        socket.connect(InetSocketAddress(remoteAddr, remotePort))','        socket.connect(InetSocketAddress(remoteAddr, remotePort), 12000)')
edit(connection,'''        if (_localAddr != null) {
            socket.bind(InetSocketAddress(_localAddr, _localPort))
        }''','''        try {
            if (_localAddr != null) socket.bind(InetSocketAddress(_localAddr, _localPort))
        } catch(t:Throwable) {
            try{socket.close()}catch(_:Throwable){}
            throw t
        }''',expected=1)
edit(connection,'    override fun close() {\n        socket.close()\n    }','''    override fun close() {
        socket.close()
        if(this::inTransform.isInitialized) inTransform.close()
        if(this::outTransform.isInitialized) outTransform.close()
    }''',expected=2)
print('Experimental IMS source generated; preserved snapshot unchanged.')
