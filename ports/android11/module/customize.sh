[ "$API" = 30 ] || abort "This trial requires Android 11/API 30"
[ "$(getprop ro.product.device)" = raphael ] || abort "This trial is restricted to raphael"
set_perm_recursive "$MODPATH" 0 0 0755 0644
for script in "$MODPATH"/*.sh; do set_perm "$script" 0 0 0755; done
ui_print "Receive-only trial. Native IMS remains enabled. No vendor overlay."
ui_print "Use Action to pause/resume. Logs: /data/adb/codex_vowifi_sms"
