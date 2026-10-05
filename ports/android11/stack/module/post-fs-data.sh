#!/system/bin/sh
STATE=/data/adb/codex_vowifi_stack
if [ -f "$STATE/enabled" ] && [ -f "$STATE/transaction" ]; then
  resetprop -n ro.telephony.iwlan_operation_mode AP-assisted
fi
for DIRECTORY in "$STATE"/transactions/slot-*-sub-*; do
  [ -f "$DIRECTORY/enabled" ] && [ -f "$DIRECTORY/transaction" ] || continue
  case "$(cat "$DIRECTORY/components")" in
    1|3|5|7) resetprop -n ro.telephony.iwlan_operation_mode AP-assisted; break;;
  esac
done
