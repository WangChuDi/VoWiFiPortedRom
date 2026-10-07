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
                {parseMessage(capturedConnection.gReader(),capturedConnection.gWriter(),attempt)},
                {error->failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=MAIN type="+error.javaClass.simpleName)},
                {connectionEnded(capturedConnection,attempt,failure)})
        }
        val capturedListener=serverSocket
        CoroutineScope(Dispatchers.IO).launch {
            var failure=dev.codex.vowifi.common.StackTelemetry.SipFailure.NETWORK_IO
            dev.codex.vowifi.common.SipReceiveLoops.runTcp(receiveCurrent,
                {
                    val(reader,writer)=capturedListener.accept()
                    object:dev.codex.vowifi.common.SipReceiveLoops.Client {
                        override fun read():Boolean=parseMessage(reader,writer,attempt)
                        override fun close(){writer.close()}
                    }
                },
                java.util.concurrent.Executor{worker->CoroutineScope(Dispatchers.IO).launch{worker.run()}},4,
                {error->
                    android.util.Log.w("Api30PhhIms","voice-socket-error channel=TCP_PEER type="+error.javaClass.simpleName)
                    if(error is SipAttemptFailure)connectionEnded(capturedConnection,attempt,failureKind(error))
                },
                {error->failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=TCP_SERVER type="+error.javaClass.simpleName)},
                {connectionEnded(capturedConnection,attempt,failure)})
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
                    val reader=ByteArrayInputStream(packet.data,packet.offset,packet.length).sipReader()
                    while(parseMessage(reader,writer,attempt)){}
                    if(receiveCurrent.asBoolean){
                        val reply=writer.toByteArray()
                        if(reply.isNotEmpty())capturedUdp.socket.send(DatagramPacket(reply,reply.size,packet.address,packet.port))
                    }
                    writer.reset()
                    true
                },
                {error->failure=failureKind(error);android.util.Log.w("Api30PhhIms","voice-socket-error channel=UDP_SERVER type="+error.javaClass.simpleName)},
                {connectionEnded(capturedConnection,attempt,failure)})
        }''' + source[end:]
    return source
