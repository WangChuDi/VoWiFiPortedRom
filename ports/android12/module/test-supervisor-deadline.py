# SPDX-License-Identifier: GPL-2.0
"""Run the actual shell readiness function with slow/failing probes, without starting a daemon."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--adb',required=True);p.add_argument('--guest',action='append',required=True);p.add_argument('--output',type=Path);a=p.parse_args()
if a.output is not None and a.output.exists():raise SystemExit('fresh-report-path-required')
source=(B/'control.sh').read_text(encoding='utf-8')
body='ensure_supervisor() {\n'+source.split('ensure_supervisor() {\n',1)[1].split('\n}\n',1)[0]+'\n}\n'
clock='uptime_seconds() {\n'+source.split('uptime_seconds() {\n',1)[1].split('\n}\n',1)[0]+'\n}\n'
fixtures=clock+'''
observed=$(uptime_seconds) || exit 10
case "$observed" in ''|*[!0-9]*) exit 11 ;; esac
'''+body+'''
recovery_check() { return 0; }
sh() { return 0; }
sleep() { return 0; }
MODDIR=/nonexistent-fixture-module
ROOT=/dev/null
'''
# The production daemon-launch line is an orthogonal effect, not the readiness
# policy under test. Replace only its redirection target with /dev/null. All
# deadline/probe/control-flow code remains the byte-exact source function body.
fixtures=fixtures.replace('>"$ROOT/selection-supervisor.json"','>/dev/null')
fixtures+='''
alive() { calls=$((calls + 1)); return 1; }
uptime_seconds() { printf '%s\\n' "$((calls * 30))"; }
calls=0
if ensure_supervisor; then exit 12; fi
[ "$calls" -eq 2 ] || exit 13
alive() { calls=$((calls + 1)); return 0; }
calls=0
ensure_supervisor || exit 14
[ "$calls" -eq 1 ] || exit 15
uptime_seconds() { return 1; }
calls=0
if ensure_supervisor; then exit 16; fi
[ "$calls" -eq 0 ] || exit 17
exit 0
'''
def check(guest):
    sdk,serial=guest.split(':',1)
    def run(*args,**kwargs):return subprocess.run([a.adb,'-s',serial,*args],capture_output=True,text=True,encoding='utf-8',timeout=10,**kwargs)
    for name,value in [('ro.kernel.qemu','1'),('ro.boot.qemu.avd_name','CodexVoWiFiApi'+sdk),('ro.build.version.sdk',sdk)]:
        if run('shell','getprop',name).stdout.strip()!=value:raise ValueError('owned-guest-required')
    result=run('shell','sh','-s',input=fixtures)
    if result.returncode:raise ValueError('supervisor-deadline-contract-failed')
    return dict(sdk=int(sdk),slow_failed_probes_stop_at_overall_deadline=True,already_running_returns_without_loop=True,unavailable_monotonic_clock_refused_before_probe=True,actual_daemon_started=False)
with ThreadPoolExecutor(max_workers=len(a.guest))as pool:result=list(pool.map(check,a.guest))
report=dict(schema=1,status='passed',versions=result)
if a.output is not None:
    if a.output.resolve()!=a.output.absolute():raise SystemExit('report-alias-refused')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
