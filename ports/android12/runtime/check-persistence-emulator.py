# SPDX-License-Identifier: GPL-2.0
"""Selected-subscription preflight; opt in to a disposable QNS persistence trial."""
from pathlib import Path
import argparse,hashlib,json,subprocess

B=Path(__file__).resolve().parent
def validate_result(result,sdk,persistent):
    if result.get('schema')!=1 or result.get('sdk')!=sdk or result.get('error'):
        raise ValueError('persistence-result-profile-mismatch')
    required=('production_snapshot','selected_live_and_native_stream_file',
              'reopened_transaction_restored','entire_original_config_restored',
              'selected_override_absent','restoration_retry_safe') if persistent else (
              'selected_target_confirmed','platform_cache_present','platform_cache_stream_read','transient_override_empty')
    if not all(result.get(key) is True for key in required):
        raise ValueError('persistence-result-incomplete')
    if persistent:
        if result.get('mask')!=2 or result.get('persistent') is not True:
            raise ValueError('persistence-trial-scope-mismatch')
    elif result.get('selected_override_present') is not False:
        raise ValueError('fresh-emulator-baseline-required')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--sdk',type=int,required=True,choices=range(31,38))
    parser.add_argument('--avd-name',required=True)
    parser.add_argument('--output-dir',type=Path)
    parser.add_argument('--no-push',action='store_true')
    parser.add_argument('--stdout-only',action='store_true')
    parser.add_argument('--persistent-trial',action='store_true',help='Change and restore QNS only in the owned emulator')
    args=parser.parse_args()
    helper=B.parent/'out/runtime/runtime-check.zip'
    remote='/data/local/tmp/codex-modern-runtime-check.zip'
    output=(args.output_dir or B.parent/'out/runtime').absolute()
    filename='persistent-framework-trial.json' if args.persistent_trial else 'persistence-preflight.json'
    def save(result):
        if not args.stdout_only:
            if output.resolve()!=output:raise ValueError('output-alias-refused')
            output.mkdir(parents=True,exist_ok=True)
            path=output/filename
            if path.is_symlink():raise ValueError('report-alias-refused')
            path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    def adb(*parts,timeout=15,check=True):
        return subprocess.run([args.adb,'-s',args.serial,*parts],capture_output=True,text=True,timeout=timeout,check=check)
    def shell(command):return adb('shell',command).stdout.strip()
    digest=None
    try:
        save(dict(schema=1,sdk=args.sdk,status='started'))
        if not args.serial.startswith('emulator-') or args.avd_name!='CodexVoWiFiApi'+str(args.sdk):
            raise ValueError('owned-emulator-required')
        if shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(args.sdk) or shell('getprop ro.boot.qemu.avd_name')!=args.avd_name or shell('id -u')!='0':
            raise ValueError('named-root-emulator-required')
        digest=hashlib.sha256(helper.read_bytes()).hexdigest()
        if not args.no_push:adb('push',str(helper),remote)
        if shell('sha256sum '+remote).split()[0]!=digest:raise ValueError('staged-helper-mismatch')
        entry='ModernPersistentFrameworkTrial' if args.persistent_trial else 'ModernPersistenceCheck 0 1'
        bound=120 if args.persistent_trial else 35
        reply=adb('shell','CLASSPATH='+remote+' timeout '+str(bound)+'s app_process /system/bin '+entry,timeout=bound+10,check=False)
        lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if len(lines)!=1:raise ValueError('persistence-json-unavailable')
        result=json.loads(lines[0])
        if reply.returncode:raise ValueError('persistence-entry-failed')
        validate_result(result,args.sdk,args.persistent_trial)
        result.update(status='passed',helper_sha256=digest,process_exit=reply.returncode)
    except Exception as failure:
        result=dict(schema=1,sdk=args.sdk,status='failed',error=type(failure).__name__,helper_sha256=digest)
        save(result);print(json.dumps(result));return 1
    save(result);print(json.dumps(result));return 0

if __name__=='__main__':raise SystemExit(main())
