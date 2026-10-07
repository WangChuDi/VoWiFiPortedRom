// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.net.IpSecManager
import android.net.IpSecTransform
import android.net.Network
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Inet6Address
import java.util.concurrent.atomic.AtomicBoolean

/** Android11 has no public ServerSocket.getFileDescriptor$ accessor. */
class SipConnectionTcpServer(val network:Network,val remoteAddr:InetAddress,
    val localAddr:InetAddress,val localPort:Int) {
    val serverSocketFd:FileDescriptor=Os.socket(
        if(localAddr is Inet6Address) OsConstants.AF_INET6 else OsConstants.AF_INET,
        OsConstants.SOCK_STREAM,0)
    private val ownership=Any()
    private val accepted=ArrayList<Accepted>()
    private val closed=AtomicBoolean(false)
    private inner class Accepted(val fd:FileDescriptor) {
        private val released=AtomicBoolean(false)
        fun close(){
            if(!released.compareAndSet(false,true))return
            synchronized(ownership){accepted.remove(this)}
            try { Os.shutdown(fd,OsConstants.SHUT_RDWR) } catch(_:Throwable){}
            try { Os.close(fd) } catch(_:Throwable){}
        }
    }
    lateinit var inTransform:IpSecTransform
    lateinit var outTransform:IpSecTransform
    val serverSocket=object:Closeable {
        override fun close(){
            if(!closed.compareAndSet(false,true))return
            try { Os.shutdown(serverSocketFd,OsConstants.SHUT_RDWR) } catch(_:Throwable){}
            try { Os.close(serverSocketFd) } catch(_:Throwable){}
            val clients=synchronized(ownership){accepted.toList().also{accepted.clear()}}
            clients.forEach{it.close()}
        }
    }
    init {
        try {
            Os.setsockoptInt(serverSocketFd,OsConstants.SOL_SOCKET,OsConstants.SO_REUSEADDR,1)
            network.bindSocket(serverSocketFd)
            Os.bind(serverSocketFd,localAddr,localPort)
            Os.listen(serverSocketFd,8)
        }catch(t:Throwable){serverSocket.close();throw t}
    }
    fun accept():Pair<SipReader,OutputStream> {
        if(closed.get())throw java.net.SocketException("server-closed")
        val fd=Os.accept(serverSocketFd,null)
        val client=Accepted(fd)
        val retained=synchronized(ownership){if(closed.get())false else{accepted.add(client);true}}
        if(!retained){client.close();throw java.net.SocketException("server-closed")}
        val input=object:InputStream(){
            override fun read():Int { val b=ByteArray(1);return if(read(b,0,1)<0) -1 else b[0].toInt() and 255 }
            override fun read(b:ByteArray,off:Int,len:Int):Int { if(len==0)return 0;val n=Os.read(fd,b,off,len);return if(n==0)-1 else n }
            override fun close(){client.close()}
        }
        val output=object:OutputStream(){
            override fun write(b:Int){write(byteArrayOf(b.toByte()))}
            override fun write(b:ByteArray,off:Int,len:Int){
                var done=0
                while(done<len){val n=Os.write(fd,b,off+done,len-done);if(n<=0)throw java.io.IOException("socket-write-ended");done+=n}
            }
            override fun close(){input.close()}
        }
        return Pair(input.sipReader(),output)
    }
    fun enableIpsec(manager:IpSecManager,inbound:IpSecTransform,outbound:IpSecTransform){
        inTransform=inbound;outTransform=outbound
        manager.applyTransportModeTransform(serverSocketFd,IpSecManager.DIRECTION_IN,inbound)
        manager.applyTransportModeTransform(serverSocketFd,IpSecManager.DIRECTION_OUT,outbound)
    }
}
