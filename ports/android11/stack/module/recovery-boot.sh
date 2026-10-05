#!/system/bin/sh
# Independent Magisk late-start recovery. No subscriber data is logged.
STATE=/data/adb/codex_vowifi_stack
MODULE=/data/adb/modules/codex_vowifi_stack_api30
MANAGED=0
[ ! -f "$STATE/transaction" ] || MANAGED=1
for DIRECTORY in "$STATE"/transactions/slot-*-sub-*; do
  [ ! -f "$DIRECTORY/transaction" ] || MANAGED=1
done
[ "$MANAGED" = 1 ] || exit 0
COUNT=0
until [ "$(getprop sys.boot_completed)" = 1 ]; do
  COUNT=$((COUNT + 1))
  [ "$COUNT" -lt 60 ] || break
  sleep 2
done
umask 077
sh "$STATE/recovery/control.sh" migrate > "$STATE/recovery-boot.log" 2>&1 || exit 1
if [ ! -d "$MODULE" ] || [ -f "$MODULE/disable" ] || [ -f "$MODULE/remove" ]; then
  sh "$STATE/recovery/control.sh" rollback-all >> "$STATE/recovery-boot.log" 2>&1
else
  sh "$STATE/recovery/control.sh" recover-trials >> "$STATE/recovery-boot.log" 2>&1
fi
