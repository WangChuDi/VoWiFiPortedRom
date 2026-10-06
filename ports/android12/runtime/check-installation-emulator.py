# SPDX-License-Identifier: GPL-2.0
"""Concurrent, disposable fake-SIM permission recovery trials, not real phone installs."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse,hashlib,json,re,stat,subprocess,uuid,zipfile
B=Path(__file__).resolve().parent
STAGES={
    'coordination':('borrowed_carrier_close_keeps_global_lock','borrowed_installation_close_keeps_global_lock',
        'closed_carrier_handle_refused','closed_installation_handle_refused',
        'cross_thread_lock_use_refused','shared_mode_no_reset_cycle_preserves_presence_and_value'),
    'seed':('missing_runtime_grant_and_exemption_seeded','denied_ipsec_seeded'),
    'prepare':('installed_permissions_prepared',),
    'resume':('new_process_retention_verified',),
    'external-policy':('external_policy_preserved_on_restore_refusal',),
    'restore':('original_seeded_policy_restored',),
    'arm-restore-resume':('simulated_restore_handoff_armed',),
    'recover':('restore_resumed_and_repeated',),
    'cleanup':('entire_outer_permission_observation_restored',),
}

def trial(adb,sdk,serial,module_zip):
    record=dict(schema=1,sdk=sdk,status='started',stages={})
    remote='/data/local/tmp/codex-modern-runtime-check.zip';nonce=uuid.uuid4().hex
    root='/data/local/tmp/codex-modern-installation-tests/'+nonce
    def command(*parts,check=True,timeout=35):
        return subprocess.run([adb,'-s',serial,*parts],capture_output=True,text=True,timeout=timeout,check=check)
    def shell(text):return command('shell',text).stdout.strip()
    def guard():
        if (shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(sdk) or
            shell('getprop ro.boot.qemu.avd_name')!='CodexVoWiFiApi'+str(sdk) or shell('id -u')!='0'):
            raise ValueError('named-root-emulator-required')
    def stage(name):
        guard()
        entry=('ModernCoordinationEmulatorCheck '+nonce) if name=='coordination' else ('ModernInstallationEmulatorTrial '+name+' '+nonce)
        reply=command('shell','CLASSPATH='+remote+' timeout 35s app_process /system/bin '+entry,check=False,timeout=45)
        lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if len(lines)!=1:raise ValueError('fixture-json-unavailable')
        observation=json.loads(lines[0]);record['stages'][name]=observation
        if (reply.returncode or observation.get('error') or observation.get('schema')!=1 or observation.get('sdk')!=sdk or
            observation.get('stage')!=name or not all(observation.get(key) is True for key in STAGES[name])):
            raise ValueError('fixture-stage-unconfirmed')
    failure=None;started=False
    try:
        guard();helper=B.parent/'out/runtime/runtime-check.zip'
        digest=hashlib.sha256(helper.read_bytes()).hexdigest();record['helper_sha256']=digest
        command('push',str(helper),remote)
        if shell('sha256sum '+remote).split()[0]!=digest:raise ValueError('helper-digest-mismatch')
        # Verify all staged payload bytes before entering the Java fixture. No
        # /system or production Magisk directory is modified by this runner.
        shell('umask 077; mkdir -p '+root+'/module')
        archive=root+'/payload.zip';command('push',str(module_zip),archive)
        shell('unzip -q '+archive+' -d '+root+'/module')
        shell('cd '+root+'/module && sha256sum -c payload.sha256 >/dev/null')
        record['module_sha256']=hashlib.sha256(module_zip.read_bytes()).hexdigest()
        # New production entry-point guards, not a non-VOXI production bypass.
        production='/data/adb/codex_vowifi_stack_modern/installation'
        absent_module=command('shell','test -e /data/adb/modules/codex_vowifi_stack_modern',check=False).returncode!=0
        absent_state=command('shell','test -e '+production+'/baseline.properties',check=False).returncode!=0
        if not absent_module or not absent_state:raise ValueError('unused-production-installation-profile-required')
        before=command('shell','test -e '+production,check=False).returncode==0
        for name,text,error in [
            ('nonroot_installation_refused','su 2000 env CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernInstallationController prepare','SecurityException'),
            ('missing_module_installation_refused','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernInstallationController prepare','IOException')]:
            reply=command('shell',text,check=False)
            lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
            if reply.returncode!=1 or len(lines)!=1 or json.loads(lines[0]).get('error')!=error:raise ValueError('production-installation-guard-unconfirmed')
            record[name]=True
        record['refused_installation_state_presence_unchanged']=(command('shell','test -e '+production,check=False).returncode==0)==before
        if not record['refused_installation_state_presence_unchanged']:raise ValueError('refused-installation-created-state')
        for script in sorted((B.parent/'module').glob('*.sh')):
            subprocess.run([adb,'-s',serial,'shell','sh','-n'],input=script.read_text(encoding='utf-8'),capture_output=True,text=True,timeout=15,check=True)
        record['module_shell_syntax_checked']=True
        phone=shell('pidof com.android.phone')
        if not re.fullmatch(r'[0-9]+',phone):raise ValueError('single-phone-process-required')
        started=True
        for name in list(STAGES)[:-1]:stage(name)
    except Exception as error:failure=type(error).__name__
    finally:
        if started:
            try:stage('cleanup')
            except Exception as error:record['cleanup_error']=type(error).__name__;failure=failure or type(error).__name__
            try:
                record['phone_process_unchanged']=shell('pidof com.android.phone')==phone
                if not record['phone_process_unchanged']:failure=failure or 'PhoneProcessRestarted'
            except Exception as error:failure=failure or type(error).__name__
    if not failure:
        try:
            observation=command('shell','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernIwlanModeCheck').stdout
            lines=[line for line in observation.splitlines() if line.startswith('{') and line.endswith('}')]
            if len(lines)!=1:raise ValueError('mode-observation-unavailable')
            mode=json.loads(lines[0])
            if mode.get('status')!='observed' or mode.get('sdk')!=sdk or mode.get('read_only') is not True:raise ValueError('mode-observation-unconfirmed')
            record['iwlan_mode_observation']=mode
        except Exception as error:failure=type(error).__name__
    record.update(status='failed' if failure else 'passed',magisk_mount_verified=False,
                  carrier_call_sms_verified=False,dual_active_sim_verified=False,actual_os_reboot_or_forced_kill_test=False)
    if failure:record['error']=failure
    return record

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True);parser.add_argument('--guest',required=True,action='append')
    parser.add_argument('--module',required=True,type=Path);parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args();guests=[]
    for guest in args.guest:
        match=re.fullmatch(r'(3[1-7]):(emulator-[0-9]+)',guest)
        if not match:parser.error('explicit-emulator-profile-required')
        guests.append((int(match[1]),match[2]))
    if len(guests)>10 or len({g[0] for g in guests})!=len(guests) or len({g[1] for g in guests})!=len(guests):parser.error('distinct-version-and-serial-required')
    output=args.output.absolute();module=args.module.absolute()
    if output.resolve()!=output or output.exists() or module.resolve()!=module or not module.is_file():parser.error('fresh-canonical-report-and-module-required')
    # Restrict zip extraction to the exact builder's payload and independently
    # verify its digest inventory. Never extract attacker-selected path entries.
    with zipfile.ZipFile(module) as archive:
        names=archive.namelist()
        if any(stat.S_ISLNK(entry.external_attr>>16) for entry in archive.infolist()):parser.error('module-payload-link-refused')
        expected=set(['controller.zip','installation.properties','module-profile.json','payload.sha256',
            'THIRD_PARTY.md','LICENSE-phhusson-ims','LICENSE-upstream','module.prop','customize.sh','control.sh','service.sh','uninstall.sh','recovery-boot.sh',
            'system/etc/permissions/privapp-permissions-codex-vowifi-modern.xml']+
            ['system/priv-app/'+d+'/'+d+'.apk' for d in ['Api31Iwlan','Api31Qns','Api31Ims']])
        if len(set(names))!=len(names) or set(names)!=expected:parser.error('module-payload-inventory-refused')
        inventory=archive.read('payload.sha256').decode().splitlines()
        recorded={}
        for line in inventory:
            match=re.fullmatch(r'([0-9a-f]{64})  (.+)',line)
            if not match or match[2] in recorded:parser.error('module-digest-inventory-refused')
            recorded[match[2]]=match[1]
        if set(recorded)!=expected-{'payload.sha256'} or any(hashlib.sha256(archive.read(name)).hexdigest()!=digest for name,digest in recorded.items()):parser.error('module-digest-mismatch')
        if archive.read('controller.zip')!=(B.parent/'out/runtime/runtime-check.zip').read_bytes():parser.error('module-helper-mismatch')
    output.parent.mkdir(parents=True,exist_ok=True)
    record=dict(schema=1,status='started',concurrent_device_workers=len(guests),versions=[])
    output.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    with ThreadPoolExecutor(max_workers=len(guests)) as pool:
        record['versions']=list(pool.map(lambda guest:trial(args.adb,*guest,module),guests))
    record['status']='passed' if all(v['status']=='passed' for v in record['versions']) else 'failed'
    output.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(record))
    return 0 if record['status']=='passed' else 1
if __name__=='__main__':raise SystemExit(main())
