#!/system/bin/sh
MODDIR=${0%/*}
if [ -d /data/adb/codex_vowifi_sms/lock ] && [ ! -e /data/adb/codex_vowifi_sms/paused ]; then
  exec /system/bin/sh "$MODDIR/control.sh" stop
else
  exec /system/bin/sh "$MODDIR/control.sh" start
fi
