// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Captured-attempt receive lifetimes; no network, registration or retry policy. */
public final class SipReceiveLoops {
    public interface Step { boolean read() throws Exception; }
    public interface Client extends AutoCloseable { boolean read() throws Exception; void close() throws Exception; }
    public interface Accept { Client accept() throws Exception; }
    private SipReceiveLoops(){}
    public static void run(BooleanSupplier current,Step step,Consumer<Throwable> failed,Runnable ended){
        try{while(current.getAsBoolean()&&step.read()){} }
        catch(Throwable error){if(current.getAsBoolean())failed.accept(error);}
        finally{if(current.getAsBoolean())ended.run();}
    }
    /** Independent accepted peers prevent a quiet old connection blocking a new one. */
    public static void runTcp(BooleanSupplier current,Accept accept,Executor executor,int maximum,
                              Consumer<Throwable> peerFailed,Consumer<Throwable> listenerFailed,Runnable ended){
        if(maximum<1||maximum>8)throw new IllegalArgumentException("sip-peer-limit");
        Semaphore slots=new Semaphore(maximum);
        try{
            while(current.getAsBoolean()){
                if(!slots.tryAcquire(100,TimeUnit.MILLISECONDS))continue;
                Client client;
                try{client=accept.accept();}
                catch(Throwable error){slots.release();throw error;}
                if(client==null){slots.release();throw new IllegalStateException("sip-peer-unavailable");}
                if(!current.getAsBoolean()){
                    try{client.close();}finally{slots.release();}
                    break;
                }
                AtomicBoolean released=new AtomicBoolean();
                Runnable release=()->{
                    if(!released.compareAndSet(false,true))return;
                    try{client.close();}
                    catch(Throwable error){if(current.getAsBoolean())peerFailed.accept(error);}
                    finally{slots.release();}
                };
                Runnable worker=()->{
                    try{while(current.getAsBoolean()&&client.read()){} }
                    catch(Throwable error){if(current.getAsBoolean())peerFailed.accept(error);}
                    finally{release.run();}
                };
                try{executor.execute(worker);}
                catch(Throwable error){try{release.run();}finally{throw error;}}
            }
        }catch(Throwable error){if(current.getAsBoolean())listenerFailed.accept(error);}
        finally{if(current.getAsBoolean())ended.run();}
    }
}
