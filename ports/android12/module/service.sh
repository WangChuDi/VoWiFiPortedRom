#!/system/bin/sh
MODDIR=${0%/*}
ROOT=/data/adb/codex_vowifi_stack_modern
umask 077
mkdir -p "$ROOT" || exit 1
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 3; done
[ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 0
# No package streaming, carrier selection or phone-process restart at this stage.
sh "$MODDIR/control.sh" prepare >"$ROOT/installation-boot.json" 2>&1
