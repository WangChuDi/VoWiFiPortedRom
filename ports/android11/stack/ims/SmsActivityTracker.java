// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import java.util.function.LongSupplier;

/** Metadata-only busy/quiet window around SMS work, including completion and ACK writes. */
public final class SmsActivityTracker {
    private final LongSupplier clock;
    private int active;
    private long last=-1;
    public SmsActivityTracker(LongSupplier clock){this.clock=clock;}
    public synchronized Scope begin(){active++;last=clock.getAsLong();return new Scope();}
    public synchronized boolean quiet(long milliseconds){
        long now=clock.getAsLong();return active==0&&(last<0||(now>=last&&now-last>=milliseconds));
    }
    public final class Scope implements AutoCloseable {
        private boolean closed;
        @Override public void close(){synchronized(SmsActivityTracker.this){
            if(closed)return;closed=true;active--;last=clock.getAsLong();
        }}
    }
}
