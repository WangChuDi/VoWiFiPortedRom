#!/system/bin/sh
STATE=/data/adb/codex_vowifi_stack
if [ -f "$STATE/enabled" ] && [ -f "$STATE/transaction" ]; then
  resetprop -n ro.telephony.iwlan_operation_mode AP-assisted
fi
