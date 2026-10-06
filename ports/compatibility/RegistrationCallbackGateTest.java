// SPDX-License-Identifier: GPL-2.0
import me.phh.ims.RegistrationCallbackGate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RegistrationCallbackGateTest {
    private static void check(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        RegistrationCallbackGate retired = new RegistrationCallbackGate();
        RegistrationCallbackGate replacement = new RegistrationCallbackGate();
        AtomicInteger registration = new AtomicInteger();
        check(retired.publish(() -> registration.set(1)), "initial producer");
        retired.close();
        check(replacement.publish(() -> registration.set(2)), "replacement producer");
        check(!retired.publish(() -> registration.set(0)), "retired failure callback refused");
        check(registration.get()==2, "late failure cannot deregister replacement");
        check(!retired.publish(() -> registration.set(1)), "retired success callback refused");
        check(registration.get()==2, "late success cannot register replacement");
        retired.close();
        check(replacement.publish(() -> registration.set(3)), "closing old owner leaves other owner alive");

        RegistrationCallbackGate concurrent = new RegistrationCallbackGate();
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                check(concurrent.publish(() -> {
                    entered.countDown();
                    try { check(finish.await(5,TimeUnit.SECONDS), "publication released"); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                    registration.set(4);
                }), "in-flight publication completes");
            } catch (Throwable t) { failure.set(t); }
        });
        Thread closer = new Thread(() -> { concurrent.close(); closed.countDown(); });
        writer.start();
        check(entered.await(5,TimeUnit.SECONDS), "publication started");
        closer.start();
        // Close cannot report completion while its producer is still publishing.
        check(!closed.await(100,TimeUnit.MILLISECONDS), "retirement waits for in-flight publication");
        finish.countDown();
        writer.join(5000); closer.join(5000);
        check(!writer.isAlive()&&!closer.isAlive(), "no retirement deadlock");
        if (failure.get()!=null) throw new AssertionError(failure.get());
        check(closed.getCount()==0&&registration.get()==4, "retirement completed after publication");
        for (int i=0;i<1000;i++) check(!concurrent.publish(() -> registration.set(0)), "post-retirement callbacks refused");
        check(registration.get()==4, "retired callbacks never mutate state");

        RegistrationCallbackGate throwing = new RegistrationCallbackGate();
        try { throwing.publish(() -> { throw new IllegalStateException("fixture"); }); throw new AssertionError("exception lost"); }
        catch (IllegalStateException expected) { }
        throwing.close();
        check(!throwing.publish(() -> registration.set(0)), "throwing publisher releases lock for retirement");
        System.out.println("registration-callback-tests=PASS replacement-state=PRESERVED late-success-and-failure=REFUSED retirement=SERIALIZED");
    }
}
