#!/system/bin/sh
SKIPUNZIP=0
[ "$(getprop ro.product.device)" = raphael ] || abort "Only raphael validated"
[ "$(getprop ro.build.version.sdk)" = 30 ] || abort "Only Android 11/API30 supported"
ui_print "Installing replacement service APKs; provider switch is a separate trial."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/recovery-boot.sh" 0 0 0755
