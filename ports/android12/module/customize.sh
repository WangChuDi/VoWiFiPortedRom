#!/system/bin/sh
SKIPMOUNT=false
sdk=$(getprop ro.build.version.sdk)
case "$sdk" in 31|32|33|34|35|36|37) ;; *) abort "This payload requires Android SDK 31-37; keep the Android 11 module on SDK30." ;; esac
old=/data/adb/modules/codex_vowifi_stack
if [ -d "$old" ] && [ ! -f "$old/disable" ] && [ ! -f "$old/remove" ]; then
  abort "Disable the API30 stack before mounting modern packages with the same package IDs."
fi
(cd "$MODPATH" && sha256sum -c payload.sha256 >/dev/null 2>&1) || abort "Modern payload digest verification failed."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/control.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/recovery-boot.sh" 0 0 0755
ui_print "Signed services will be mounted after reboot."
ui_print "This installation stage prepares permissions only; it does not select carrier replacements."
