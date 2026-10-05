#!/system/bin/sh
# Fixed-device reversible experiment. Backups stay root-private on the device.
set -eu
MODDIR=${0%/*}
STATE=/data/adb/codex_vowifi_stack
FILES=/data/user_de/0/com.android.phone/files
COMPANION=/data/adb/modules/codex_vowifi_sms/control.sh
MODULE=/data/adb/modules/codex_vowifi_stack_api30
umask 077
mkdir -p "$STATE"
chmod 700 "$STATE"
run_cmd() {
  if OUTPUT=$("$@" 2>&1); then STATUS=0; else STATUS=$?; fi
  printf '%s\n' "$OUTPUT"
  return "$STATUS"
}
carrier() { CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial "$1"; }
restart_phone() {
  PID=$(pidof com.android.phone || true)
  [ -z "$PID" ] || kill "$PID"
}
gate() {
  BOOT=$(settings get global boot_count 2>&1)
  case "$BOOT" in ''|*[!0-9]*) return 1;; esac
  NOW=$(awk '{printf "%.0f", $1*1000}' /proc/uptime)
  run_cmd settings put global codex_wfc_stack_trial_boot "$BOOT"
  run_cmd settings put global codex_wfc_stack_trial_until "$((NOW + 90000))"
}
rollback() {
  [ -f "$STATE/transaction" ] || { echo rollback=NOT_NEEDED; return; }
  run_cmd settings put global codex_wfc_stack_trial_until 0 || true
  rm -f "$STATE/enabled" "$STATE/trial-running"
  # Only persisted override files are touched; ordinary cache and APNs stay intact.
  for F in "$FILES"/carrierconfig-*-override-*.xml; do
    [ -f "$F" ] || continue
    NAME=${F##*/}
    if [ -f "$STATE/baseline/$NAME" ]; then
      cp -p "$STATE/baseline/$NAME" "$F"
    else
      rm -f "$F"
    fi
  done
  for F in "$STATE/baseline"/carrierconfig-*-override-*.xml; do
    [ -f "$F" ] || continue
    cp -p "$F" "$FILES/${F##*/}"
  done
  restorecon "$FILES"/carrierconfig-*.xml >/dev/null 2>&1 || true
  resetprop -n ro.telephony.iwlan_operation_mode "$(cat "$STATE/mode-before")"
  POLICY_OK=1
  if [ -f "$STATE/sms-policy-before" ]; then
    CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin SmsTestPolicy restore || POLICY_OK=0
  fi
  restart_phone
  sleep 8
  [ ! -f "$COMPANION" ] || sh "$COMPANION" start
  [ "$POLICY_OK" = 1 ] || { echo rollback=PROVIDERS_RESTORED_POLICY_RETRY_REQUIRED; return 1; }
  rm -f "$STATE/transaction"
  mv "$STATE/baseline" "$STATE/baseline-restored-$(date +%s)"
  echo rollback=RESTORED
}
case "${1:-status}" in
  status)
    echo "mode=$(getprop ro.telephony.iwlan_operation_mode)"
    [ ! -f "$STATE/transaction" ] || echo transaction=ACTIVE
    [ ! -f "$STATE/enabled" ] || echo persistent=ENABLED
    carrier read
    ;;
  trial)
    [ "$(getprop ro.product.device)" = raphael ]
    [ "$(getprop ro.build.version.sdk)" = 30 ]
    carrier check
    [ ! -f "$STATE/transaction" ] || { echo transaction-already-active; exit 1; }
    # Refuse a framework restart during an active call.
    REGISTRY=$(dumpsys telephony.registry 2>&1 | grep -E '^[[:space:]]+mCallState=[0-9]')
    [ -n "$REGISTRY" ] || { echo call-state-unavailable; exit 1; }
    printf '%s\n' "$REGISTRY" > "$STATE/registry-before.txt"
    if grep -Eq 'mCallState=[12]' "$STATE/registry-before.txt"; then echo active-call-refused; exit 1; fi
    mkdir -p "$STATE/baseline"
    # This directory is used only for this transaction; stale snapshots are rejected.
    [ ! -f "$STATE/baseline/ready" ] || { echo stale-baseline-refused; exit 1; }
    for F in "$FILES"/carrierconfig-*-override-*.xml; do
      [ ! -f "$F" ] || cp -p "$F" "$STATE/baseline/${F##*/}"
    done
    touch "$STATE/baseline/ready"
    getprop ro.telephony.iwlan_operation_mode > "$STATE/mode-before"
    # Remains available if the module is disabled/removed at the next normal boot.
    mkdir -p "$STATE/recovery" /data/adb/service.d
    cp "$MODDIR/control.sh" "$STATE/recovery/control.sh"
    cp "$MODDIR/carrier-trial.zip" "$STATE/recovery/carrier-trial.zip"
    cp "$MODDIR/recovery-boot.sh" /data/adb/service.d/codex-vowifi-stack-recovery.sh
    chmod 700 "$STATE/recovery" /data/adb/service.d/codex-vowifi-stack-recovery.sh
    chmod 600 "$STATE/recovery/control.sh" "$STATE/recovery/carrier-trial.zip"
    touch "$STATE/transaction" "$STATE/trial-running"
    nohup sh "$MODDIR/control.sh" watchdog > "$STATE/watchdog.log" 2>&1 < /dev/null &
    echo $! > "$STATE/watchdog.pid"
    if ! carrier apply; then echo provider-apply-failed; rollback; exit 1; fi
    sleep 2
    FOUND=0
    for F in "$FILES"/carrierconfig-*-override-*.xml; do
      [ -f "$F" ] || continue
      grep -q dev.codex.vowifi.iwlan "$F" && FOUND=1
    done
    [ "$FOUND" = 1 ] || { echo persist-format-unverified; rollback; exit 1; }
    if ! gate; then echo trial-gate-failed; rollback; exit 1; fi
    [ ! -f "$COMPANION" ] || sh "$COMPANION" stop
    resetprop -n ro.telephony.iwlan_operation_mode AP-assisted
    restart_phone
    echo trial=STARTED
    ;;
  watchdog)
    DEADLINE=$(( $(cut -d. -f1 /proc/uptime) + 300 ))
    while [ -f "$STATE/trial-running" ] && [ "$(cut -d. -f1 /proc/uptime)" -lt "$DEADLINE" ]; do
      gate || break
      sleep 6
    done
    [ -f "$STATE/enabled" ] || rollback
    ;;
  enable)
    [ -f "$STATE/transaction" ]
    carrier check
    [ ! -f "$STATE/enabled" ] || { echo replacement=ALREADY_PERSISTENT; exit 0; }
    touch "$STATE/enabled"
    rm -f "$STATE/trial-running"
    nohup sh "$MODDIR/control.sh" supervise > "$STATE/supervisor.log" 2>&1 < /dev/null &
    echo replacement=PERSISTENT
    ;;
  supervise)
    [ ! -f "$COMPANION" ] || sh "$COMPANION" stop
    LAST_RECOVERY=$(( $(cut -d. -f1 /proc/uptime) - 300 ))
    while [ -f "$STATE/enabled" ] && [ -f "$STATE/transaction" ]; do
      if [ ! -d "$MODULE" ] || [ -f "$MODULE/disable" ] || [ -f "$MODULE/remove" ]; then rollback; exit; fi
      # Binder services disappear during shutdown. Preserve the transaction so
      # the next boot can resume it instead of silently undoing persistence.
      gate || { echo supervisor=GATE_UNAVAILABLE; exit 1; }
      # A killed IWLAN process destroys its tunnel while MIUI may retain stale
      # LinkProperties. Rebuild bindings only when idle, with a five-minute
      # backoff, and only while the tested SIM is still installed.
      MISSING=0
      for PKG in dev.codex.vowifi.iwlan dev.codex.vowifi.qns me.phh.ims; do
        pidof "$PKG" >/dev/null 2>&1 || MISSING=1
      done
      NOW_SECONDS=$(cut -d. -f1 /proc/uptime)
      if [ "$MISSING" = 1 ] && [ "$((NOW_SECONDS - LAST_RECOVERY))" -ge 300 ]; then
        if sh "$MODDIR/control.sh" reload; then
          echo supervisor=RECOVERED_MISSING_PROCESS
          LAST_RECOVERY=$NOW_SECONDS
        else
          echo supervisor=RECOVERY_DEFERRED
        fi
      fi
      sleep 30
    done
    ;;
  reload)
    [ -f "$STATE/transaction" ]
    carrier check
    REGISTRY=$(dumpsys telephony.registry 2>&1 | grep -E '^[[:space:]]+mCallState=[0-9]')
    [ -n "$REGISTRY" ] || { echo call-state-unavailable; exit 1; }
    if printf '%s\n' "$REGISTRY" | grep -Eq 'mCallState=[12]'; then echo active-call-refused; exit 1; fi
    gate
    restart_phone
    echo framework-reload=REQUESTED
    ;;
  rollback) rollback ;;
  *) echo 'status|trial|enable|reload|rollback'; exit 2 ;;
esac
