"""Isolated Android shell fixture for the production APK verification/update gate."""
from pathlib import Path
import argparse,shlex,subprocess,uuid
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
a=p.parse_args();b=Path(__file__).resolve().parent
fixture='/data/local/tmp/codex-apk-update-fixture-'+uuid.uuid4().hex
out=b/'out';out.mkdir(exist_ok=True)
source=(b.parent/'android11/stack/module/service.sh').read_text(encoding='utf-8')
source=source[source.index('ensure_installed() {'):source.index('\nprepare_apps() {')]
local=out/(fixture.rsplit('/',1)[1]+'.sh')
local.write_text('''#!/system/bin/sh
set -u
FIXTURE=${0%/*}
MODDIR=$FIXTURE
pm() {
  case "$1" in
    path) printf 'package:%s/installed.apk\n' "$FIXTURE";;
    install)
      echo invoked >> "$FIXTURE/install-calls"
      cat > "$FIXTURE/stream.apk"
      [ "$2" = -r ] && [ "$3" = -S ] || return 1
      [ "$(stat -c %s "$FIXTURE/stream.apk")" = "$4" ] || return 1
      [ ! -f "$FIXTURE/corrupt-update" ] && cp "$FIXTURE/stream.apk" "$FIXTURE/installed.apk"
      echo Success;;
    *) return 99;;
  esac
}
dumpsys() { echo invoked >> "$FIXTURE/state-calls"; cat "$FIXTURE/call-states"; }
# Production uses timeout to bound Binder. This fixture replaces only that command.
timeout() { shift; "$@"; }
'''+source+'''
mkdir -p "$MODDIR/system/priv-app/Api30Iwlan"
printf new-payload > "$MODDIR/system/priv-app/Api30Iwlan/Api30Iwlan.apk"
printf old-payload > "$FIXTURE/installed.apk"
printf 'mCallState=0\nmCallState=1\n' > "$FIXTURE/call-states"
if ensure_installed fixture.package Api30Iwlan; then exit 11; fi
test ! -f "$FIXTURE/install-calls" || exit 12
printf 'mCallState=0\n' > "$FIXTURE/call-states"
if ensure_installed fixture.package Api30Iwlan; then exit 13; fi
test ! -f "$FIXTURE/install-calls" || exit 14
printf 'mCallState=0\nmCallState=0\n' > "$FIXTURE/call-states"
ensure_installed fixture.package Api30Iwlan || exit 15
test "$(cat "$FIXTURE/installed.apk")" = new-payload || exit 16
before=$(wc -l < "$FIXTURE/state-calls")
ensure_installed fixture.package Api30Iwlan || exit 17
test "$(wc -l < "$FIXTURE/state-calls")" = "$before" || exit 18
test "$(wc -l < "$FIXTURE/install-calls")" = 1 || exit 19
printf old-again > "$FIXTURE/installed.apk"
touch "$FIXTURE/corrupt-update"
if ensure_installed fixture.package Api30Iwlan; then exit 20; fi
test "$(cat "$FIXTURE/installed.apk")" = old-again || exit 21
echo service-apk-update-tests=PASS
''',encoding='utf-8',newline='\n')
def adb(*args):
    r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,timeout=45)
    if r.returncode:raise RuntimeError('fixture-command-failed: '+r.stdout.decode('utf-8','replace'))
    return r.stdout.decode('utf-8','replace')
adb('shell','mkdir',fixture);adb('push',str(local),fixture+'/test.sh')
result=adb('shell','su -c '+shlex.quote('sh '+fixture+'/test.sh'))
if 'service-apk-update-tests=PASS' not in result:raise RuntimeError('fixture-not-passed')
print('service-apk-update-tests=PASS active-and-unknown-calls=REFUSED matching-apk=UNCHANGED streamed-update=VERIFIED corrupt-update=REFUSED')
