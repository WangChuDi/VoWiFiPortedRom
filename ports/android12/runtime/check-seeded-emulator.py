# SPDX-License-Identifier: GPL-2.0
"""Existing-file recovery across isolated app_process stages, owned fake-SIM guests only."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,uuid
B=Path(__file__).resolve().parent
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True);parser.add_argument('--serial',required=True)
    parser.add_argument('--sdk',required=True,type=int,choices=range(31,38))
    parser.add_argument('--avd-name',required=True);parser.add_argument('--output-dir',required=True,type=Path)
    parser.add_argument('--no-push',action='store_true')
    args=parser.parse_args();output=args.output_dir.absolute();record=dict(schema=1,sdk=args.sdk,status='started',stages={})
    remote='/data/local/tmp/codex-modern-runtime-check.zip';nonce=uuid.uuid4().hex
    def save():
        if output.resolve()!=output:raise ValueError('output-alias-refused')
        output.mkdir(parents=True,exist_ok=True);path=output/'seeded-persistence-trial.json'
        if path.is_symlink():raise ValueError('report-alias-refused')
        path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    def adb(*parts,timeout=15,check=True):
        return subprocess.run([args.adb,'-s',args.serial,*parts],capture_output=True,text=True,check=check,timeout=timeout)
    def shell(command):return adb('shell',command).stdout.strip()
    def guard():
        if not args.serial.startswith('emulator-') or args.avd_name!='CodexVoWiFiApi'+str(args.sdk):raise ValueError('owned-emulator-required')
        if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(args.sdk) or shell('getprop ro.boot.qemu.avd_name')!=args.avd_name or shell('id -u')!='0':raise ValueError('named-root-emulator-required')
    expected={
        'prepare':('seeded_disk_layer_loaded','seeded_original_file_snapshotted'),'apply':('persistent_qns_selected','recorded_mask_verified','other_mask_refused'),
        'restore-file':('original_file_restored','loader_not_yet_restored'),
        'arm-file-resume':('simulated_copy_interruption_armed',),
        'recover-and-reload':('interrupted_copy_resumed','selected_loader_reloaded','entire_seeded_config_restored','repeat_restore_safe'),
        'cleanup':('entire_pristine_config_restored','selected_override_absent')}
    def stage(name):
        guard()
        reply=adb('shell','CLASSPATH='+remote+' timeout 110s app_process /system/bin ModernSeededPersistenceTrial '+name+' '+nonce,timeout=120,check=False)
        lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if len(lines)!=1:raise ValueError('fixture-json-unavailable')
        result=json.loads(lines[0]);record['stages'][name]=result;save()
        if reply.returncode or result.get('error') or result.get('schema')!=1 or result.get('sdk')!=args.sdk or result.get('stage')!=name or not all(result.get(key) is True for key in expected[name]):
            raise ValueError('fixture-stage-unconfirmed')
    started=False;failure=None
    try:
        save();guard()
        helper=B.parent/'out/runtime/runtime-check.zip';digest=hashlib.sha256(helper.read_bytes()).hexdigest()
        if not args.no_push:adb('push',str(helper),remote)
        if shell('sha256sum '+remote).split()[0]!=digest:raise ValueError('staged-helper-mismatch')
        record['helper_sha256']=digest
        phone=shell('pidof com.android.phone')
        if not re.fullmatch(r'[0-9]+',phone):raise ValueError('single-phone-process-required')
        started=True
        for name in tuple(expected)[:-1]:stage(name)
    except Exception as error:failure=type(error).__name__
    finally:
        if started:
            try:stage('cleanup')
            except Exception as error:record['cleanup_error']=type(error).__name__;failure=failure or type(error).__name__
            try:
                record['phone_process_unchanged']=shell('pidof com.android.phone')==phone
                if not record['phone_process_unchanged']:failure=failure or 'PhoneProcessRestarted'
            except Exception as error:failure=failure or type(error).__name__
    record.update(status='failed' if failure else 'passed',carrier_call_sms_verified=False,dual_sim_verified=False,
                  actual_os_reboot_or_forced_kill_test=False,simulated_copy_phase_test=True)
    if failure:record['error']=failure
    save();print(json.dumps(record));return 1 if failure else 0
if __name__=='__main__':raise SystemExit(main())
