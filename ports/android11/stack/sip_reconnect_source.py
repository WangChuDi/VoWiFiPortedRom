# SPDX-License-Identifier: GPL-2.0
"""Generation-scoped transport adaptations for both preserved IMS variants."""
def apply(source):
    def once(old,new):
        nonlocal source
        if source.count(old)!=1:raise RuntimeError('SIP reconnect patch context changed: '+old[:60])
        source=source.replace(old,new,1)
    once('    fun connect() {','''    private fun resetRegistrationState(){
        val previous=registerHeaders["call-id"]?.firstOrNull()
        cbLock.withLock { if(previous!=null)responseCallbacks=responseCallbacks-previous }
        registerHeaders=mapOf("from" to listOf("<sip:$user>"),"to" to listOf("<sip:$user>"))+generateCallId()
        commonHeaders=emptyMap();registerCounter=1;contact="";mySip="";myTel=""
        akaDigest="""Digest username="$user",realm="$realm",nonce="",uri="sip:$realm",response="",algorithm=AKAv1-MD5"""
    }
    fun connect(attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>) {
        requireAttempt(attempt);resetRegistrationState()''')
    once('''            abandonnedBecauseOfNoPcscf = true
            imsFailureCallback?.invoke()
            return''','''            abandonnedBecauseOfNoPcscf = true
            throw SipAttemptFailure(dev.codex.vowifi.common.StackTelemetry.SipFailure.UNAVAILABLE)''')
    once('        val clientSpiS = ipSecManager.allocateSecurityParameterIndex(localAddr, clientSpiC.spi + 1)',
         '        val clientSpiS = try{ipSecManager.allocateSecurityParameterIndex(localAddr, clientSpiC.spi + 1)}catch(error:Throwable){clientSpiC.close();throw error}')
    once('        plainSocket.connect(5060)','''        requireAttempt(attempt)
        (plainSocket as? SipConnectionTcp)?.socket?.soTimeout=12000
        plainSocket.connect(5060)
        requireAttempt(attempt)''')
    once('''            Rlog.w(TAG, "Didn't get expected response from initial register, aborting")
            imsFailureCallback?.invoke()
            return''','''            throw SipAttemptFailure(if(plainRegReply is SipResponse)dev.codex.vowifi.common.StackTelemetry.SipFailure.SIP_REJECTED else dev.codex.vowifi.common.StackTelemetry.SipFailure.INVALID_RESPONSE)''')
    once('        val akaResult=authenticateAka()','''        requireAttempt(attempt)
        telemetry?.sipStage(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipStage.AKA)
        val akaResult=try{authenticateAka()}catch(error:IllegalStateException){throw SipAttemptFailure(dev.codex.vowifi.common.StackTelemetry.SipFailure.AKA_ERROR)}
        requireAttempt(attempt)''')
    once('        var portS = 5060','''        telemetry?.sipStage(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipStage.IPSEC_PARAMETERS)
        var portS = 5060''')
    once('        socket.connect(portS)','''        requireAttempt(attempt)
        telemetry?.sipStage(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipStage.SECURE_CONNECT)
        (socket as? SipConnectionTcp)?.socket?.soTimeout=12000
        socket.connect(portS)
        requireAttempt(attempt)''')
    once('''            Rlog.w(TAG, "Could not connect, aborting SIP")
            imsFailureCallback?.invoke()
            return''','''            throw SipAttemptFailure(if(regReply is SipResponse)dev.codex.vowifi.common.StackTelemetry.SipFailure.SIP_REJECTED else dev.codex.vowifi.common.StackTelemetry.SipFailure.INVALID_RESPONSE)''')
    once('        setResponseCallback(registerHeaders["call-id"]!![0], ::registerCallback)',
         '        setResponseCallback(registerHeaders["call-id"]!![0]){registerCallback(it,attempt)}')
    once('        handleResponse(regReply)','''        requireAttempt(attempt)
        (socket as? SipConnectionTcp)?.socket?.soTimeout=0
        handleResponse(regReply)''')
    once('    fun register(_writer: OutputStream? = null) {','''    @Synchronized
    fun register(_writer: OutputStream? = null) {
        val attempt=reconnectGate.current() ?: throw SipAttemptFailure(dev.codex.vowifi.common.StackTelemetry.SipFailure.CANCELED)
        requireAttempt(attempt)
        telemetry?.sipStage(attempt.generation,if(_writer!=null)dev.codex.vowifi.common.StackTelemetry.SipStage.INITIAL_REGISTER else dev.codex.vowifi.common.StackTelemetry.SipStage.AUTH_REGISTER)''')
    once('    fun parseMessage(reader: SipReader, writer: OutputStream): Boolean {',
         '    fun parseMessage(reader: SipReader, writer: OutputStream,attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>?=reconnectGate.current()): Boolean {')
    once('        Rlog.d(TAG, "RObject() message $msg")','''        if(stopped||!reconnectGate.current(attempt))return false
        Rlog.d(TAG, "RObject() message $msg")''')
    once('while (!stopped && parseMessage(connection.gReader(), connection.gWriter()))',
         'while (!stopped && parseMessage(connection.gReader(), connection.gWriter(),attempt))')
    once('connectionEnded(connection)','connectionEnded(connection,attempt)')
    once('connectionEnded(capturedConnection)','connectionEnded(capturedConnection,attempt)')
    once('while (!stopped && parseMessage(reader,writer))','while (!stopped && parseMessage(reader,writer,attempt))')
    once('while (parseMessage(reader, writer))','while (parseMessage(reader, writer,attempt))')
    once('        android.util.Log.i("Api30PhhIms","sip-register=200")',
         '        telemetry?.sipStage(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipStage.REGISTERED)\n        android.util.Log.i("Api30PhhIms","sip-register=200")')
    from sip_receive_source import apply as apply_receive
    return apply_receive(source)
