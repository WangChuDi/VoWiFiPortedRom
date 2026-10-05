"""Android shell integration of the real coordinator with isolated fake backends.

Requires explicit ADB/root. All device paths are inside a unique private fixture;
platform calls, carrier Binder calls and background spawning are replaced. Tests
exercise controller ownership/global coordination, not live dual-SIM registration.
"""
from pathlib import Path
import argparse,shlex,subprocess,uuid
parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--serial',required=True)
args=parser.parse_args();base=Path(__file__).resolve().parent
fixture='/data/local/tmp/codex-multi-fixture-'+uuid.uuid4().hex
root=fixture+'/data-adb/codex_vowifi_stack';module=fixture+'/module'
out=base/'out'/fixture.rsplit('/',1)[1];out.mkdir(parents=True)
def adb(*command):
    result=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=45)
    if result.returncode:raise RuntimeError('fixture-command-failed')
    return result.stdout.decode('utf-8','replace')
for name in ('control.sh','subscription-control.sh'):
    source=(base.parent/'android11/stack/module'/name).read_text(encoding='utf-8').replace('/data/adb',fixture+'/data-adb')
    if name=='subscription-control.sh':
        marker='case "${1:-status}" in\n  status)';assert source.count(marker)==1
        guard='''carrier() {
  if [ "$1" = check ] && [ -f "$FIXTURE/refuse-owner-check" ]; then return 1; fi
  case "$1" in
    snapshot) printf original > "$STATE/providers-before.bin"; printf fixture > "$STATE/persistence.properties";;
    apply) printf replacement > "$STATE/live-provider";;
    clear) printf original > "$STATE/live-provider";;
    verify-restored) [ "$(cat "$STATE/live-provider")" = original ] || return 1;;
    read) echo config_ims_mmtel_package_override_string=fixture;;
  esac
  echo provider-fixture=ACK
}
'''
        source=source.replace(marker,guard+marker)
    (out/name).write_text(source,encoding='utf-8',newline='\n')
stubs={
 'getprop':'''case "$1" in
ro.product.device) echo raphael;; ro.build.version.sdk) echo 30;;
ro.telephony.iwlan_operation_mode) cat "$FIXTURE/mode";; esac''',
 'resetprop':'printf "%s" "$3" > "$FIXTURE/mode"',
 'settings':'''case "$1" in
get) echo 77;;
put) printf "%s" "$4" > "$FIXTURE/leases/$3";;
delete) rm -f "$FIXTURE/leases/$3";; esac''',
 'dumpsys':'printf "  mCallState=0\\n  mCallState=0\\n"',
 'pidof':'exit 1','sleep':'exit 0','nohup':'exit 0',
 'app_process':'echo provider-fixture=ACK',
}
for name,body in stubs.items():(out/('stub-'+name)).write_text('#!/system/bin/sh\n'+body+'\n',encoding='utf-8',newline='\n')
for name in ('carrier-trial.zip','recovery-boot.sh'):(out/name).write_text('fixture',encoding='utf-8')
(out/'companion.sh').write_text('#!/system/bin/sh\nprintf "%s\\n" "$1" >> "$FIXTURE/companion-events"\n',encoding='utf-8',newline='\n')
adb('shell','mkdir','-p',module,fixture+'/bin',fixture+'/leases',fixture+'/data-adb/modules/codex_vowifi_sms')
for path in out.iterdir():
    destination=(fixture+'/bin/'+path.name[5:])if path.name.startswith('stub-')else(
        fixture+'/data-adb/modules/codex_vowifi_sms/control.sh'if path.name=='companion.sh'else module+'/'+path.name)
    adb('push',str(path),destination)
command=f'''set -eu
export FIXTURE={fixture}
export PATH={fixture}/bin:$PATH
chmod 700 {fixture}/bin/*
printf legacy > "$FIXTURE/mode"
C={module}/control.sh
A={root}/transactions/slot-0-sub-100
B={root}/transactions/slot-1-sub-101
sh "$C" trial 7 0 100
sh "$C" enable 0 100
sh "$C" trial 7 1 101
sh "$C" enable 1 101
TOKEN_A=$(cat "$A/transaction")
TOKEN_B=$(cat "$B/transaction")
LEASE_B=$(cat "$FIXTURE/leases/codex_wfc_stack_slot_1_until")
BASE_B=$(sha256sum "$B/providers-before.bin")
touch "$FIXTURE/refuse-owner-check"
if sh "$C" renew "$TOKEN_B" 1 101; then exit 84; fi
test "$(cat "$FIXTURE/leases/codex_wfc_stack_slot_1_until")" = "$LEASE_B"
rm "$FIXTURE/refuse-owner-check"
rm {root}/phone-reload-clock
sh "$C" reload-owner "$TOKEN_A" 0 100
sh "$C" reload-owner "$TOKEN_B" 1 101 > "$FIXTURE/second-background-reload"
grep -q 'framework-reload=RECENT_BACKGROUND_REQUEST' "$FIXTURE/second-background-reload"
sh "$C" rollback 0 100
test "$(cat "$B/transaction")" = "$TOKEN_B"
test "$(sha256sum "$B/providers-before.bin")" = "$BASE_B"
test "$(cat "$FIXTURE/leases/codex_wfc_stack_slot_1_until")" = "$LEASE_B"
test "$(cat "$FIXTURE/mode")" = AP-assisted
test "$(grep -c '^start$' "$FIXTURE/companion-events" || true)" = 0
if sh "$C" enable 1 999; then exit 81; fi
sh "$C" expire "$TOKEN_A" 1 101
test "$(cat "$B/transaction")" = "$TOKEN_B"
sh "$C" rollback 1 101
test ! -f "$B/transaction"
test "$(cat "$FIXTURE/mode")" = legacy
test "$(grep -c '^start$' "$FIXTURE/companion-events")" = 1
sh "$C" trial 7 0 100
NEW_A=$(cat "$A/transaction")
test "$NEW_A" != "$TOKEN_A"
sh "$C" expire "$TOKEN_A" 0 100
test "$(cat "$A/transaction")" = "$NEW_A"
if sh "$C" trial 7 0 102; then exit 82; fi
sh "$C" trial 4 1 101
if sh "$C" enable 1 101; then exit 83; fi
sh "$C" rollback 0 100
test -f "$B/transaction"
test "$(cat "$FIXTURE/mode")" = legacy
test "$(grep -c '^start$' "$FIXTURE/companion-events")" = 1
sh "$C" rollback-all
test ! -f "$A/transaction"
test ! -f "$B/transaction"
test "$(grep -c '^start$' "$FIXTURE/companion-events")" = 2
mkdir -p {root}/baseline
printf '0:100' > {root}/owner
printf 'migration-fixture-token' > {root}/transaction
printf 7 > {root}/components
printf ACTIVE > {root}/phase
printf original > {root}/providers-before.bin
printf fixture > {root}/persistence.properties
printf original-xml > {root}/override-before.xml
printf legacy > {root}/mode-before
touch {root}/enabled {root}/baseline/ready
sh "$C" migrate
test ! -f {root}/transaction
test "$(cat "$A/transaction")" = migration-fixture-token
test "$(cat "$A/override-before.xml")" = original-xml
# Model interruption after the slot directory was committed and old owner/XML
# had already been archived, but before the root transaction was moved.
printf '0:100' > {root}/migration-owner
printf migration-fixture-token > {root}/transaction
sh "$C" migrate
test ! -f {root}/transaction
test ! -f {root}/migration-owner
test "$(cat "$A/transaction")" = migration-fixture-token
sh "$C" trial 7 1 1
LEGACY_LEASE=$(cat "$FIXTURE/leases/codex_wfc_stack_trial_until")
sh "$C" rollback 0 100
test "$(cat "$FIXTURE/leases/codex_wfc_stack_trial_until")" = "$LEGACY_LEASE"
sh "$C" rollback 1 1
test "$(cat "$FIXTURE/leases/codex_wfc_stack_trial_until")" = 0
test "$(cat "$FIXTURE/mode")" = legacy
echo multi-controller-tests=PASS
'''
result=adb('shell','su -c '+shlex.quote(command))
assert 'multi-controller-tests=PASS' in result
print('multi-controller-tests=PASS other-owner-state-and-lease=PRESERVED shared-mode=COORDINATED stale-token=REFUSED same-slot-conflict=REFUSED companion=LAST_OWNER_ONLY')
