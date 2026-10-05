#!/system/bin/sh
MODDIR=${0%/*}
/system/bin/sh "$MODDIR/control.sh" stop
STATE=/data/adb/codex_vowifi_sms
if [ -f "$STATE/write_sms.original" ]; then
  mode=$(cat "$STATE/write_sms.original")
  case "$mode" in allow|ignore|deny|default|foreground)
    cmd appops set android WRITE_SMS "$mode" && rm -f "$STATE/write_sms.original";;
  esac
fi
