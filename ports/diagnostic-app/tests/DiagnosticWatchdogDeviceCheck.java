// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import android.os.Build;
import android.os.SystemProperties;
import org.json.JSONObject;

/** Isolated owned-emulator fixture. Not packaged in the application. */
public final class DiagnosticWatchdogDeviceCheck {
    public static void main(String[] args)throws Exception{
        int sdk=Build.VERSION.SDK_INT;
        if(args.length!=0||android.os.Process.myUid()!=0||sdk<31||sdk>37||
            !"1".equals(SystemProperties.get("ro.kernel.qemu"))||
            !("CodexVoWiFiApi"+sdk).equals(SystemProperties.get("ro.boot.qemu.avd_name")))
            throw new SecurityException("owned-root-emulator-required");
        PlatformHealth health=new PlatformHealth();health.begin();
        DiagnosticProgress progress=new DiagnosticProgress();
        progress.checkpoint(new JSONObject().put("sdk",sdk).put("fixture",true)
            .put("stage_completed_before_stall",true).put("engine_supported",true),"telephony");
        progress.startWatchdog(health);
        // Exercise a truly blocked process using the production APK's watchdog.
        // No telephony mutation, radio changes, SIM authentication or traffic.
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
