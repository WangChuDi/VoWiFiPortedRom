// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Immutable checkpoints let a watchdog return partial data during stalled Binder calls. */
final class DiagnosticProgress {
    static final long BUDGET_MILLIS=32000;
    private static final class Snapshot {
        final String json,stage;
        Snapshot(String json,String stage){this.json=json;this.stage=stage;}
    }
    private volatile Snapshot latest=new Snapshot("{}","platform_health");
    private final AtomicBoolean terminal=new AtomicBoolean();
    void checkpoint(JSONObject out,String stage){latest=new Snapshot(out.toString(),stage);}
    JSONObject timedOutSnapshot()throws Exception{
        Snapshot snapshot=latest;
        return new JSONObject(snapshot.json).put("diagnostic_complete",false)
            .put("diagnostic_error","TimeoutException").put("diagnostic_stage",snapshot.stage)
            .put("engine_supported",false);
    }
    boolean claimTerminal(){return terminal.compareAndSet(false,true);}
    void startWatchdog(PlatformHealth health){
        Thread watchdog=new Thread(()->{
            try{
                Thread.sleep(BUDGET_MILLIS);
                if(!claimTerminal())return;
                JSONObject partial=timedOutSnapshot();
                try{partial.put("platform_health",health.finish());}catch(Exception ignored){}
                System.out.println(partial.toString());System.exit(0);
            }catch(InterruptedException ignored){}
            catch(Exception ignored){System.exit(1);}
        },"vowifi-diagnostic-deadline");
        // The main thread may finish just after the watchdog claims the output.
        // Keep the process alive until that winner emits its result and exits.
        watchdog.setDaemon(false);watchdog.start();
    }
}
