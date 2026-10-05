#!/system/bin/sh
MODDIR=${0%/*}
while [ "$(getprop sys.boot_completed)" != 1 ]; do
  [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ] && exit 0
  sleep 5
done
sleep 60
exec /system/bin/sh "$MODDIR/daemon.sh"
