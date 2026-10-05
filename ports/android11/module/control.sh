#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/codex_vowifi_sms
RUNTIME=/data/local/tmp/codex-vowifi-sms
mkdir -p "$STATE"
chmod 700 "$STATE"
case "$1" in
  start)
    if [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ]; then echo "Enable the module in Magisk first"; exit 1; fi
    rm -f "$STATE/paused" "$RUNTIME/stop"
    /data/adb/magisk/busybox nohup /system/bin/sh "$MODDIR/daemon.sh" </dev/null >"$STATE/supervisor.log" 2>&1 &
    echo "Receiver starting"
    ;;
  stop)
    touch "$STATE/paused"
    [ -d "$RUNTIME" ] && touch "$RUNTIME/stop"
    n=0
    while [ -d "$STATE/lock" ] && [ "$n" -lt 25 ]; do sleep 2; n=$((n+1)); done
    cat "$STATE/status" 2>/dev/null
    [ ! -d "$STATE/lock" ] || exit 1
    ;;
  status)
    cat "$STATE/status" 2>/dev/null
    tail -n 12 "$STATE/current.log" 2>/dev/null
    ;;
  *) echo "Usage: sh $MODDIR/control.sh start|stop|status"; exit 1;;
esac
