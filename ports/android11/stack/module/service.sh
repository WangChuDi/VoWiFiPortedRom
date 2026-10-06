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
ensure_installed() {
  PKG=$1
  APK="$MODDIR/system/priv-app/$2/$2.apk"
  EXPECTED_HASH=$(sha256sum "$APK" | cut -d ' ' -f 1)
  [ "${#EXPECTED_HASH}" = 64 ] || return 1
  # Both Binder output FDs must be pipes; boot.log is root-private.
  INSTALLED=$(pm path "$PKG" 2>&1) || { echo apk-update=PACKAGE_PATH_UNAVAILABLE; return 1; }
  # These bundled packages are single-APK services. Refuse an unexpected layout.
  case "$INSTALLED" in package:*) ;; *) return 1 ;; esac
  [ "$(printf '%s\n' "$INSTALLED" | wc -l)" = 1 ] || return 1
  ACTUAL_HASH=$(sha256sum "${INSTALLED#package:}" | cut -d ' ' -f 1)
  [ "$EXPECTED_HASH" != "$ACTUAL_HASH" ] || return 0
  # APK replacement kills its service. Require observed idle state on both slots.
  CALLS=$(timeout 8s dumpsys telephony.registry 2>/dev/null | sed -n 's/^.*mCallState=\([0-9][0-9]*\).*$/\1/p')
  COUNT=$(printf '%s\n' "$CALLS" | grep -c '^0$')
  TOTAL=$(printf '%s\n' "$CALLS" | wc -l)
  [ "$COUNT" -ge 2 ] && [ "$COUNT" = "$TOTAL" ] || { echo apk-update=WAITING_FOR_IDLE; return 1; }
  SIZE=$(stat -c %s "$APK") || return 1
  # Stream the known signed APK: system_server may not share the module namespace.
  OUTPUT=$(cat "$APK" | pm install -r -S "$SIZE" 2>&1)
  STATUS=$?
  [ "$STATUS" = 0 ] && [ "$OUTPUT" = Success ] || { echo apk-update=INSTALL_FAILED; return 1; }
  INSTALLED=$(pm path "$PKG" 2>&1) || { echo apk-update=PACKAGE_PATH_UNAVAILABLE; return 1; }
  case "$INSTALLED" in package:*) ;; *) return 1 ;; esac
  [ "$(printf '%s\n' "$INSTALLED" | wc -l)" = 1 ] || return 1
  ACTUAL_HASH=$(sha256sum "${INSTALLED#package:}" | cut -d ' ' -f 1)
  [ "$EXPECTED_HASH" = "$ACTUAL_HASH" ] || { echo apk-update=VERIFY_FAILED; return 1; }
  echo "apk-update=VERIFIED package=$PKG"
}
prepare_apps() {
  READY=1
  ensure_installed dev.codex.vowifi.iwlan Api30Iwlan || return 1
  ensure_installed dev.codex.vowifi.qns Api30Qns || return 1
  ensure_installed me.phh.ims Api30PhhIms || return 1
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
