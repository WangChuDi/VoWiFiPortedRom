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
import java.util.concurrent.CopyOnWriteArrayList

/** Android11 has no public ServerSocket.getFileDescriptor$ accessor. */
class SipConnectionTcpServer(val network:Network,val remoteAddr:InetAddress,
    val localAddr:InetAddress,val localPort:Int) {
    val serverSocketFd:FileDescriptor=Os.socket(
        if(localAddr is Inet6Address) OsConstants.AF_INET6 else OsConstants.AF_INET,
        OsConstants.SOCK_STREAM,0)
    private val accepted=CopyOnWriteArrayList<FileDescriptor>()
    lateinit var inTransform:IpSecTransform
    lateinit var outTransform:IpSecTransform
    val serverSocket=object:Closeable {
        override fun close(){
            try { Os.shutdown(serverSocketFd,OsConstants.SHUT_RDWR) } catch(_:Throwable){}
            try { Os.close(serverSocketFd) } catch(_:Throwable){}
            accepted.forEach { try { Os.shutdown(it,OsConstants.SHUT_RDWR); Os.close(it) } catch(_:Throwable){} }
            accepted.clear()
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
        val fd=Os.accept(serverSocketFd,null)
        accepted.add(fd)
        val input=object:InputStream(){
            override fun read():Int { val b=ByteArray(1);return if(read(b,0,1)<0) -1 else b[0].toInt() and 255 }
            override fun read(b:ByteArray,off:Int,len:Int):Int { if(len==0)return 0;val n=Os.read(fd,b,off,len);return if(n==0)-1 else n }
            override fun close(){accepted.remove(fd);try{Os.close(fd)}catch(_:Throwable){}}
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
