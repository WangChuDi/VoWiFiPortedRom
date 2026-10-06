#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Run an isolated blocked fixture with watchdog classes loaded from the actual APK."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,time,zipfile,re,os
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--adb',required=True);p.add_argument('--guest',required=True);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():raise SystemExit('fresh-report-required')
a.output=a.output.absolute()
if a.output.resolve()!=a.output:raise SystemExit('report-alias-refused')
sdk,serial=a.guest.split(':',1);sdk=int(sdk)
if not 31<=sdk<=37 or not re.fullmatch(r'emulator-[0-9]+',serial):raise SystemExit('owned-modern-emulator-required')
apk=a.apk.absolute()
if apk.resolve()!=apk or not apk.is_file():raise SystemExit('canonical-apk-required')
def adb(*parts,timeout=12):return subprocess.run([a.adb,'-s',serial,*parts],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
def shell(command):
    r=adb('shell',command)
    if r.returncode:raise ValueError('fixed-guest-command-unconfirmed')
    return r.stdout.strip()
if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(sdk) or shell('getprop ro.boot.qemu.avd_name')!='CodexVoWiFiApi'+str(sdk) or shell('id -u')!='0':raise SystemExit('named-root-emulator-required')
out=B/'out/watchdog-device-fixture';out.mkdir(parents=True,exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
android=TOOLS/'android-all-11.jar'
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(B/'out/classes.jar')+os.pathsep+str(android),'-d',str(classes),str(B/'tests/DiagnosticWatchdogDeviceCheck.java')],check=True)
fixture=out/'fixture.jar'
with zipfile.ZipFile(fixture,'w')as archive:
    for path in classes.rglob('*.class'):archive.write(path,path.relative_to(classes).as_posix())
dex=out/'fixture.zip'
subprocess.run([JAVA,'-cp',str(TOOLS/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','30','--lib',str(android),'--output',str(dex),str(fixture)],check=True)
remote_apk='/data/local/tmp/codex-diagnostic-watchdog-tool.apk';remote_dex='/data/local/tmp/codex-diagnostic-watchdog-fixture.zip'
for local,remote in ((apk,remote_apk),(dex,remote_dex)):
    if adb('push',str(local),remote,timeout=30).returncode or shell('sha256sum '+remote).split()[0]!=hashlib.sha256(local.read_bytes()).hexdigest():raise SystemExit('guest-payload-identity-unconfirmed')
began=time.monotonic();r=adb('shell','CLASSPATH='+remote_dex+':'+remote_apk+' timeout 40s app_process /system/bin dev.codex.vowifi.tool.DiagnosticWatchdogDeviceCheck',timeout=46);elapsed=time.monotonic()-began
rows=[line for line in r.stdout.splitlines()if line.startswith('{')and line.endswith('}')]
if r.returncode or len(rows)!=1:raise SystemExit('single-watchdog-result-unconfirmed')
value=json.loads(rows[0]);health=value.get('platform_health',{})
if value.get('sdk')!=sdk or value.get('fixture')is not True or value.get('stage_completed_before_stall')is not True or value.get('diagnostic_complete')is not False or value.get('engine_supported')is not False or value.get('diagnostic_stage')!='telephony' or value.get('diagnostic_error')!='TimeoutException' or not 30<=elapsed<39 or any(health.get(name,{}).get('state')!='stable'for name in ('phone','system_server')):raise SystemExit('owned-guest-watchdog-unconfirmed')
result=dict(schema=1,status='passed',sdk=sdk,apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),production_apk_watchdog_executed=True,actual_blocked_guest_process=True,single_partial_result=True,completed_stage_preserved=True,changes_disabled=True,platform_process_continuity_observed=True,elapsed_seconds=round(elapsed,2),fixture_only=True,carrier_call_sms_verified=False,dual_active_sim_verified=False)
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
