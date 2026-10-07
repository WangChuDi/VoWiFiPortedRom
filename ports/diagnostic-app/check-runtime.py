# SPDX-License-Identifier: GPL-2.0
"""Read-only root diagnostic/action-refusal checks; never install an engine or send traffic."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse,hashlib,json,re,subprocess

B=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',required=True)
p.add_argument('--guest',action='append',required=True,help='SDK:SERIAL of owned named root emulator')
p.add_argument('--apk',type=Path,default=B/'out/vowifi-tool-unsigned.apk')
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
tuples=[guest.split(':',1)for guest in a.guest]
if any(len(value)!=2 for value in tuples) or len({value[0]for value in tuples})!=len(tuples) or len({value[1]for value in tuples})!=len(tuples):raise SystemExit('distinct-version-and-device-workers-required')
if a.output.exists():raise SystemExit('fresh-report-path-required')
apk=a.apk.absolute()
if apk.resolve()!=apk or not apk.is_file():raise SystemExit('diagnostic-apk-unavailable')
sha=hashlib.sha256(apk.read_bytes()).hexdigest()

def check(guest):
    sdk,serial=guest.split(':',1);sdk=int(sdk)
    if not 31<=sdk<=37 or not re.fullmatch(r'emulator-[0-9]+',serial):raise ValueError('owned-modern-emulator-required')
    result=dict(schema=1,sdk=sdk,status='started',apk_sha256=sha)
    def call(*parts,timeout=60):
        return subprocess.run([a.adb,'-s',serial,*parts],capture_output=True,text=True,encoding='utf-8',timeout=timeout)
    def shell(text):
        r=call('shell',text)
        if r.returncode:raise ValueError('adb-command-unconfirmed')
        return r.stdout.strip()
    def guard():
        if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.boot.qemu.avd_name')!='CodexVoWiFiApi'+str(sdk) or shell('getprop ro.build.version.sdk')!=str(sdk) or shell('id -u')!='0':raise ValueError('named-root-emulator-required')
    def state_absent():
        return call('shell','test -e /data/adb/codex_vowifi_stack_modern').returncode!=0 and call('shell','test -e /data/adb/modules/codex_vowifi_stack_modern').returncode!=0
    remote='/data/local/tmp/codex-vowifi-diagnostic-app-check.apk'
    def entry(name,args='',nonroot=False):
        guard();prefix='su 2000 env ' if nonroot else ''
        r=call('shell',prefix+'CLASSPATH='+remote+' timeout 45s app_process /system/bin dev.codex.vowifi.tool.'+name+(' '+args if args else ''))
        lines=[line for line in r.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if len(lines)!=1:raise ValueError('single-result-required')
        value=json.loads(lines[0])
        # Keep complete root output in memory only. Public evidence is a fixed
        # whitelist below; private tokens/identities/text are never published.
        if any(key in value for key in ('owner_token','token','imsi','iccid','imei','password','sms_body')):raise ValueError('unsafe-result-field')
        return r.returncode,value
    try:
        guard()
        result['root_uid_verified']=True
        if not state_absent():raise ValueError('unused-production-profile-required')
        if call('push',str(apk),remote).returncode or shell('sha256sum '+remote).split()[0]!=sha:raise ValueError('diagnostic-payload-unconfirmed')
        rc,value=entry('RootDiagnostics','0')
        if rc or value.get('error') or value.get('sdk')!=sdk or value.get('slot')!=0 or value.get('engine')!='modern' or value.get('engine_experimental') is not True or value.get('engine_supported') is not False or value.get('controller') is not False or value.get('operator')=='23415':raise ValueError('modern-read-only-diagnostic-unconfirmed')
        result['modern_read_only_diagnostic']=True
        health=value.get('platform_health',{})
        if value.get('diagnostic_complete')is not True or value.get('diagnostic_stage')!='finished' or value.get('diagnostic_error') or any(health.get(name,{}).get('state')!='stable'for name in ('phone','system_server')):raise ValueError('complete-stable-platform-observation-unconfirmed')
        result['complete_stable_platform_observation']=True
        result['diagnostic_fields_observed']={key:key in value for key in ('runtime_abi','sim_state','dns','selected_policy','ims_transport','cap_observed','native_sms_ims_supported','native_sms_error')}
        if 'native_sms_ims_supported'not in value and 'native_sms_error'not in value:raise ValueError('native-sms-observation-missing')
        if 'native_sms_ims_supported'in value and not isinstance(value['native_sms_ims_supported'],bool):raise ValueError('native-sms-observation-type')
        if 'native_sms_ims_supported'in value:
            window=value.get('sms_dispatcher_window')
            if window!={'status':'unknown','reason':'rom-not-calibrated'}:raise ValueError('uncalibrated-sms-window-not-unknown')
            result['uncalibrated_software_sms_dispatcher_reported_unknown']=True
        rc,value=entry('RootDiagnostics','0',True)
        if rc or value.get('error')!='SecurityException' or 'sdk' in value:raise ValueError('nonroot-diagnostic-not-refused')
        result['nonroot_diagnostic_refused']=True
        for label,args,nonroot in [('nonroot_action','trial 0 1 7',True),('invalid_mask','trial 0 1 0',False),('invalid_slot','trial 8 1 7',False),('invalid_subscription','trial 0 -1 7',False),('invalid_action','arbitrary 0 1 7',False)]:
            rc,value=entry('ModernAppActions',args,nonroot)
            if rc!=1 or value.get('error')!='SecurityException' or value.get('action_completed'):raise ValueError('action-guard-unconfirmed')
            result[label+'_refused']=True
        rc,value=entry('ModernAppActions','trial 0 1 7')
        if rc!=1 or value.get('error')!='IOException' or value.get('action_completed'):raise ValueError('missing-module-action-not-refused')
        result['missing_module_action_refused']=True
        rc,value=entry('ModernAppActions','rollback 0 1 7')
        if rc!=1 or value.get('error')!='IOException' or value.get('action_completed') or value.get('recovery_pending_owner'):raise ValueError('unrecorded-recovery-not-refused')
        result['missing_owner_recovery_refused']=True
        result['production_state_presence_unchanged']=state_absent()
        if not result['production_state_presence_unchanged']:raise ValueError('read-only-check-created-production-state')
        result.update(status='passed',actual_modern_module_installation_verified=False,modern_carrier_selection_verified=False,carrier_call_sms_verified=False,dual_active_sim_verified=False)
    except Exception as error:result.update(status='failed',error=type(error).__name__)
    return result

a.output=a.output.absolute()
if a.output.resolve()!=a.output:raise SystemExit('report-alias-refused')
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(dict(schema=1,status='started',versions=[]),indent=2)+'\n',encoding='utf-8')
with ThreadPoolExecutor(max_workers=len(a.guest))as pool:versions=list(pool.map(check,a.guest))
result=dict(schema=1,status='passed'if all(value['status']=='passed'for value in versions)else'failed',concurrent_workers=len(versions),versions=versions)
a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
raise SystemExit(0 if result['status']=='passed'else 1)
