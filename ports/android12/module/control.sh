#!/system/bin/sh
MODDIR=/data/adb/modules/codex_vowifi_stack_modern
ROOT=/data/adb/codex_vowifi_stack_modern
RECOVERY_BASE="$ROOT/installation/recovery"
[ "$(id -u)" = 0 ] || exit 1
case "$(getprop ro.build.version.sdk)" in 31|32|33|34|35|36|37) ;; *) exit 1 ;; esac
umask 077
recovery_check() {
  [ "$(readlink -f "$RECOVERY_BASE")" = "$RECOVERY_BASE" ] && [ ! -L "$RECOVERY_BASE/current" ] || return 1
  generation=$(cat "$RECOVERY_BASE/current") || return 1
  [ "${#generation}" = 64 ] || return 1
  case "$generation" in *[!0-9a-f]*) return 1 ;; esac
  RECOVERY="$RECOVERY_BASE/generations/$generation"
  [ "$(readlink -f "$RECOVERY")" = "$RECOVERY" ] && [ ! -L "$RECOVERY/controller.zip" ] && [ ! -L "$RECOVERY/recovery.sha256" ] || return 1
  (cd "$RECOVERY" && sha256sum -c recovery.sha256 >/dev/null 2>&1)
}
installation() { CLASSPATH="$MODDIR/controller.zip" timeout 45s app_process /system/bin ModernInstallationController "$1"; }
selection() { CLASSPATH="$RECOVERY/controller.zip" timeout 75s app_process /system/bin ModernSelectionController "$@"; }
alive() { CLASSPATH="$RECOVERY/controller.zip" timeout 25s app_process /system/bin ModernSelectionSupervisor alive | grep -q '"supervisor_alive":true'; }
uptime_seconds() {
  read elapsed unused < /proc/uptime || return 1
  elapsed=${elapsed%%.*}
  case "$elapsed" in ''|*[!0-9]*) return 1 ;; esac
  printf '%s\n' "$elapsed"
}
ensure_supervisor() {
  recovery_check || return 1
  began=$(uptime_seconds) || return 1
  deadline=$((began + 60))
  if alive; then return 0; fi
  sh "$MODDIR/control.sh" supervise >"$ROOT/selection-supervisor.json" 2>&1 </dev/null &
  attempt=0
  while [ "$attempt" -lt 60 ]; do
    now=$(uptime_seconds) || return 1
    [ "$now" -lt "$deadline" ] || return 1
    sleep 1
    if alive; then return 0; fi
    attempt=$((attempt + 1))
  done
  return 1
}
case "$1" in
  restore|restore-all|supervise|tick)
    [ "$#" = 1 ] && recovery_check || exit 1
    action=restore-all
    case "$1" in supervise) action=run ;; tick) action=tick ;; esac
    if [ "$action" = run ]; then
      exec env CLASSPATH="$RECOVERY/controller.zip" app_process /system/bin ModernSelectionSupervisor run
    fi
    exec env CLASSPATH="$RECOVERY/controller.zip" timeout 150s app_process /system/bin ModernSelectionSupervisor "$action"
    ;;
  check|prepare|status) [ "$#" = 1 ] || exit 1 ;;
  select) [ "$#" = 4 ] || exit 1 ;;
  retain|renew|verify|selection-restore) [ "$#" = 4 ] && recovery_check || exit 1 ;;
  recover|owner-status) [ "$#" = 3 ] && recovery_check || exit 1 ;;
  *) exit 1 ;;
esac
case "$1" in
  retain|renew|verify) action="$1"; shift; selection "$action" "$@"; exit $? ;;
  selection-restore) shift; selection restore "$@"; exit $? ;;
  recover) shift; selection recover "$@"; exit $? ;;
  owner-status) shift; selection status "$@"; exit $? ;;
esac
[ -f "$MODDIR/controller.zip" ] && [ ! -L "$MODDIR" ] || exit 1
(cd "$MODDIR" && sha256sum -c payload.sha256 >/dev/null 2>&1) || exit 1
if [ "$1" = select ]; then
  case "$4" in 1|2|3|4|5|6|7) ;; *) exit 1 ;; esac
  CLASSPATH="$MODDIR/controller.zip" timeout 25s app_process /system/bin ModernSelectionController owner-check "$2" "$3" || exit 1
fi
if [ "$1" = prepare ] || [ "$1" = select ]; then
  [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 1
  CLASSPATH="$MODDIR/controller.zip" timeout 45s app_process /system/bin ModernRecoveryPublication publish || exit 1
  if ! installation prepare; then ensure_supervisor; exit 1; fi
  ensure_supervisor || exit 1
  [ "$1" != select ] || selection trial "$2" "$3" "$4"
  exit $?
fi
installation "$1"
