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
# Serialize mutations with a kernel lock. A process exit or reboot releases it.
# Background workers close FD9 and always carry their own transaction token.
case "${1:-status}" in
  trial|enable|reload|rollback|renew|expire|rollback-owner)
    exec 9>"$STATE/control.lock"
    LOCK_WAIT=0
    # Android mksh marks exec-opened descriptors close-on-exec. Explicitly
    # duplicate FD9 for flock so the child can lock the parent's open file.
    until flock -n 9 9>&9 2>/dev/null; do
      LOCK_WAIT=$((LOCK_WAIT + 1))
      [ "$LOCK_WAIT" -lt 20 ] || { echo controller-busy; exit 1; }
      sleep 1
    done
    ;;
esac
# One global transaction for now. Preserve the proven fixed identity of older
# transactions; new transactions record their explicit slot:subscription owner.
OWNER=$(cat "$STATE/owner" 2>/dev/null || echo 1:1)
OWNER_SLOT=${OWNER%%:*}
OWNER_SUB=${OWNER#*:}
validate_selection() {
  case "$1" in ''|*[!0-9]*) echo invalid-selection; return 1;; esac
  case "$2" in ''|*[!0-9]*) echo invalid-selection; return 1;; esac
  [ "$1" -le 7 ] && [ "$2" -le 2147483647 ] || { echo invalid-selection; return 1; }
}
validate_selection "$OWNER_SLOT" "$OWNER_SUB"
case "${1:-status}" in
  trial)
    [ "$#" != 3 ] && [ "$#" -le 4 ] || { echo invalid-selection; exit 2; }
    OWNER_SLOT=${3:-1}; OWNER_SUB=${4:-1}
    validate_selection "$OWNER_SLOT" "$OWNER_SUB"
    ;;
  enable|rollback)
    if [ "$#" -gt 1 ]; then
      [ "$#" = 3 ] || { echo invalid-selection; exit 2; }
      validate_selection "$2" "$3"
      [ "$2:$3" = "$OWNER_SLOT:$OWNER_SUB" ] || { echo owner-selection-mismatch; exit 1; }
    fi
    ;;
  reload)
    if [ "$#" -gt 2 ]; then
      [ "$#" = 3 ] || { echo invalid-selection; exit 2; }
      validate_selection "$2" "$3"
      [ "$2:$3" = "$OWNER_SLOT:$OWNER_SUB" ] || { echo owner-selection-mismatch; exit 1; }
    fi
    ;;
esac
owns_transaction() {
  [ -n "${TOKEN:-}" ] && [ -f "$STATE/transaction" ] && [ "$(cat "$STATE/transaction")" = "$TOKEN" ]
}
run_cmd() {
  if OUTPUT=$("$@" 2>&1); then STATUS=0; else STATUS=$?; fi
  printf '%s\n' "$OUTPUT"
  return "$STATUS"
}
carrier() { CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial "$@" "$OWNER_SLOT" "$OWNER_SUB"; }
restart_phone() {
  PID=$(pidof com.android.phone || true)
  [ -z "$PID" ] || kill "$PID"
}
gate() {
  BOOT=$(settings get global boot_count 2>&1)
  case "$BOOT" in ''|*[!0-9]*) return 1;; esac
  NOW=$(awk '{printf "%.0f", $1*1000}' /proc/uptime)
  run_cmd settings put global codex_wfc_stack_trial_boot "$BOOT"
  LEGACY_UNTIL=0
  [ "$OWNER_SLOT:$OWNER_SUB" != 1:1 ] || LEGACY_UNTIL=$((NOW + 90000))
  run_cmd settings put global codex_wfc_stack_trial_until "$LEGACY_UNTIL"
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_sub" "$OWNER_SUB"
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_boot" "$BOOT"
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_until" "$((NOW + 90000))"
}
rollback() {
  [ -f "$STATE/transaction" ] || { echo rollback=NOT_NEEDED; return; }
  run_cmd settings put global codex_wfc_stack_trial_until 0 || true
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_until" 0 || true
  run_cmd settings delete global "codex_wfc_stack_slot_${OWNER_SLOT}_sub" || true
  run_cmd settings delete global "codex_wfc_stack_slot_${OWNER_SLOT}_boot" || true
  rm -f "$STATE/enabled" "$STATE/trial-running"
  # A file-only restore can leave the loader's live override selected. Clear
  # both layers and wait for its asynchronous deletion before restoring XML.
  if ! carrier clear; then echo rollback=OVERRIDE_CLEAR_RETRY_REQUIRED; return 1; fi
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
  if ! carrier verify-restored; then echo rollback=PROVIDER_RESTORE_RETRY_REQUIRED; return 1; fi
  [ ! -f "$COMPANION" ] || sh "$COMPANION" start
  [ "$POLICY_OK" = 1 ] || { echo rollback=PROVIDERS_RESTORED_POLICY_RETRY_REQUIRED; return 1; }
  rm -f "$STATE/transaction"
  rm -f "$STATE/providers-before.bin" "$STATE/components" "$STATE/owner"
  mv "$STATE/baseline" "$STATE/baseline-restored-$(date +%s)"
  echo rollback=RESTORED
}
case "${1:-status}" in
  status)
    echo "mode=$(getprop ro.telephony.iwlan_operation_mode)"
    [ ! -f "$STATE/transaction" ] || echo transaction=ACTIVE
    [ ! -f "$STATE/enabled" ] || echo persistent=ENABLED
    echo component_selection=1
    echo identity_selection=1
    if [ -f "$STATE/transaction" ]; then
      echo "owner_slot=$OWNER_SLOT"
      echo "owner_sub=$OWNER_SUB"
      if [ -f "$STATE/owner" ]; then echo owner_schema=EXPLICIT; else echo owner_schema=LEGACY_FIXED; fi
    fi
    echo "components=$(cat "$STATE/components" 2>/dev/null || echo 7)"
    carrier read
    ;;
  trial)
    [ ! -f "$STATE/transaction" ] || { echo transaction-already-active; exit 1; }
    [ "$(getprop ro.product.device)" = raphael ]
    [ "$(getprop ro.build.version.sdk)" = 30 ]
    carrier check
    # Never turn an orphaned replacement override into the original baseline.
    # Recovery of an older broken transaction must preserve its saved original.
    carrier baseline-check
    COMPONENTS=${2:-7}
    case "$COMPONENTS" in 1|2|3|4|5|6|7) ;; *) echo invalid-components; exit 2;; esac
    [ ! -f "$STATE/transaction" ] || { echo transaction-already-active; exit 1; }
    [ ! -f "$STATE/providers-before.bin" ] || { echo stale-provider-snapshot-refused; exit 1; }
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
    TOKEN="$(settings get global boot_count)-$(cut -d. -f1 /proc/uptime)-$$"
    printf '%s\n' "$OWNER_SLOT:$OWNER_SUB" > "$STATE/owner.new"
    mv "$STATE/owner.new" "$STATE/owner"
    printf '%s\n' "$TOKEN" > "$STATE/transaction"
    touch "$STATE/trial-running"
    printf '%s\n' "$COMPONENTS" > "$STATE/components"
    if ! carrier snapshot; then echo provider-snapshot-failed; rollback; exit 1; fi
    nohup sh "$MODDIR/control.sh" watchdog "$TOKEN" 9>&- > "$STATE/watchdog.log" 2>&1 < /dev/null &
    echo $! > "$STATE/watchdog.pid"
    if ! carrier apply "$COMPONENTS"; then echo provider-apply-failed; rollback; exit 1; fi
    sleep 2
    if ! carrier verify "$COMPONENTS"; then echo provider-verify-failed; rollback; exit 1; fi
    FOUND=0
    if [ "$((COMPONENTS & 1))" != 0 ]; then SELECTED_PACKAGE=dev.codex.vowifi.iwlan
    elif [ "$((COMPONENTS & 2))" != 0 ]; then SELECTED_PACKAGE=dev.codex.vowifi.qns
    else SELECTED_PACKAGE=me.phh.ims; fi
    for F in "$FILES"/carrierconfig-*-override-*.xml; do
      [ -f "$F" ] || continue
      grep -q "$SELECTED_PACKAGE" "$F" && FOUND=1
    done
    [ "$FOUND" = 1 ] || { echo persist-format-unverified; rollback; exit 1; }
    if ! gate; then echo trial-gate-failed; rollback; exit 1; fi
    [ ! -f "$COMPANION" ] || sh "$COMPANION" stop
    # Operation mode is global. Change it only for a replacement data service.
    if [ "$((COMPONENTS & 1))" != 0 ]; then resetprop -n ro.telephony.iwlan_operation_mode AP-assisted; fi
    restart_phone
    echo trial=STARTED
    ;;
  watchdog)
    TOKEN=${2:-}
    owns_transaction || exit 0
    DEADLINE=$(( $(cut -d. -f1 /proc/uptime) + 300 ))
    while owns_transaction && [ -f "$STATE/trial-running" ] && [ "$(cut -d. -f1 /proc/uptime)" -lt "$DEADLINE" ]; do
      sh "$MODDIR/control.sh" renew "$TOKEN" || break
      sleep 6
    done
    sh "$MODDIR/control.sh" expire "$TOKEN"
    ;;
  renew)
    TOKEN=${2:-}
    owns_transaction || { echo worker=STALE; exit 1; }
    gate
    ;;
  expire)
    TOKEN=${2:-}
    owns_transaction || { echo worker=STALE; exit 0; }
    [ -f "$STATE/enabled" ] || rollback
    ;;
  rollback-owner)
    TOKEN=${2:-}
    owns_transaction || { echo worker=STALE; exit 0; }
    rollback
    ;;
  enable)
    [ -f "$STATE/transaction" ]
    carrier check
    [ "$(cat "$STATE/components" 2>/dev/null || echo 7)" = 7 ] || { echo partial-components-trial-only; exit 1; }
    [ ! -f "$STATE/enabled" ] || { echo replacement=ALREADY_PERSISTENT; exit 0; }
    touch "$STATE/enabled"
    rm -f "$STATE/trial-running"
    TOKEN=$(cat "$STATE/transaction")
    nohup sh "$MODDIR/control.sh" supervise "$TOKEN" 9>&- > "$STATE/supervisor.log" 2>&1 < /dev/null &
    echo replacement=PERSISTENT
    ;;
  supervise)
    TOKEN=${2:-$(cat "$STATE/transaction" 2>/dev/null || true)}
    owns_transaction || exit 0
    LAST_RECOVERY=$(( $(cut -d. -f1 /proc/uptime) - 300 ))
    while [ -f "$STATE/enabled" ] && owns_transaction; do
      if [ ! -d "$MODULE" ] || [ -f "$MODULE/disable" ] || [ -f "$MODULE/remove" ]; then sh "$MODDIR/control.sh" rollback-owner "$TOKEN"; exit; fi
      # Binder services disappear during shutdown. Preserve the transaction so
      # the next boot can resume it instead of silently undoing persistence.
      sh "$MODDIR/control.sh" renew "$TOKEN" || { echo supervisor=GATE_UNAVAILABLE; exit 1; }
      # A killed IWLAN process destroys its tunnel while MIUI may retain stale
      # LinkProperties. Rebuild bindings only when idle, with a five-minute
      # backoff, and only while the tested SIM is still installed.
      MISSING=0
      for PKG in dev.codex.vowifi.iwlan dev.codex.vowifi.qns me.phh.ims; do
        pidof "$PKG" >/dev/null 2>&1 || MISSING=1
      done
      NOW_SECONDS=$(cut -d. -f1 /proc/uptime)
      if [ "$MISSING" = 1 ] && [ "$((NOW_SECONDS - LAST_RECOVERY))" -ge 300 ]; then
        if sh "$MODDIR/control.sh" reload "$TOKEN"; then
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
    if [ "$#" = 2 ]; then TOKEN=$2; owns_transaction || { echo worker=STALE; exit 0; }; fi
    carrier check
    REGISTRY=$(dumpsys telephony.registry 2>&1 | grep -E '^[[:space:]]+mCallState=[0-9]')
    [ -n "$REGISTRY" ] || { echo call-state-unavailable; exit 1; }
    if printf '%s\n' "$REGISTRY" | grep -Eq 'mCallState=[12]'; then echo active-call-refused; exit 1; fi
    # Migrate an active pre-0.4 transaction without changing its saved baseline.
    if [ ! -s "$STATE/transaction" ]; then
      printf '%s\n' "$(settings get global boot_count)-$(cut -d. -f1 /proc/uptime)-$$" > "$STATE/transaction"
    fi
    gate
    restart_phone
    echo framework-reload=REQUESTED
    ;;
  rollback) rollback ;;
  *) echo 'status|trial|enable|reload|rollback'; exit 2 ;;
esac
