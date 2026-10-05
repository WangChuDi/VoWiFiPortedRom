#!/system/bin/sh
# Independent Magisk late-start recovery. No subscriber data is logged.
STATE=/data/adb/codex_vowifi_stack
MODULE=/data/adb/modules/codex_vowifi_stack_api30
[ -f "$STATE/transaction" ] || exit 0
COUNT=0
until [ "$(getprop sys.boot_completed)" = 1 ]; do
  COUNT=$((COUNT + 1))
  [ "$COUNT" -lt 60 ] || break
  sleep 2
done
if [ ! -d "$MODULE" ] || [ -f "$MODULE/disable" ] || [ -f "$MODULE/remove" ] || [ ! -f "$STATE/enabled" ]; then
  umask 077
  sh "$STATE/recovery/control.sh" rollback > "$STATE/recovery-boot.log" 2>&1
fi
