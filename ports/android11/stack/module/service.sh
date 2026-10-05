#!/system/bin/sh
MODDIR=${0%/*}
if [ "${1:-}" != prepare ]; then
  until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
  sleep 15
fi
STATE=/data/adb/codex_vowifi_stack
mkdir -p "$STATE"
chmod 700 "$STATE"
LOG="$STATE/boot.log"
umask 077
prepare_apps() {
  READY=1
  for PKG in dev.codex.vowifi.iwlan dev.codex.vowifi.qns me.phh.ims; do
    run_cmd pm grant "$PKG" android.permission.READ_PHONE_STATE || READY=0
    run_cmd cmd deviceidle whitelist +"$PKG" || READY=0
  done
  run_cmd pm grant me.phh.ims android.permission.RECORD_AUDIO || READY=0
  run_cmd pm grant me.phh.ims android.permission.SEND_SMS || READY=0
  run_cmd appops set dev.codex.vowifi.iwlan MANAGE_IPSEC_TUNNELS allow || READY=0
  [ "$READY" = 1 ]
}
run_cmd() {
  # Binder shell commands cannot hand system_server a root-private log FD.
  # Capture through a pipe, then write from this root shell instead.
  if OUTPUT=$("$@" 2>&1); then STATUS=0; else STATUS=$?; fi
  printf '%s\n' "$OUTPUT"
  return "$STATUS"
}
COUNT=0
until prepare_apps > "$LOG" 2>&1; do
  COUNT=$((COUNT + 1))
  [ "$COUNT" -lt 12 ] || exit 1
  sleep 5
done
echo services-prepared >> "$LOG"
[ "${1:-}" != prepare ] || exit 0
sh "$MODDIR/control.sh" migrate >> "$LOG" 2>&1 || exit 1
sh "$MODDIR/control.sh" recover-trials >> "$LOG" 2>&1 || echo boot-trial-recovery=DEFERRED >> "$LOG"
# The phone process is shared. Reload once for a valid persistent owner, then
# start independent token-scoped supervisors for all retained subscriptions.
for DIRECTORY in "$STATE"/transactions/slot-*-sub-*; do
  [ -f "$DIRECTORY/transaction" ] && [ -f "$DIRECTORY/enabled" ] || continue
  OWNER=$(cat "$DIRECTORY/owner")
  COMPANION=/data/adb/modules/codex_vowifi_sms/control.sh
  [ ! -f "$COMPANION" ] || sh "$COMPANION" stop >> "$LOG" 2>&1
  if sh "$MODDIR/control.sh" reload "${OWNER%%:*}" "${OWNER#*:}" >> "$LOG" 2>&1; then break; fi
done
sh "$MODDIR/control.sh" resume-all >> "$LOG" 2>&1
