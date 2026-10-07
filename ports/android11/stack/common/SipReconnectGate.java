// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

import java.util.Objects;

/** One handshake at a time; canceled attempts cannot publish a new connection. */
public final class SipReconnectGate<T> {
    public static final class Attempt<T> {
        public final T network;
        public final long generation;
        private boolean canceled;
        private Attempt(T network,long generation){this.network=network;this.generation=generation;}
    }
    public static final class Completion<T> {
        public final boolean accepted;
        public final T deferredNetwork;
        private Completion(boolean accepted,T deferred){this.accepted=accepted;this.deferredNetwork=deferred;}
    }
    private Attempt<T> active;
    private T deferred;
    private boolean busy,registered,stopped;
    private long generation;
    public synchronized Attempt<T> offer(T network){
        if(network==null||stopped)return null;
        if(busy){deferred=active.canceled||!Objects.equals(active.network,network)?network:null;return null;}
        if(active!=null&&registered&&Objects.equals(active.network,network))return null;
        if(active!=null)active.canceled=true;
        registered=false;busy=true;active=new Attempt<>(network,++generation);return active;
    }
    public synchronized boolean current(Attempt<T> attempt){return !stopped&&attempt!=null&&attempt==active&&!attempt.canceled;}
    public synchronized Attempt<T> current(){return current(active)?active:null;}
    public synchronized boolean lost(T network){
        if(Objects.equals(deferred,network))deferred=null;
        if(active==null||!Objects.equals(active.network,network))return false;
        active.canceled=true;registered=false;if(!busy)active=null;return true;
    }
    public synchronized Completion<T> finish(Attempt<T> attempt,boolean success){
        if(!busy||attempt!=active)throw new IllegalStateException("sip-attempt-completion-refused");
        boolean accepted=current(attempt)&&success;busy=false;registered=accepted;
        if(!accepted)active=null;
        T next=stopped?null:deferred;deferred=null;return new Completion<>(accepted,next);
    }
    public synchronized void stop(){stopped=true;registered=false;deferred=null;if(active!=null)active.canceled=true;}
}
