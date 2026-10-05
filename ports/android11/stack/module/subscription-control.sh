#!/system/bin/sh
# Fixed-device reversible experiment. Backups stay root-private on the device.
set -eu
MODDIR=${0%/*}
ROOT=/data/adb/codex_vowifi_stack
STATE=${CODEX_WFC_STATE:-$ROOT}
export CODEX_WFC_STATE="$STATE"
COMPANION=/data/adb/modules/codex_vowifi_sms/control.sh
MODULE=/data/adb/modules/codex_vowifi_stack_api30
umask 077
if [ "${1:-status}" = trial ]; then mkdir -p "$STATE"; chmod 700 "$STATE"; fi
# Serialize mutations with a kernel lock. A process exit or reboot releases it.
# Background workers close FD9 and always carry their own transaction token.
case "${1:-status}" in
  trial|enable|reload|rollback|renew|expire|rollback-owner)
    exec 9>"$ROOT/control.lock"
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
# The coordinator chooses a subscription state; legacy recovery uses ROOT.
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
carrier() { CLASSPATH="$MODDIR/carrier-trial.zip" timeout 25s app_process /system/bin CarrierTrial "$@" "$OWNER_SLOT" "$OWNER_SUB"; }
other_transactions() {
  for DIRECTORY in "$ROOT"/transactions/slot-*-sub-*; do
    [ "$DIRECTORY" = "$STATE" ] && continue
    [ -f "$DIRECTORY/transaction" ] || continue
    if [ "${1:-all}" = iwlan ]; then
      MASK=$(cat "$DIRECTORY/components")
      case "$MASK" in 1|3|5|7) ;; *) continue;; esac
    fi
    printf '%s\n' "$DIRECTORY"
  done
}
shared_mode() {
  if [ -n "$(other_transactions iwlan)" ]; then
    resetprop -n ro.telephony.iwlan_operation_mode AP-assisted
  else
    [ -f "$ROOT/shared-mode-before" ] || { echo shared-mode-baseline-unavailable; return 1; }
    resetprop -n ro.telephony.iwlan_operation_mode "$(cat "$ROOT/shared-mode-before")"
  fi
}
restart_phone() {
  PID=$(pidof com.android.phone || true)
  [ -z "$PID" ] || kill "$PID"
  printf '%s:%s\n' "$(settings get global boot_count)" "$(cut -d. -f1 /proc/uptime)" > "$ROOT/phone-reload-clock.new"
  mv "$ROOT/phone-reload-clock.new" "$ROOT/phone-reload-clock"
}
archive_transaction() {
  if [ "$STATE" != "$ROOT" ]; then
    mkdir -p "$ROOT/archives"
    ARCHIVE="$ROOT/archives/${STATE##*/}-${1}-$(date +%s)-$$"
    mv "$STATE" "$ARCHIVE"
    return
  fi
  # Legacy preparation fixtures only. Live legacy transactions migrate first.
  ARCHIVE="$STATE/baseline-${1}-$(date +%s)-$$"
  if [ -d "$STATE/baseline" ]; then mv "$STATE/baseline" "$ARCHIVE"; else mkdir -p "$ARCHIVE"; fi
  for NAME in providers-before.bin providers-before.bin.new persistence.properties persistence.properties.new override-before.xml owner components phase transaction; do
    [ ! -f "$STATE/$NAME" ] || mv "$STATE/$NAME" "$ARCHIVE/$NAME"
  done
}
gate() {
  BOOT=$(settings get global boot_count 2>&1)
  case "$BOOT" in ''|*[!0-9]*) return 1;; esac
  NOW=$(awk '{printf "%.0f", $1*1000}' /proc/uptime)
  if [ "$OWNER_SLOT:$OWNER_SUB" = 1:1 ]; then
    run_cmd settings put global codex_wfc_stack_trial_boot "$BOOT"
    run_cmd settings put global codex_wfc_stack_trial_until "$((NOW + 90000))"
  fi
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_sub" "$OWNER_SUB"
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_boot" "$BOOT"
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_until" "$((NOW + 90000))"
}
rollback() {
  [ -f "$STATE/transaction" ] || { echo rollback=NOT_NEEDED; return; }
  if [ "$(cat "$STATE/phase" 2>/dev/null || true)" = PREPARING ]; then
    # Snapshot failure cannot justify clearing a live override that was never
    # modified by this transaction. Preserve its partial evidence privately.
    archive_transaction prepare-failed
    rm -f "$STATE/trial-running"
    echo rollback=PREPARATION_DISCARDED_NO_PROVIDER_CHANGE
    return
  fi
  REGISTRY=$(dumpsys telephony.registry 2>&1 | grep -E '^[[:space:]]+mCallState=[0-9]')
  [ -n "$REGISTRY" ] || { echo call-state-unavailable; return 1; }
  if printf '%s\n' "$REGISTRY" | grep -Eq 'mCallState=[12]'; then echo active-call-refused; return 1; fi
  # Old directory-wide baselines are adopted once, while the original card and
  # its selected replacement file still prove which filename belongs to it.
  if [ ! -f "$STATE/persistence.properties" ]; then
    if ! carrier persistence-adopt; then echo rollback=SUBSCRIPTION_MIGRATION_RETRY_REQUIRED; return 1; fi
  fi
  [ "$OWNER_SLOT:$OWNER_SUB" != 1:1 ] || run_cmd settings put global codex_wfc_stack_trial_until 0 || true
  run_cmd settings put global "codex_wfc_stack_slot_${OWNER_SLOT}_until" 0 || true
  run_cmd settings delete global "codex_wfc_stack_slot_${OWNER_SLOT}_sub" || true
  run_cmd settings delete global "codex_wfc_stack_slot_${OWNER_SLOT}_boot" || true
  rm -f "$STATE/enabled" "$STATE/trial-running"
  # A file-only restore can leave the loader's live override selected. Clear
  # both layers and wait for its asynchronous deletion before restoring XML.
  if ! carrier clear; then echo rollback=OVERRIDE_CLEAR_RETRY_REQUIRED; return 1; fi
  if ! carrier persistence-restore; then echo rollback=SELECTED_FILE_RESTORE_RETRY_REQUIRED; return 1; fi
  shared_mode
  POLICY_OK=1
  if [ -z "$(other_transactions)" ] && [ -f "$ROOT/sms-policy-before" ]; then
    CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin SmsTestPolicy restore || POLICY_OK=0
  fi
  restart_phone
  sleep 8
  if ! carrier verify-restored; then echo rollback=PROVIDER_RESTORE_RETRY_REQUIRED; return 1; fi
  if [ -z "$(other_transactions)" ]; then
    [ ! -f "$COMPANION" ] || sh "$COMPANION" start
  fi
  [ "$POLICY_OK" = 1 ] || { echo rollback=PROVIDERS_RESTORED_POLICY_RETRY_REQUIRED; return 1; }
  archive_transaction restored
  if [ -z "$(other_transactions)" ] && [ -f "$ROOT/shared-mode-before" ]; then
    mv "$ROOT/shared-mode-before" "$ARCHIVE/shared-mode-before"
  fi
  echo rollback=RESTORED
}
case "${1:-status}" in
  status)
    echo "mode=$(getprop ro.telephony.iwlan_operation_mode)"
    [ ! -f "$STATE/transaction" ] || echo transaction=ACTIVE
    [ ! -f "$STATE/enabled" ] || echo persistent=ENABLED
    echo component_selection=1
    echo identity_selection=1
    echo subscription_file_selection=1
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
    for DIRECTORY in "$ROOT"/transactions/slot-"$OWNER_SLOT"-sub-*; do
      [ "$DIRECTORY" = "$STATE" ] || [ ! -f "$DIRECTORY/transaction" ] || { echo slot-owner-conflict; exit 1; }
    done
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
    touch "$STATE/baseline/ready"
    if [ ! -f "$ROOT/shared-mode-before" ]; then
      [ -z "$(other_transactions)" ] || { echo shared-mode-baseline-unavailable; exit 1; }
      getprop ro.telephony.iwlan_operation_mode > "$ROOT/shared-mode-before.new"
      mv "$ROOT/shared-mode-before.new" "$ROOT/shared-mode-before"
    fi
    cp "$ROOT/shared-mode-before" "$STATE/mode-before"
    # Remains available if the module is disabled/removed at the next normal boot.
    mkdir -p "$ROOT/recovery" /data/adb/service.d
    for NAME in control.sh subscription-control.sh carrier-trial.zip recovery-boot.sh; do
      cp "$MODDIR/$NAME" "$ROOT/recovery/$NAME.new"
      chmod 600 "$ROOT/recovery/$NAME.new"
      mv "$ROOT/recovery/$NAME.new" "$ROOT/recovery/$NAME"
    done
    cp "$MODDIR/recovery-boot.sh" /data/adb/service.d/codex-vowifi-stack-recovery.sh
    chmod 700 "$ROOT/recovery" /data/adb/service.d/codex-vowifi-stack-recovery.sh
    TOKEN="$(settings get global boot_count)-$(cut -d. -f1 /proc/uptime)-$$"
    printf '%s\n' "$OWNER_SLOT:$OWNER_SUB" > "$STATE/owner.new"
    mv "$STATE/owner.new" "$STATE/owner"
    printf '%s\n' "$TOKEN" > "$STATE/transaction"
    printf '%s\n' PREPARING > "$STATE/phase"
    touch "$STATE/trial-running"
    printf '%s\n' "$COMPONENTS" > "$STATE/components"
    if ! carrier snapshot; then echo provider-snapshot-failed; rollback; exit 1; fi
    printf '%s\n' OVERRIDE_ATTEMPTED > "$STATE/phase"
    nohup sh "$MODDIR/control.sh" watchdog "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB" 9>&- > "$STATE/watchdog.log" 2>&1 < /dev/null &
    echo $! > "$STATE/watchdog.pid"
    if ! carrier apply "$COMPONENTS"; then echo provider-apply-failed; rollback; exit 1; fi
    sleep 2
    if ! carrier verify "$COMPONENTS"; then echo provider-verify-failed; rollback; exit 1; fi
    if ! carrier persistence-verify "$COMPONENTS"; then echo persist-format-unverified; rollback; exit 1; fi
    if ! gate; then echo trial-gate-failed; rollback; exit 1; fi
    [ ! -f "$COMPANION" ] || sh "$COMPANION" stop
    # Operation mode is global. Change it only for a replacement data service.
    if [ "$((COMPONENTS & 1))" != 0 ]; then resetprop -n ro.telephony.iwlan_operation_mode AP-assisted; fi
    restart_phone
    printf '%s\n' ACTIVE > "$STATE/phase"
    echo trial=STARTED
    ;;
  watchdog)
    TOKEN=${2:-}
    owns_transaction || exit 0
    DEADLINE=$(( $(cut -d. -f1 /proc/uptime) + 300 ))
    while owns_transaction && [ -f "$STATE/trial-running" ] && [ "$(cut -d. -f1 /proc/uptime)" -lt "$DEADLINE" ]; do
      sh "$MODDIR/control.sh" renew "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB" || break
      sleep 6
    done
    sh "$MODDIR/control.sh" expire "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB"
    ;;
  renew)
    TOKEN=${2:-}
    owns_transaction || { echo worker=STALE; exit 1; }
    # Reused numeric subIds must not reauthorize a different physical SIM.
    carrier check >/dev/null 2>&1 || { echo owner-not-ready; exit 1; }
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
    nohup sh "$MODDIR/control.sh" supervise "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB" 9>&- > "$STATE/supervisor.log" 2>&1 < /dev/null &
    echo replacement=PERSISTENT
    ;;
  supervise)
    TOKEN=${2:-$(cat "$STATE/transaction" 2>/dev/null || true)}
    owns_transaction || exit 0
    exec 8>"$STATE/supervisor.lock"
    flock -n 8 8>&8 2>/dev/null || { echo supervisor=ALREADY_RUNNING; exit 0; }
    LAST_RECOVERY=$(( $(cut -d. -f1 /proc/uptime) - 300 ))
    while [ -f "$STATE/enabled" ] && owns_transaction; do
      if [ ! -d "$MODULE" ] || [ -f "$MODULE/disable" ] || [ -f "$MODULE/remove" ]; then sh "$MODDIR/control.sh" rollback-owner "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB"; exit; fi
      # Binder services disappear during shutdown. Preserve the transaction so
      # the next boot can resume it instead of silently undoing persistence.
      if ! sh "$MODDIR/control.sh" renew "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB"; then
        echo supervisor=OWNER_OR_GATE_UNAVAILABLE
        sleep 30
        continue
      fi
      # A killed IWLAN process destroys its tunnel while MIUI may retain stale
      # LinkProperties. Rebuild bindings only when idle, with a five-minute
      # backoff, and only while the tested SIM is still installed.
      MISSING=0
      for PKG in dev.codex.vowifi.iwlan dev.codex.vowifi.qns me.phh.ims; do
        pidof "$PKG" >/dev/null 2>&1 || MISSING=1
      done
      NOW_SECONDS=$(cut -d. -f1 /proc/uptime)
      if [ "$MISSING" = 1 ] && [ "$((NOW_SECONDS - LAST_RECOVERY))" -ge 300 ]; then
        if sh "$MODDIR/control.sh" reload-owner "$TOKEN" "$OWNER_SLOT" "$OWNER_SUB"; then
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
    if [ "${CODEX_BACKGROUND_RELOAD:-0}" = 1 ] && [ -f "$ROOT/phone-reload-clock" ]; then
      CLOCK=$(cat "$ROOT/phone-reload-clock")
      BOOT=$(settings get global boot_count)
      NOW=$(cut -d. -f1 /proc/uptime)
      LAST_BOOT=${CLOCK%%:*}; LAST_SECONDS=${CLOCK#*:}
      case "$LAST_SECONDS" in ''|*[!0-9]*) echo reload-clock-unavailable; exit 1;; esac
      if [ "$LAST_BOOT" = "$BOOT" ] && [ "$NOW" -ge "$LAST_SECONDS" ] && [ "$((NOW - LAST_SECONDS))" -lt 300 ]; then
        echo framework-reload=RECENT_BACKGROUND_REQUEST
        exit 0
      fi
    fi
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
