#!/system/bin/sh
# Root coordinator. Shared mutations are serialized by subscription-control.sh.
set -eu
MODDIR=${0%/*}
ROOT=/data/adb/codex_vowifi_stack
umask 077
[ ! -L "$ROOT/transactions" ] || { echo state-path-refused; exit 1; }
mkdir -p "$ROOT/transactions"
chmod 700 "$ROOT" "$ROOT/transactions"
valid() {
  case "$1" in ''|*[!0-9]*) echo invalid-selection; return 1;; esac
  case "$2" in ''|*[!0-9]*) echo invalid-selection; return 1;; esac
  [ "$1" -le 7 ] && [ "$2" -le 2147483647 ] || { echo invalid-selection; return 1; }
}
owners() {
  for DIRECTORY in "$ROOT"/transactions/slot-*-sub-*; do
    [ -f "$DIRECTORY/transaction" ] || continue
    [ ! -L "$DIRECTORY" ] || { echo state-path-refused >&2; return 1; }
    OWNER=$(cat "$DIRECTORY/owner")
    SLOT=${OWNER%%:*}; SUB=${OWNER#*:}
    valid "$SLOT" "$SUB" >&2 || return 1
    [ "$DIRECTORY" = "$ROOT/transactions/slot-$SLOT-sub-$SUB" ] || { echo state-record-invalid >&2; return 1; }
    printf '%s\n' "$OWNER"
  done
}
select_owner() {
  valid "$1" "$2"
  SLOT=$1; SUB=$2
  STATE="$ROOT/transactions/slot-$SLOT-sub-$SUB"
  [ ! -L "$STATE" ] || { echo state-path-refused; return 1; }
}
find_single() {
  LIST=$(owners)
  COUNT=$(printf '%s\n' "$LIST" | grep -c ':' || true)
  [ "$COUNT" -le 1 ] || { echo explicit-owner-required; return 1; }
  if [ "$COUNT" = 1 ]; then select_owner "${LIST%%:*}" "${LIST#*:}"; else select_owner 1 1; fi
}
lock() {
  exec 9>"$ROOT/control.lock"
  ATTEMPT=0
  until flock -n 9 9>&9 2>/dev/null; do
    ATTEMPT=$((ATTEMPT + 1))
    [ "$ATTEMPT" -lt 20 ] || { echo controller-busy; return 1; }
    sleep 1
  done
}
migrate() {
  lock
  [ -f "$ROOT/transaction" ] || { echo migration=NOT_NEEDED; return; }
  mkdir -p "$ROOT/recovery" /data/adb/service.d
  for NAME in control.sh subscription-control.sh carrier-trial.zip recovery-boot.sh; do
    cp "$MODDIR/$NAME" "$ROOT/recovery/$NAME.new"
    chmod 600 "$ROOT/recovery/$NAME.new"
    mv "$ROOT/recovery/$NAME.new" "$ROOT/recovery/$NAME"
  done
  cp "$MODDIR/recovery-boot.sh" /data/adb/service.d/codex-vowifi-stack-recovery.sh.new
  chmod 700 /data/adb/service.d/codex-vowifi-stack-recovery.sh.new
  mv /data/adb/service.d/codex-vowifi-stack-recovery.sh.new /data/adb/service.d/codex-vowifi-stack-recovery.sh
  OWNER=$(cat "$ROOT/owner" 2>/dev/null || cat "$ROOT/migration-owner" 2>/dev/null || echo 1:1)
  select_owner "${OWNER%%:*}" "${OWNER#*:}"
  if [ ! -d "$STATE" ]; then
    CODEX_WFC_STATE="$ROOT" CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial check "$SLOT" "$SUB"
    [ -f "$ROOT/persistence.properties" ] || CODEX_WFC_STATE="$ROOT" CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial persistence-adopt "$SLOT" "$SUB"
    [ -z "$(owners)" ] || { echo legacy-migration-conflict; return 1; }
    STAGE="$ROOT/transactions/.migration-slot-$SLOT-sub-$SUB"
    [ ! -e "$STAGE" ] || mv "$STAGE" "$ROOT/migration-preparation-failed-$(date +%s)-$$"
    printf '%s\n' "$OWNER" > "$ROOT/migration-owner"
    mkdir "$STAGE"
    for NAME in owner components phase transaction enabled trial-running providers-before.bin persistence.properties override-before.xml mode-before; do
      [ ! -f "$ROOT/$NAME" ] || cp -p "$ROOT/$NAME" "$STAGE/$NAME"
    done
    [ ! -d "$ROOT/baseline" ] || cp -a "$ROOT/baseline" "$STAGE/baseline"
    [ -f "$STAGE/owner" ] || printf '%s\n' "$SLOT:$SUB" > "$STAGE/owner"
    for NAME in components transaction providers-before.bin persistence.properties mode-before; do
      [ -f "$STAGE/$NAME" ] || { echo legacy-migration-evidence-missing; return 1; }
      cmp -s "$ROOT/$NAME" "$STAGE/$NAME" || { echo legacy-migration-copy-mismatch; return 1; }
    done
    for NAME in owner phase enabled trial-running override-before.xml; do
      [ ! -f "$ROOT/$NAME" ] || cmp -s "$ROOT/$NAME" "$STAGE/$NAME" || { echo legacy-migration-copy-mismatch; return 1; }
    done
    cp -p "$ROOT/mode-before" "$ROOT/shared-mode-before.new"
    mv "$ROOT/shared-mode-before.new" "$ROOT/shared-mode-before"
    mv "$STAGE" "$STATE"
  fi
  [ "$(cat "$ROOT/transaction")" = "$(cat "$STATE/transaction")" ] || { echo legacy-migration-conflict; return 1; }
  [ -f "$ROOT/shared-mode-before" ] || { echo shared-mode-baseline-unavailable; return 1; }
  CODEX_WFC_STATE="$STATE" CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial check "$SLOT" "$SUB"
  ARCHIVE="$ROOT/legacy-state-$(date +%s)-$$"
  mkdir "$ARCHIVE"
  for NAME in baseline owner components phase enabled trial-running providers-before.bin persistence.properties override-before.xml mode-before; do
    [ ! -e "$ROOT/$NAME" ] || mv "$ROOT/$NAME" "$ARCHIVE/$NAME"
  done
  mv "$ROOT/transaction" "$ARCHIVE/transaction"
  rm -f "$ROOT/migration-owner"
  echo migration=SUBSCRIPTION_STATE_READY
}
ACTION=${1:-status}
case "$ACTION" in
  migrate) migrate; exit;;
  status)
    echo "mode=$(getprop ro.telephony.iwlan_operation_mode)"
    echo component_selection=1
    echo identity_selection=1
    echo subscription_file_selection=1
    echo multi_transaction_selection=1
    LIST=$(owners); INDEX=0
    for OWNER in $LIST; do
      echo "active_owner_${INDEX}_slot=${OWNER%%:*}"
      echo "active_owner_${INDEX}_sub=${OWNER#*:}"
      INDEX=$((INDEX + 1))
    done
    echo "active_owner_count=$INDEX"
    if [ -f "$ROOT/transaction" ]; then
      echo migration_required=1
      CODEX_WFC_STATE="$ROOT" sh "$MODDIR/subscription-control.sh" status || true
      exit
    fi
    if [ "$#" = 3 ] && [ "$3" != -1 ]; then
      select_owner "$2" "$3"
    elif [ "$#" = 3 ]; then
      valid "$2" 0
      MATCH=
      for OWNER in $LIST; do
        if [ "${OWNER%%:*}" = "$2" ]; then
          [ -z "$MATCH" ] || { echo slot-recovery-ambiguous; exit 1; }
          MATCH=$OWNER
        fi
      done
      if [ -n "$MATCH" ]; then select_owner "${MATCH%%:*}" "${MATCH#*:}"; else select_owner "$2" 0; fi
    elif [ "$#" = 1 ]; then find_single
    else echo invalid-selection; exit 2
    fi
    if [ -f "$STATE/transaction" ]; then
      CODEX_WFC_STATE="$STATE" sh "$MODDIR/subscription-control.sh" status || true
    else
      echo transaction=INACTIVE
      CODEX_WFC_STATE="$STATE" CLASSPATH="$MODDIR/carrier-trial.zip" app_process /system/bin CarrierTrial read "$SLOT" "$SUB" || true
    fi
    exit;;
  rollback-all|resume-all|recover-trials)
    [ ! -f "$ROOT/transaction" ] || { echo legacy-state-needs-migration; exit 1; }
    LIST=$(owners); RESULT=0
    for OWNER in $LIST; do
      select_owner "${OWNER%%:*}" "${OWNER#*:}"
      if [ "$ACTION" = rollback-all ] || { [ "$ACTION" = recover-trials ] && [ ! -f "$STATE/enabled" ]; }; then
        sh "$MODDIR/control.sh" rollback "$SLOT" "$SUB" || RESULT=1
      elif [ "$ACTION" = resume-all ]; then
        if [ -f "$STATE/enabled" ]; then
          TOKEN=$(cat "$STATE/transaction")
          nohup sh "$MODDIR/control.sh" supervise "$TOKEN" "$SLOT" "$SUB" > "$STATE/supervisor.log" 2>&1 < /dev/null &
        else
          sh "$MODDIR/control.sh" rollback "$SLOT" "$SUB" || RESULT=1
        fi
      fi
    done
    exit "$RESULT";;
  trial)
    [ "$#" = 4 ] || { echo explicit-owner-required; exit 2; }
    select_owner "$3" "$4";;
  enable|rollback|reload)
    if [ "$#" = 3 ]; then select_owner "$2" "$3"
    elif [ "$#" = 1 ]; then find_single; set -- "$ACTION" "$SLOT" "$SUB"
    else echo explicit-owner-required; exit 2; fi;;
  watchdog|supervise|renew|expire|rollback-owner|reload-owner)
    [ "$#" = 4 ] || { echo explicit-owner-required; exit 2; }
    TOKEN=$2; select_owner "$3" "$4"
    if [ ! -f "$STATE/transaction" ] || [ "$(cat "$STATE/transaction")" != "$TOKEN" ]; then
      echo worker=STALE
      [ "$ACTION" != renew ] || exit 1
      exit 0
    fi
    if [ "$ACTION" = reload-owner ]; then export CODEX_BACKGROUND_RELOAD=1; set -- reload "$TOKEN"; fi;;
  *) echo 'status|trial MASK SLOT SUB|enable SLOT SUB|reload SLOT SUB|rollback SLOT SUB'; exit 2;;
esac
[ ! -f "$ROOT/transaction" ] || { echo legacy-state-needs-migration; exit 1; }
if [ "$ACTION" = trial ]; then
  for OWNER in $(owners); do
    [ "${OWNER%%:*}" != "$SLOT" ] || [ "$OWNER" = "$SLOT:$SUB" ] || { echo slot-owner-conflict; exit 1; }
  done
fi
CODEX_WFC_STATE="$STATE" exec sh "$MODDIR/subscription-control.sh" "$@"
