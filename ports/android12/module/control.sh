#!/system/bin/sh
MODDIR=/data/adb/modules/codex_vowifi_stack_modern
[ "$(id -u)" = 0 ] || exit 1
case "$(getprop ro.build.version.sdk)" in 31|32|33|34|35|36|37) ;; *) exit 1 ;; esac
[ "$#" = 1 ] || exit 1
case "$1" in check|prepare|restore|status) ;; *) echo 'Carrier selection requires the separate lifecycle coordinator.'; exit 1 ;; esac
[ -f "$MODDIR/controller.zip" ] || exit 1
(cd "$MODDIR" && sha256sum -c payload.sha256 >/dev/null 2>&1) || exit 1
if [ "$1" = prepare ]; then
  [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 1
  ROOT=/data/adb/codex_vowifi_stack_modern
  RECOVERY="$ROOT/installation/recovery"
  HOOK=/data/adb/service.d/codex-modern-installation-recovery.sh
  umask 077
  mkdir -p "$RECOVERY" /data/adb/service.d || exit 1
  [ "$(readlink -f "$RECOVERY")" = "$RECOVERY" ] && [ "$(readlink -f /data/adb/service.d)" = /data/adb/service.d ] || exit 1
  for target in "$RECOVERY/controller.zip" "$RECOVERY/recovery-boot.sh" "$HOOK" "$HOOK.new"; do
    [ ! -L "$target" ] || exit 1
  done
  # Publish an independent recovery entry before adding any permission. Removing
  # the module leaves the helper and baseline available for guarded restoration.
  cp "$MODDIR/controller.zip" "$RECOVERY/controller.zip" || exit 1
  cp "$MODDIR/recovery-boot.sh" "$RECOVERY/recovery-boot.sh" || exit 1
  chmod 600 "$RECOVERY/controller.zip" || exit 1
  chmod 700 "$RECOVERY/recovery-boot.sh" || exit 1
  printf '%s\n' '#!/system/bin/sh' 'exec sh /data/adb/codex_vowifi_stack_modern/installation/recovery/recovery-boot.sh' >"$HOOK.new" || exit 1
  chmod 700 "$HOOK.new" && mv "$HOOK.new" "$HOOK" || exit 1
fi
CLASSPATH="$MODDIR/controller.zip" timeout 40s app_process /system/bin ModernInstallationController "$1"
