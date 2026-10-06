# SPDX-License-Identifier: GPL-2.0
"""Read-only guard checks for the production CLI on prepared fake-SIM emulators."""
from pathlib import Path
import argparse,hashlib,json,subprocess
B=Path(__file__).resolve().parent
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True);parser.add_argument('--serial',required=True)
    parser.add_argument('--sdk',type=int,required=True,choices=range(31,38));parser.add_argument('--avd-name',required=True)
    parser.add_argument('--output-dir',type=Path);parser.add_argument('--stdout-only',action='store_true')
    args=parser.parse_args();record=dict(schema=1,sdk=args.sdk,status='started')
    def save():
        if args.stdout_only:return
        if args.output_dir is None:raise ValueError('explicit-output-required')
        output=args.output_dir.absolute()
        if output.resolve()!=output:raise ValueError('output-alias-refused')
        output.mkdir(parents=True,exist_ok=True);path=output/'modern-controller-guard.json'
        if path.is_symlink():raise ValueError('report-alias-refused')
        path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    def adb(command,check=True):return subprocess.run([args.adb,'-s',args.serial,'shell',command],capture_output=True,text=True,timeout=35,check=check)
    def shell(command):return adb(command).stdout.strip()
    try:
        save()
        if not args.serial.startswith('emulator-') or args.avd_name!='CodexVoWiFiApi'+str(args.sdk):raise ValueError('owned-emulator-required')
        if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(args.sdk) or shell('getprop ro.boot.qemu.avd_name')!=args.avd_name or shell('id -u')!='0':raise ValueError('named-root-emulator-required')
        remote='/data/local/tmp/codex-modern-runtime-check.zip'
        digest=hashlib.sha256((B.parent/'out/runtime/runtime-check.zip').read_bytes()).hexdigest()
        if shell('sha256sum '+remote).split()[0]!=digest:raise ValueError('staged-helper-mismatch')
        record['helper_sha256']=digest
        # This suite is only valid when the preflight confirms no selected persisted
        # override and empty RAM after the separately owned fake-SIM trial.
        reply=adb('CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernPersistenceCheck 0 1',False)
        lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if reply.returncode or len(lines)!=1:raise ValueError('clean-test-baseline-unconfirmed')
        baseline=json.loads(lines[0])
        if baseline.get('selected_override_present') is not False or baseline.get('transient_override_empty') is not True:raise ValueError('dirty-test-baseline')
        root='/data/adb/codex_vowifi_stack_modern/transactions'
        before=adb('test -e '+root,False).returncode==0
        tests=(('nonroot_refused','su 2000 env CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernCarrierController owner-check 0 1','SecurityException'),
               ('non_voxi_owner_refused','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernCarrierController owner-check 0 1','SecurityException'),
               ('invalid_mask_refused','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernCarrierController apply 0 1 8','IllegalArgumentException'))
        for name,command,error in tests:
            reply=adb(command,False);lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
            if reply.returncode!=1 or len(lines)!=1 or json.loads(lines[0]).get('error')!=error:raise ValueError('production-guard-unconfirmed')
            record[name]=True
        record['transaction_inventory_presence_unchanged']=(adb('test -e '+root,False).returncode==0)==before
        if not record['transaction_inventory_presence_unchanged']:raise ValueError('rejected-action-created-transaction')
        record.update(status='passed',production_voxi_action_verified=False,production_installer_supervisor_verified=False,carrier_call_sms_verified=False,dual_sim_verified=False)
    except Exception as error:record.update(status='failed',error=type(error).__name__)
    save();print(json.dumps(record));return 0 if record['status']=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
