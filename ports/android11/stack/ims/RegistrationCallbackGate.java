// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

/** Retiring a feature waits for its publication and permanently rejects late callbacks. */
public final class RegistrationCallbackGate {
    private boolean open = true;
    public synchronized boolean publish(Runnable action) {
        if (!open) return false;
        action.run();
        return true;
    }
    public synchronized void close() { open = false; }
}
