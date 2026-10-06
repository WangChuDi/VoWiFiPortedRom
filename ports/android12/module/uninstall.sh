#!/system/bin/sh
# Keep the original policy record when recovery is refused/incomplete.
MODDIR=${0%/*}
ROOT=/data/adb/codex_vowifi_stack_modern
[ ! -f "$ROOT/installation/baseline.properties" ] || sh "$MODDIR/control.sh" restore >"$ROOT/installation-uninstall.json" 2>&1
