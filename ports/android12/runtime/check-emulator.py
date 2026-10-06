# SPDX-License-Identifier: GPL-2.0
"""Check an already-installed modern stack on a named, disposable modern emulator.

No signing keys, SDK downloading or package installation. --framework-trial
explicitly opts into the bounded, nonpersistent QNS override/restore test.
"""
from pathlib import Path
import argparse,json,subprocess
B=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb',required=True)
parser.add_argument('--serial',required=True)
parser.add_argument('--avd-name',default='CodexVoWiFiApi31')
parser.add_argument('--sdk',type=int,default=31,choices=range(31,38))
parser.add_argument('--output-dir',type=Path,help='Separate result directory for each concurrent emulator')
parser.add_argument('--no-push',action='store_true',help='Use the helper already staged by the preparation owner')
parser.add_argument('--stdout-only',action='store_true',help='Validate without creating or modifying host files')
parser.add_argument('--framework-trial',action='store_true')
args=parser.parse_args()
out=(args.output_dir or B.parent/'out/runtime').resolve()
helper=B.parent/'out/runtime/runtime-check.zip'
remote='/data/local/tmp/codex-modern-runtime-check.zip'
def save(name,data):
    if not args.stdout_only:(out/name).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def adb(*parts,check=True,timeout=15):
    return subprocess.run([args.adb,'-s',args.serial,*parts],capture_output=True,text=True,check=check,timeout=timeout)
def shell(command):return adb('shell',command).stdout.strip()
def execute(entry):
    reply=adb('shell','CLASSPATH='+remote+' timeout 55s app_process /system/bin '+entry,check=False,timeout=60)
    lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
    if len(lines)!=1:raise SystemExit('runtime-JSON-unavailable')
    result=json.loads(lines[0])
    return reply.returncode,result
def validate():
    if not args.serial.startswith('emulator-'):raise SystemExit('emulator-serial-required')
    if args.avd_name!='CodexVoWiFiApi'+str(args.sdk):raise SystemExit('owned-sdk-AVD-name-required')
    if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(args.sdk) or shell('getprop ro.boot.qemu.avd_name')!=args.avd_name or shell('id -u')!='0':raise SystemExit('named-root-modern-emulator-required')
    if not args.no_push:adb('push',str(helper),remote)
    status,result=execute('ModernRuntimeCheck')
    save('emulator-runtime-check.json',result)
    if status or result.get('error'):raise SystemExit('runtime-entry-failed')
    packages=result.get('packages',{})
    if set(packages)!={'dev.codex.vowifi.iwlan','dev.codex.vowifi.qns','me.phh.ims'}:raise SystemExit('package-inventory-mismatch')
    if not all(item.get('system_app') and item.get('privileged_app') and item.get('permissions') and all(item['permissions'].values()) for item in packages.values()):raise SystemExit('package-permission-incomplete')
    abi=result.get('iwlan_service_abi',{})
    lookups={key:state for key,state in abi.get('checks',{}).items() if not key.startswith('carrier_')}
    if len(lookups)!=20 or not all(state.startswith('visible:') for state in lookups.values()):raise SystemExit('service-library-incomplete')
    bindings=result.get('privileged_explicit_bindings',{})
    if set(bindings)!={'iwlan_data','iwlan_network','qns','ims'} or not all(bindings[name].get('connected') for name in ('iwlan_data','iwlan_network','ims')) or not bindings['qns'].get('requires_platform_caller'):raise SystemExit('explicit-binding-incomplete')
    guard=result.get('privileged_nonroot_guard',{})
    if not guard.get('denied') or not guard.get('caller_permission_granted') or guard.get('tested_uid',0)==0:raise SystemExit('privileged-nonroot-guard-incomplete')
    if not result.get('carrier_binder_read'):raise SystemExit('carrier-Binder-read-incomplete')
    if shell('su 2000 id -u')!='2000':raise SystemExit('shell-negative-caller-unavailable')
    denied=adb('shell','su 2000 content call --uri content://dev.codex.vowifi.iwlan.runtime --method abi --arg '+'a'*32,check=False)
    refusal=denied.stdout+denied.stderr
    if 'snapshot=' in refusal or not any(marker in refusal for marker in ('Permission Denial','SecurityException','not allowed')):raise SystemExit('shell-refusal-unproven')
    report=dict(schema=1,status='passed',sdk=args.sdk,installation_permissions=True,service_library_lookups=20,
                explicit_iwlan_ims_bindings=True,shell_and_privileged_nonroot_refused=True,
                carrier_binder_read=True,carrier_call_sms_verified=False,dual_sim_verified=False)
    if args.framework_trial:
        status,trial=execute('ModernFrameworkTrial')
        save('emulator-framework-trial.json',trial)
        if status or not all(trial.get(name) for name in ('carrier_binder_write_readback','qns_platform_binding','original_qns_restored','original_config_restored')) or trial.get('persisted_override_created'):raise SystemExit('framework-binding-or-restoration-incomplete')
        report['qns_platform_binding_and_full_config_restoration']=True
    return report
try:
    if not args.stdout_only:
        out.mkdir(parents=True,exist_ok=True)
        for name in ('emulator-runtime-check.json','emulator-framework-trial.json'):(out/name).unlink(missing_ok=True)
    save('emulator-validation.json',dict(schema=1,status='started',sdk=args.sdk,
         carrier_call_sms_verified=False,dual_sim_verified=False))
    report=validate()
except (Exception,SystemExit) as failure:
    report=dict(schema=1,status='failed',sdk=args.sdk,error=str(failure.code) if isinstance(failure,SystemExit) else type(failure).__name__,
                carrier_call_sms_verified=False,dual_sim_verified=False)
    save('emulator-validation.json',report)
    print(json.dumps(report,indent=2));raise SystemExit(1)
save('emulator-validation.json',report)
print(json.dumps(report,indent=2))
