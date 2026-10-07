// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import dev.codex.vowifi.common.SipReceiveLoops;
import dev.codex.vowifi.common.SipReconnectGate;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual production loops with host sockets; not an Android/carrier fault-injection test. */
public final class SipReceiveLoopsTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void await(CountDownLatch latch,String label)throws Exception{check(latch.await(3,TimeUnit.SECONDS),label);}
    private static void join(Thread thread,String label)throws Exception{thread.join(3000);check(!thread.isAlive(),label);}
    private static Thread start(Runnable task){Thread t=new Thread(task);t.setDaemon(true);t.start();return t;}
    private static final class Client implements SipReceiveLoops.Client {
        final CountDownLatch reading=new CountDownLatch(1),release=new CountDownLatch(1),closed=new CountDownLatch(1);
        final AtomicInteger closes=new AtomicInteger();
        public boolean read()throws Exception{reading.countDown();release.await();return false;}
        public void close(){closes.incrementAndGet();release.countDown();closed.countDown();}
    }
    public static void main(String[]args)throws Exception{
        AtomicBoolean current=new AtomicBoolean(true);AtomicInteger ended=new AtomicInteger(),failed=new AtomicInteger();
        SipReceiveLoops.run(current::get,()->false,error->failed.incrementAndGet(),ended::incrementAndGet);
        check(ended.get()==1&&failed.get()==0,"EOF ends current receive lifetime once");
        SipReceiveLoops.run(current::get,()->{throw new IOException();},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(ended.get()==2&&failed.get()==1,"fatal read error reports failure and end");
        try{SipReceiveLoops.run(current::get,()->{throw new IOException();},error->{throw new IllegalStateException();},ended::incrementAndGet);throw new AssertionError("callback exception swallowed");}
        catch(IllegalStateException expected){check(ended.get()==3,"logging failure cannot suppress finally");}
        SipReceiveLoops.run(current::get,()->{current.set(false);throw new IOException();},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(ended.get()==3&&failed.get()==1,"canceled receive cannot report recovery");

        SipReconnectGate<String> gate=new SipReconnectGate<>(),peerGate=new SipReconnectGate<>();
        SipReconnectGate.Attempt<String> old=gate.offer("network"),peer=peerGate.offer("peer");gate.finish(old,true);peerGate.finish(peer,true);
        SipReceiveLoops.run(()->gate.current(old),()->{gate.lost("network");SipReconnectGate.Attempt<String> fresh=gate.offer("network");gate.finish(fresh,true);throw new IOException();},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(failed.get()==1&&ended.get()==3&&gate.current()!=old&&peerGate.current(peer),"late old failure leaves new attempt and other SIM intact");

        current.set(true);ended.set(0);failed.set(0);
        SipReceiveLoops.runTcp(current::get,()->{throw new IOException();},Runnable::run,2,error->{throw new AssertionError("peer error");},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(failed.get()==1&&ended.get()==1,"listener failure recovers once");
        Client stale=new Client();
        SipReceiveLoops.runTcp(current::get,()->{current.set(false);return stale;},Runnable::run,2,error->{throw new AssertionError("stale peer error");},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(stale.closes.get()==1&&failed.get()==1&&ended.get()==1,"accept racing cancellation closes peer without recovery");
        current.set(true);Client rejected=new Client();
        SipReceiveLoops.runTcp(current::get,()->rejected,task->{throw new RejectedExecutionException();},2,error->{throw new AssertionError("peer error");},error->failed.incrementAndGet(),ended::incrementAndGet);
        check(rejected.closes.get()==1&&failed.get()==2&&ended.get()==2,"executor rejection closes once and recovers listener");

        AtomicBoolean boundedCurrent=new AtomicBoolean(true);AtomicInteger accepted=new AtomicInteger(),recoveries=new AtomicInteger();
        Client first=new Client(),second=new Client(),third=new Client();CountDownLatch thirdAccepted=new CountDownLatch(1);
        ExecutorService pool=Executors.newCachedThreadPool(task->{Thread t=new Thread(task);t.setDaemon(true);return t;});
        Thread listener=start(()->SipReceiveLoops.runTcp(boundedCurrent::get,()->{
            int index=accepted.incrementAndGet();
            if(index==1)return first;if(index==2)return second;
            if(index==3){thirdAccepted.countDown();return third;}throw new EOFException();
        },pool,2,error->recoveries.incrementAndGet(),error->recoveries.incrementAndGet(),recoveries::incrementAndGet));
        try{
            await(first.reading,"first held peer active");await(second.reading,"second held peer active");
            check(!thirdAccepted.await(150,TimeUnit.MILLISECONDS)&&accepted.get()==2,"hard peer limit prevents extra accept while full");
            first.release.countDown();await(thirdAccepted,"completed peer releases permit for next accept");await(third.reading,"third peer starts");
            boundedCurrent.set(false);join(listener,"canceled full listener leaves bounded wait");
            second.close();third.close();await(first.closed,"first worker closes");
            check(first.closes.get()==1&&recoveries.get()==0,"peer EOF does not kill listener or trigger recovery");
        }finally{boundedCurrent.set(false);first.release.countDown();second.release.countDown();third.release.countDown();pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"bounded workers stop");}

        AtomicBoolean socketCurrent=new AtomicBoolean(true);List<Socket> sockets=Collections.synchronizedList(new ArrayList<Socket>());
        AtomicInteger peerErrors=new AtomicInteger(),listenerErrors=new AtomicInteger(),socketEnds=new AtomicInteger();
        CountDownLatch quietRead=new CountDownLatch(1),messageRead=new CountDownLatch(1);ExecutorService socketPool=Executors.newCachedThreadPool();
        try(ServerSocket server=new ServerSocket(0)){
            Thread receiver=start(()->SipReceiveLoops.runTcp(socketCurrent::get,()->{
                Socket owned=server.accept();sockets.add(owned);
                return new SipReceiveLoops.Client(){
                    public boolean read()throws Exception{
                        quietRead.countDown();int value=owned.getInputStream().read();if(value==42)messageRead.countDown();return value>=0;
                    }
                    public void close()throws Exception{owned.close();}
                };
            },socketPool,4,error->peerErrors.incrementAndGet(),error->listenerErrors.incrementAndGet(),socketEnds::incrementAndGet));
            try(Socket quiet=new Socket("127.0.0.1",server.getLocalPort());Socket active=new Socket("127.0.0.1",server.getLocalPort())){
                await(quietRead,"host TCP reader started");active.getOutputStream().write(42);active.getOutputStream().flush();
                await(messageRead,"new peer delivered despite old quiet connection");
                check(peerErrors.get()==0&&listenerErrors.get()==0,"no network failure during parallel delivery");
                socketCurrent.set(false);server.close();synchronized(sockets){for(Socket s:sockets)s.close();}
                join(receiver,"listener stops after socket retirement");
            }finally{socketCurrent.set(false);synchronized(sockets){for(Socket s:sockets)s.close();}}
        }finally{socketPool.shutdown();check(socketPool.awaitTermination(3,TimeUnit.SECONDS),"quiet workers unblocked by owned socket cleanup");}
        check(socketEnds.get()==0&&listenerErrors.get()==0,"retired sockets cannot recover newer registration");
        System.out.println("SIP production receive-loop host contracts PASS checks="+checks+" (not a device/carrier test)");
    }
}
