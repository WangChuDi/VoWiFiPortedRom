#!/system/bin/sh
ROOT=/data/adb/codex_vowifi_stack_modern
MODDIR=/data/adb/modules/codex_vowifi_stack_modern
RECOVERY="$ROOT/installation/recovery"
[ "$(id -u)" = 0 ] || exit 1
case "$(getprop ro.build.version.sdk)" in 31|32|33|34|35|36|37) ;; *) exit 1 ;; esac
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 3; done
if [ -d "$MODDIR" ] && [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ]; then exit 0; fi
[ -f "$ROOT/installation/baseline.properties" ] || exit 0
[ "$(readlink -f "$RECOVERY")" = "$RECOVERY" ] && [ ! -L "$RECOVERY/controller.zip" ] || exit 1
umask 077
CLASSPATH="$RECOVERY/controller.zip" timeout 40s app_process /system/bin ModernInstallationController restore >"$ROOT/installation-recovery.json" 2>&1
