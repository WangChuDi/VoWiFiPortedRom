#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/codex_vowifi_sms
RUNTIME=/data/local/tmp/codex-vowifi-sms
BB=/data/adb/magisk/busybox
SU=$(command -v su)
[ -n "$SU" ] && [ -x "$SU" ] || exit 1
umask 077
mkdir -p "$STATE"
chmod 700 "$STATE"
[ "$(getprop ro.product.device)" = raphael ] && [ "$(getprop ro.build.version.sdk)" = 30 ] || exit 1
if ! mkdir "$STATE/lock" 2>/dev/null; then
  old=$(cat "$STATE/lock/pid" 2>/dev/null)
  case "$old" in ''|*[!0-9]*) old=0;; esac
  if [ "$old" -gt 1 ] && kill -0 "$old" 2>/dev/null && tr '\000' ' ' < "/proc/$old/cmdline" | grep -q '/codex_vowifi_sms/daemon.sh'; then exit 0; fi
  rm -f "$STATE/lock/pid"
  rmdir "$STATE/lock" 2>/dev/null || exit 1
  mkdir "$STATE/lock" || exit 1
fi
echo $$ > "$STATE/lock/pid"
client_job=
broker_job=
stop_needed() { [ -f "$STATE/paused" ] || [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; }
restore_appop() {
  [ -f "$STATE/write_sms.original" ] || return 0
  original=$(cat "$STATE/write_sms.original")
  case "$original" in allow|ignore|deny|default|foreground) ;; *) return 1;; esac
  result=$("$BB" timeout 10 /system/bin/cmd appops set android WRITE_SMS "$original" 2>&1) || { echo "$result"; return 1; }
  rm -f "$STATE/write_sms.original"
}
cleanup() {
  trap '' INT TERM
  touch "$RUNTIME/stop" 2>/dev/null
  if [ -n "$client_job" ]; then
    n=0
    while kill -0 "$client_job" 2>/dev/null && [ "$n" -lt 20 ]; do sleep 2; n=$((n+1)); done
    pid=$(cat "$RUNTIME/state/client.pid" 2>/dev/null)
    case "$pid" in ''|*[!0-9]*) pid=0;; esac
    if [ "$pid" -gt 1 ] && kill -0 "$pid" 2>/dev/null && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q 'codex-vowifi-sms-rx'; then
      kill -TERM "$pid"
      n=0
      while kill -0 "$pid" 2>/dev/null && [ "$n" -lt 5 ]; do sleep 1; n=$((n+1)); done
      if kill -0 "$pid" 2>/dev/null && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q 'codex-vowifi-sms-rx'; then
        kill -KILL "$pid"
        sleep 1
      fi
    fi
  fi
  [ -n "$broker_job" ] && kill "$broker_job" 2>/dev/null
  if restore_appop; then echo stopped > "$STATE/status"; else echo restore-appop-failed > "$STATE/status"; fi
  rm -f "$STATE/lock/pid"
  rmdir "$STATE/lock" 2>/dev/null
}
trap cleanup EXIT
trap 'exit 0' INT TERM
restore_appop || exit 1
stop_needed && exit 0
mkdir -p "$RUNTIME/state"
chown 0:1000 "$RUNTIME"
chmod 750 "$RUNTIME"
chown 1000:1000 "$RUNTIME/state"
chmod 700 "$RUNTIME/state"
cp "$MODDIR/receiver.zip" "$RUNTIME/receiver.zip" || exit 1
cp "$MODDIR/libcodex-ims-nested.so" "$RUNTIME/libcodex-ims-nested.so" || exit 1
chown 0:1000 "$RUNTIME/receiver.zip" "$RUNTIME/libcodex-ims-nested.so"
chmod 640 "$RUNTIME/receiver.zip" "$RUNTIME/libcodex-ims-nested.so"
rm -f "$RUNTIME/stop" "$RUNTIME/state/client.pid"
session=$(cat "$STATE/session_seconds" 2>/dev/null)
case "$session" in ''|*[!0-9]*) session=900;; esac
[ "$session" -ge 30 ] && [ "$session" -le 900 ] || session=900
backoff=60
cycle=0
while ! stop_needed; do
  cycle=$((cycle+1))
  mv -f "$STATE/current.log" "$STATE/previous.log" 2>/dev/null
  broker="codex-ims-m$$${cycle}$(date +%s)"
  CODEX_IMS_LIBRARY="$RUNTIME/libcodex-ims-nested.so" CLASSPATH="$RUNTIME/receiver.zip" "$BB" timeout -k 5 40 /system/bin/app_process /system/bin ImsPolicyBroker "$broker" > "$STATE/broker.log" 2>&1 &
  broker_job=$!
  sleep 1
  rm -f "$RUNTIME/state/client.pid"
  echo "connecting cycle=$cycle" > "$STATE/status"
  limit=$((session+65))
  "$BB" timeout -k 10 "$limit" "$SU" -g 1000 -G 3003 1000 -c "CODEX_IMS_NESTED_POLICY=1 CODEX_IMS_BROKER=$broker CODEX_SMS_RECEIVE_ONLY=1 CODEX_SMS_DELIVER=1 CODEX_SMS_FRAMEWORK=1 CODEX_SMS_RESIDENT=1 CODEX_SMS_SESSION_SECONDS=$session CLASSPATH=$RUNTIME/receiver.zip /system/bin/app_process /system/bin --nice-name=codex-vowifi-sms-rx ImsApi30Probe register" > "$STATE/current.log" 2>&1 &
  client_job=$!
  while kill -0 "$client_job" 2>/dev/null; do
    if stop_needed; then touch "$RUNTIME/stop"; break; fi
    if grep -q 'sms-delivery=READY' "$STATE/current.log"; then echo "registered cycle=$cycle" > "$STATE/status"; fi
    sleep 2
  done
  if stop_needed; then exit 0; fi
  wait "$client_job"
  client_job=
  {
    echo "cycle=$cycle ended=$(date +%s)"
    grep -E 'authenticated-register-status=|sms-framework=|sms-inbox=|sms-delivery-ack-sip-status=|sms-resident=|diagnostic-contact-removal=' "$STATE/current.log"
  } >> "$STATE/events.log"
  tail -n 250 "$STATE/events.log" > "$STATE/events.tmp" && mv "$STATE/events.tmp" "$STATE/events.log"
  [ -n "$broker_job" ] && kill "$broker_job" 2>/dev/null
  wait "$broker_job" 2>/dev/null
  broker_job=
  restore_appop || exit 1
  if grep -q 'sms-resident=SESSION_FINISHED' "$STATE/current.log" && grep -q 'diagnostic-contact-removal=CONFIRMED' "$STATE/current.log"; then
    delay=5; backoff=60
  else
    delay=$backoff
    backoff=$((backoff*2)); [ "$backoff" -le 600 ] || backoff=600
  fi
  echo "retry-in=${delay}s cycle=$cycle" > "$STATE/status"
  while [ "$delay" -gt 0 ] && ! stop_needed; do sleep 2; delay=$((delay-2)); done
done
