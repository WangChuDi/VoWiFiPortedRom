# SPDX-License-Identifier: GPL-2.0
"""Captured-attempt receive recovery shared by the API30 and modern generators."""
def apply(source):
    def once(old, new):
        nonlocal source
        if source.count(old) != 1:
            raise RuntimeError('SIP receive patch context changed: ' + old[:60])
        source = source.replace(old, new, 1)
    once('private fun connectionEnded(connection:SipConnection,attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>?=reconnectGate.current()){',
         'private fun connectionEnded(connection:SipConnection,attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>?=reconnectGate.current(),failure:dev.codex.vowifi.common.StackTelemetry.SipFailure=dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO){')
    once('telemetry?.sipFailure(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO)\n            reconnectGate.lost(found)',
         'telemetry?.sipFailure(attempt.generation,failure)\n            reconnectGate.lost(found)')
    once('''        if(response.statusCode!=200)telemetry?.sipFailure(attempt.generation,dev.codex.vowifi.common.StackTelemetry.SipFailure.SIP_REJECTED)
        // once we get there all register must be successful
        // on failure just abort thread, ims will restart
        require(response.statusCode == 200)''', '''        if(response.statusCode<200)return false
        if(response.statusCode!=200)throw SipAttemptFailure(dev.codex.vowifi.common.StackTelemetry.SipFailure.SIP_REJECTED)''')
    start = source.index('        val capturedConnection=socket\n')
    end = source.index('\n    }\n\n    fun getVolteNetwork()', start)
    old = source[start:end]
    if any(old.count(marker) != 1 for marker in ('channel=MAIN', 'channel=TCP_SERVER', 'channel=UDP_SERVER', 'val capturedListener=serverSocket', 'val capturedUdp=serverSocketUdp')):
        raise RuntimeError('SIP receive loop region changed')
    source = source[:start] + '''        val capturedConnection=socket
        val receiveCurrent=java.util.function.BooleanSupplier{!stopped&&reconnectGate.current(attempt)}
        CoroutineScope(Dispatchers.IO).launch {
            var failure=dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO
            dev.codex.vowifi.common.SipReceiveLoops.run(receiveCurrent,
                {parseMessage(capturedConnection.gReader(),capturedConnection.gWriter(),attempt,capturedRx,dev.codex.vowifi.common.SipReceiveObservation.Channel.MAIN)},
                {error->capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.MAIN,dev.codex.vowifi.common.SipReceiveObservation.State.FAILED);failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=MAIN type="+error.javaClass.simpleName)},
                {capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.MAIN,dev.codex.vowifi.common.SipReceiveObservation.State.ENDED);connectionEnded(capturedConnection,attempt,failure)})
        }
        val capturedListener=serverSocket
        CoroutineScope(Dispatchers.IO).launch {
            var failure=dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO
            dev.codex.vowifi.common.SipReceiveLoops.runTcp(receiveCurrent,
                {
                    val(reader,writer)=capturedListener.accept()
                    val peer=try{capturedRx?.accepted()}catch(error:Throwable){writer.close();throw error}
                    object:dev.codex.vowifi.common.SipReceiveLoops.Client {
                        override fun read():Boolean=parseMessage(reader,writer,attempt,capturedRx,dev.codex.vowifi.common.SipReceiveObservation.Channel.TCP)
                        override fun close(){try{writer.close()}finally{peer?.close()}}
                    }
                },
                java.util.concurrent.Executor{worker->CoroutineScope(Dispatchers.IO).launch{worker.run()}},4,
                {error->
                    android.util.Log.w("Api30PhhIms","voice-socket-error channel=TCP_PEER type="+error.javaClass.simpleName)
                    if(error is SipAttemptFailure)connectionEnded(capturedConnection,attempt,failureKind(error))
                },
                {error->capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.TCP,dev.codex.vowifi.common.SipReceiveObservation.State.FAILED);failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=TCP_SERVER type="+error.javaClass.simpleName)},
                {capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.TCP,dev.codex.vowifi.common.SipReceiveObservation.State.ENDED);connectionEnded(capturedConnection,attempt,failure)})
        }
        val capturedUdp=serverSocketUdp
        CoroutineScope(Dispatchers.IO).launch {
            var failure=dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO
            val bufferIn=ByteArray(128*1024)
            val packet=DatagramPacket(bufferIn,bufferIn.size)
            val writer=ByteArrayOutputStream()
            dev.codex.vowifi.common.SipReceiveLoops.run(receiveCurrent,
                {
                    packet.length=bufferIn.size
                    capturedUdp.socket.receive(packet)
                    if(receiveCurrent.asBoolean)capturedRx?.datagram()
                    val reader=ByteArrayInputStream(packet.data,packet.offset,packet.length).sipReader()
                    while(parseMessage(reader,writer,attempt,capturedRx,dev.codex.vowifi.common.SipReceiveObservation.Channel.UDP)){}
                    if(receiveCurrent.asBoolean){
                        val reply=writer.toByteArray()
                        if(reply.isNotEmpty())capturedUdp.socket.send(DatagramPacket(reply,reply.size,packet.address,packet.port))
                    }
                    writer.reset()
                    true
                },
                {error->capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.UDP,dev.codex.vowifi.common.SipReceiveObservation.State.FAILED);failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=UDP_SERVER type="+error.javaClass.simpleName)},
                {capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.UDP,dev.codex.vowifi.common.SipReceiveObservation.State.ENDED);connectionEnded(capturedConnection,attempt,failure)})
        }''' + source[end:]
    once('    @Volatile private var imsReady = false','    @Volatile private var imsReady = false\n    @Volatile private var inboundObservation:dev.codex.vowifi.common.SipReceiveObservation?=null')
    once('    private fun closeConnection() {','    private fun closeConnection() {\n        inboundObservation?.retire();inboundObservation=null')
    once('        setResponseCallback(registerHeaders["call-id"]!![0]){registerCallback(it,attempt)}','''        val capturedRx=telemetry?.beginReceive(attempt.generation)
        inboundObservation=capturedRx
        setResponseCallback(registerHeaders["call-id"]!![0]){registerCallback(it,attempt)}''')
    once('        setRequestCallback(SipMethod.MESSAGE, ::handleSms)','        setRequestCallback(SipMethod.MESSAGE){handleSms(it,capturedRx)}')
    once('fun parseMessage(reader: SipReader, writer: OutputStream,attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>?=reconnectGate.current()): Boolean {','fun parseMessage(reader: SipReader, writer: OutputStream,attempt:dev.codex.vowifi.common.SipReconnectGate.Attempt<Network>?=reconnectGate.current(),rx:dev.codex.vowifi.common.SipReceiveObservation?=null,channel:dev.codex.vowifi.common.SipReceiveObservation.Channel=dev.codex.vowifi.common.SipReceiveObservation.Channel.MAIN): Boolean {')
    once('        Rlog.d(TAG, "RObject() message $msg")','''        if(msg is SipResponse || msg is SipRequest)rx?.parsed(channel,msg is SipRequest && msg.method==SipMethod.MESSAGE)
        Rlog.d(TAG, "RObject() message $msg")''')
    once('    fun handleSms(request: SipRequest): Int {','    fun handleSms(request: SipRequest,rx:dev.codex.vowifi.common.SipReceiveObservation?=null): Int {')
    once('''        if (sms == null) {
            Rlog.w(TAG, "Could not decode sms pdu")''','''        if (sms == null) {
            rx?.rp(dev.codex.vowifi.common.SipReceiveObservation.Rp.DECODE_ERROR)
            Rlog.w(TAG, "Could not decode sms pdu")''')
    once('        Rlog.d(TAG, "Decoded SMS type ${sms.type}, ${sms.pdu?.toString()}")','''        rx?.rp(when(sms.type){
            SmsType.RP_DATA_FROM_NETWORK->dev.codex.vowifi.common.SipReceiveObservation.Rp.DATA
            SmsType.RP_ACK_FROM_NETWORK->dev.codex.vowifi.common.SipReceiveObservation.Rp.ACK
            SmsType.RP_ERROR_FROM_NETWORK->dev.codex.vowifi.common.SipReceiveObservation.Rp.ERROR
            else->dev.codex.vowifi.common.SipReceiveObservation.Rp.OTHER
        })
        Rlog.d(TAG, "Decoded SMS type ${sms.type}, ${sms.pdu?.toString()}")''')
    for result,cause in [('true','-1'),('false','sms.cause ?: -1')]:
        old=f'if (pending != null) {{ pending.observation?.onRp({result},{cause}); completeSms(ref, pending, pending.state.onRp({result})) }}'
        once(old,old+' else rx?.unmatched()')
    for channel,anchor in [('MAIN','            dev.codex.vowifi.common.SipReceiveLoops.run(receiveCurrent,\n                {parseMessage(capturedConnection'),('TCP','            dev.codex.vowifi.common.SipReceiveLoops.runTcp(receiveCurrent,'),('UDP','            dev.codex.vowifi.common.SipReceiveLoops.run(receiveCurrent,\n                {\n                    packet.length')]:
        once(anchor,f'            if(receiveCurrent.asBoolean)capturedRx?.loop(dev.codex.vowifi.common.SipReceiveObservation.Channel.{channel},dev.codex.vowifi.common.SipReceiveObservation.State.RUNNING)\n'+anchor)
    return source
