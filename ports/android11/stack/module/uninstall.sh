#!/system/bin/sh
MODDIR=${0%/*}
if sh "$MODDIR/control.sh" migrate && sh "$MODDIR/control.sh" rollback-all; then
  rm -f /data/adb/service.d/codex-vowifi-stack-recovery.sh
fi
for PKG in dev.codex.vowifi.iwlan dev.codex.vowifi.qns me.phh.ims; do
  cmd deviceidle whitelist -"$PKG"
done
