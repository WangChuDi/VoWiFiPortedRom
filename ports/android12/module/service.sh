#!/system/bin/sh
MODDIR=${0%/*}
ROOT=/data/adb/codex_vowifi_stack_modern
umask 077
mkdir -p "$ROOT" || exit 1
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 3; done
[ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 0
# Preparation publishes recovery first, then starts the single resident supervisor.
# Retained owners renew; interrupted or expired owners restore before permissions.
sh "$MODDIR/control.sh" prepare >"$ROOT/installation-boot.json" 2>&1
