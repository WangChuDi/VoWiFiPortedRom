#!/system/bin/sh
ROOT=/data/adb/codex_vowifi_stack_modern
MODDIR=/data/adb/modules/codex_vowifi_stack_modern
RECOVERY_BASE="$ROOT/installation/recovery"
[ "$(id -u)" = 0 ] || exit 1
case "$(getprop ro.build.version.sdk)" in 31|32|33|34|35|36|37) ;; *) exit 1 ;; esac
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 3; done
# The independent entry also starts the supervisor when preparation was
# interrupted. Module payload validity is checked under the Java controller lock.
[ -f "$ROOT/installation/baseline.properties" ] || [ -d "$ROOT/coordination/owners" ] || exit 0
[ "$(readlink -f "$RECOVERY_BASE")" = "$RECOVERY_BASE" ] && [ ! -L "$RECOVERY_BASE/current" ] || exit 1
generation=$(cat "$RECOVERY_BASE/current") || exit 1
[ "${#generation}" = 64 ] || exit 1
case "$generation" in *[!0-9a-f]*) exit 1 ;; esac
RECOVERY="$RECOVERY_BASE/generations/$generation"
[ "$(readlink -f "$RECOVERY")" = "$RECOVERY" ] && [ ! -L "$RECOVERY/controller.zip" ] && [ ! -L "$RECOVERY/recovery.sha256" ] || exit 1
umask 077
(cd "$RECOVERY" && sha256sum -c recovery.sha256 >/dev/null 2>&1) || exit 1
# Keeps retrying pending recovery. Only safe fixed metadata reaches this private log.
exec env CLASSPATH="$RECOVERY/controller.zip" app_process /system/bin ModernSelectionSupervisor run >"$ROOT/installation-recovery.json" 2>&1
