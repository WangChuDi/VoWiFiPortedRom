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
    'selection-trial':('full_mask_live_disk_and_lease_verified',),
    'selection-reopen':('reopened_retention_and_renewal_verified','stale_token_refused_without_change'),
    'selection-partial-lease':('partial_lease_publication_resumed',),
    'selection-foreign-lease':('foreign_lease_renew_and_restore_refused',),
    'selection-role-handoff':('pre_observation_role_handoff_armed',),
    'selection-restore':('original_config_lease_and_mode_restored','repeat_restore_verified'),
    'selection-repeat':('new_qns_only_cycle_preserves_previous_snapshot','previous_cycle_token_refused'),
    'selection-expire':('expired_trial_restores_entire_owner',),
    'selection-cleanup':('selection_outer_cleanup_completed',),
    'external-policy':('external_policy_preserved_on_restore_refusal',),
    'restore':('original_seeded_policy_restored',),
    'arm-restore-resume':('simulated_restore_handoff_armed',),
    'recover':('restore_resumed_and_repeated',),
    'supervisor-publish':('independent_helper_script_and_hook_published','arbitrary_failure_messages_not_exported'),
    'supervisor-publication-handoff':('uncommitted_generation_leaves_previous_recovery_usable','publication_reopen_preserves_selected_generation'),
    'supervisor-trial':('fresh_installation_cycle_archives_original','supervised_full_mask_trial_ready'),
    'supervisor-renew':('inventory_renews_trial_owner',),
    'supervisor-upgrade-refusal':('pending_owner_prevents_recovery_helper_replacement',),
    'supervisor-payload-loss':('missing_module_apk_does_not_block_carrier_recovery','installed_app_original_policy_restored_without_payload'),
    'supervisor-reprepare':('second_installation_cycle_reprepared_after_recovery',),
    'supervisor-inventory':('missing_owner_record_refused_before_any_mutation','empty_archived_owner_directory_skipped'),
    'supervisor-expire':('inventory_restores_expired_trial',),
    'supervisor-persistent':('inventory_renews_retained_owner',),
    'supervisor-role-failure':('carrier_restores_before_role_identity_refusal','pending_owner_blocks_installation_restore'),
    'supervisor-disable':('disabled_module_restores_owner_before_permissions',),
    'supervisor-repeat':('repeat_recovery_keeps_original_installation_policy',),
    'supervisor-generation':('older_supervisor_stops_before_changing_new_generation',),
    'supervisor-cleanup':('supervisor_fixture_cleanup_completed',),
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
        entry=('ModernCoordinationEmulatorCheck '+nonce) if name=='coordination' else ((('ModernSupervisorEmulatorTrial' if name.startswith('supervisor-') else 'ModernSelectionEmulatorTrial' if name.startswith('selection-') else 'ModernInstallationEmulatorTrial')+' '+name+' '+nonce))
        reply=command('shell','CLASSPATH='+remote+' timeout 75s app_process /system/bin '+entry,check=False,timeout=85)
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
        coordination='/data/adb/codex_vowifi_stack_modern/coordination'
        before_coordination=command('shell','test -e '+coordination,check=False).returncode==0
        for name,text,error in [
            ('nonroot_selection_refused','su 2000 env CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernSelectionController trial 0 1 7','SecurityException'),
            ('invalid_selection_mask_refused','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernSelectionController trial 0 1 8','IllegalArgumentException'),
            ('non_voxi_selection_refused','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernSelectionController owner-check 0 1','SecurityException')]:
            reply=command('shell',text,check=False)
            lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
            if reply.returncode!=1 or len(lines)!=1 or json.loads(lines[0]).get('error')!=error:raise ValueError('production-selection-guard-unconfirmed')
            record[name]=True
        alive_reply=command('shell','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernSelectionSupervisor alive',check=False)
        alive_lines=[line for line in alive_reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
        if alive_reply.returncode or len(alive_lines)!=1 or json.loads(alive_lines[0]).get('supervisor_alive') is not False:raise ValueError('supervisor-alive-probe-unconfirmed')
        record['supervisor_probe_does_not_create_state']=(command('shell','test -e '+coordination,check=False).returncode==0)==before_coordination
        if not record['supervisor_probe_does_not_create_state']:raise ValueError('refused-selection-created-state')
        for script in sorted((B.parent/'module').glob('*.sh')):
            subprocess.run([adb,'-s',serial,'shell','sh','-n'],input=script.read_text(encoding='utf-8'),capture_output=True,text=True,timeout=15,check=True)
        record['module_shell_syntax_checked']=True
        phone=shell('pidof com.android.phone')
        if not re.fullmatch(r'[0-9]+',phone):raise ValueError('single-phone-process-required')
        started=True
        for name in STAGES:
            if name not in ('supervisor-cleanup','selection-cleanup','cleanup'):stage(name)
    except Exception as error:
        failure=type(error).__name__
        if started:
            try:
                reply=command('shell','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernInstallationEmulatorTrial audit '+nonce,check=False)
                lines=[line for line in reply.stdout.splitlines() if line.startswith('{') and line.endswith('}')]
                if len(lines)==1:record['pre_cleanup_policy_audit']=json.loads(lines[0])
            except Exception:record['pre_cleanup_policy_audit_unavailable']=True
    finally:
        if started:
            try:stage('supervisor-cleanup')
            except Exception as error:record['supervisor_cleanup_error']=type(error).__name__;failure=failure or type(error).__name__
            try:stage('selection-cleanup')
            except Exception as error:record['selection_cleanup_error']=type(error).__name__;failure=failure or type(error).__name__
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
