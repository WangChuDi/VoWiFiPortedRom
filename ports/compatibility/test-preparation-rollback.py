"""Explicit Android-root fixture: failed preparation never changes providers.

Every /data/adb path is redirected into a unique /data/local/tmp fixture.
Mutating platform commands are guarded; no production helper or service runs.
"""
from pathlib import Path
import argparse,shlex,subprocess,uuid

parser=argparse.ArgumentParser()
parser.add_argument('--adb',required=True)
parser.add_argument('--serial',required=True)
args=parser.parse_args()
base=Path(__file__).resolve().parent
fixture='/data/local/tmp/codex-prepare-fixture-'+uuid.uuid4().hex
state=fixture+'/data-adb/codex_vowifi_stack'
source=(base.parent/'android11/stack/module/control.sh').read_text(encoding='utf-8')
source=source.replace('/data/adb',fixture+'/data-adb')
marker='case "${1:-status}" in\n  status)'
assert source.count(marker)==1
guard='''unexpected() { echo unexpected-platform-operation; return 97; }
carrier() { unexpected; }
settings() { unexpected; }
restart_phone() { unexpected; }
dumpsys() { unexpected; }
resetprop() { unexpected; }
'''
source=source.replace(marker,guard+marker)
out=base/'out';out.mkdir(exist_ok=True)
local=out/(fixture.rsplit('/',1)[1]+'.sh');local.write_text(source,encoding='utf-8',newline='\n')
def adb(*command):
    result=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=30)
    if result.returncode:raise RuntimeError('fixture-device-command-failed')
    return result.stdout.decode('utf-8','replace')
adb('shell','mkdir',fixture)
adb('push',str(local),fixture+'/control.sh')
command=f'''set -eu
mkdir -p {state}/baseline
printf original > {state}/baseline/original.xml
printf partial > {state}/override-before.xml
printf partial > {state}/persistence.properties.new
printf 1:1 > {state}/owner
printf fixture-token > {state}/transaction
printf PREPARING > {state}/phase
touch {state}/trial-running
sh {fixture}/control.sh rollback 1 1
test ! -f {state}/transaction
test ! -f {state}/trial-running
test ! -f {state}/override-before.xml
test ! -f {state}/persistence.properties.new
test "$(cat {state}/baseline-prepare-failed-*/original.xml)" = original
test "$(cat {state}/baseline-prepare-failed-*/override-before.xml)" = partial
echo preparation-rollback-test=PASS'''
result=adb('shell','su -c '+shlex.quote(command))
assert 'unexpected-platform-operation' not in result
assert 'rollback=PREPARATION_DISCARDED_NO_PROVIDER_CHANGE' in result
assert 'preparation-rollback-test=PASS' in result
print('preparation-rollback-test=PASS provider-commands=0 partial-evidence=ARCHIVED')
