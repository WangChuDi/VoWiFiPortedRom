"""Same original fixture; calibrated PM readiness before bounded recovery."""
from pathlib import Path
import json,subprocess,sys,time
W=Path(__file__).resolve().parent;D=W/'api37-recovery-calibrated-original-20261007';assert not(D/'sequence.json').exists()
A='C:/Users/WangChuDi/Documents/Codex/2026-09-27/a-d-b/work/platform-tools/adb.exe';runner=W/'vowifi-portedrom-fork/ports/android12/runtime/recovery-api37/run-probe.py';probe=W/'api37-original-fixture-recovery-child-v5-20261007'
payloads=[('Api31Iwlan','dev.codex.vowifi.iwlan','5ee8d67d5b9a53d98646c7d64cfdae0261a072dbca05d3c570c3c2d602340528'),('Api31Qns','dev.codex.vowifi.qns','798ac76899c8dea99b587f1e0b47fb64708fb238b3323d6f21f5a9bc74d11800'),('Api31Ims','me.phh.ims','4d645baf4c0f7a744e3cefac4190ddc83f977790451cd67c155b15ca0ad1a009')]
report=dict(schema=1,sdk=37,status='waiting-owned-boot',physical_phone_modified=False,new_baseline_created=False,original_journals_deleted=False,actions=[],package_samples=[]);uncertain=False
def save():(D/'sequence.json').write_bytes((json.dumps(report,indent=2)+'\n').encode('utf-8'))
def adb(*parts,timeout=8):return subprocess.run([A,'-s','emulator-5582',*parts],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
def invoke(action,name):
    output=D/(name+'.json');reply=subprocess.run([sys.executable,'-X','utf8',str(runner),'--adb',A,'--probe-dir',str(probe),'--action',action,'--output',str(output)],capture_output=True,timeout=65);value=json.loads(output.read_text());report['actions'].append(dict(action=action,report=name+'.json',status=value['status']));save();print(json.dumps(dict(action=action,status=value['status'])),flush=True)
    if value['status']=='host-observation-timeout':raise TimeoutError('guest-state-unconfirmed')
    return value
try:
    deadline=time.monotonic()+160
    while time.monotonic()<deadline:
        reply=adb('shell','getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop ro.boot.qemu.avd_name; getprop sys.boot_completed')
        if reply.returncode==0 and reply.stdout.split()==['1','37','CodexVoWiFiApi37','1']:break
        time.sleep(2)
    else:raise ValueError('owned-boot-unconfirmed')
    reply=adb('root',timeout=12);assert reply.returncode==0
    for attempt in range(5):
        reply=adb('shell','id -u')
        if reply.returncode==0 and reply.stdout.strip()=='0':break
        time.sleep(1)
    else:raise ValueError('root-unconfirmed')
    for folder,package,digest in payloads:
        reply=adb('shell','sha256sum /system/priv-app/'+folder+'/'+folder+'.apk')
        report.setdefault('retained_file_observations',[]).append(dict(component=folder,command_exit=reply.returncode,expected_digest_observed=reply.returncode==0 and bool(reply.stdout.split())and reply.stdout.split()[0]==digest));save()
    if not all(row['expected_digest_observed']for row in report['retained_file_observations']):raise ValueError('retained-files-unconfirmed')
    deadline=time.monotonic()+75;stable=0
    while time.monotonic()<deadline:
        reply=adb('shell','cmd package list packages');packages=set(reply.stdout.splitlines());calibrated=reply.returncode==0 and {'package:com.android.phone','package:com.android.settings'}.issubset(packages)
        row=dict(command_exit=reply.returncode,known_platform_packages_present=calibrated,all_retained_packages_present=calibrated and all('package:'+p in packages for _,p,_ in payloads));report['package_samples'].append(row);save()
        stable=stable+1 if row['all_retained_packages_present']else 0
        if stable>=2:break
        time.sleep(3)
    else:raise ValueError('calibrated-package-readiness-unconfirmed')
    ready=None
    for attempt in range(3):
        value=invoke('inventory','inventory-'+str(attempt));result=value.get('result',{})
        if value['status']=='passed'and result.get('single_ready_fake_subscription')is True and result.get('two_consecutive_ready_samples')is True:ready=result;break
        if attempt<2:
            reply=adb('shell','cmd settings --user 0 get global boot_count')
            if reply.returncode!=0 or not reply.stdout.strip().isdigit():break
            report['read_only_retry_after_direct_settings_success']=True;save();time.sleep(3)
    if ready is None:raise ValueError('ready-original-owner-unconfirmed')
    fixtures=ready['fixtures'];assert len(fixtures)==1 and fixtures[0]['installation_schema_1']and fixtures[0]['outer_record_present'];owners=fixtures[0]['owners'];assert len(owners)==1 and owners[0]['schema_3']and owners[0]['current_owner']
    for action in ('selection','installation','outer','audit'):
        value=invoke(action,action);assert value['status']=='passed'and value['result']['status']=='passed'
    final=invoke('inventory','inventory-final');assert final['status']=='passed';fixtures=final['result']['fixtures'];assert len(fixtures)==1 and fixtures[0]['installation_phase']=='RESTORED'and all(row['phase']=='RESTORED'and not row['mode_owned']for row in fixtures[0]['owners'])
    report.update(status='restored-and-audited',original_fixture_cleanup_confirmed=True,full_api37_lifecycle_verified=False)
except (subprocess.TimeoutExpired,TimeoutError):uncertain=True;report.update(status='observation-timeout',guest_terminal_verified=False,original_fixture_cleanup_confirmed=False)
except Exception as e:report.update(status='failed',error=type(e).__name__,reason=str(e)if str(e)in {'owned-boot-unconfirmed','root-unconfirmed','retained-files-unconfirmed','calibrated-package-readiness-unconfirmed','ready-original-owner-unconfirmed'}else'unclassified',original_fixture_cleanup_confirmed=False)
finally:
    save()
    if not uncertain:(D/'finish.request').write_bytes(b'finished-existing-record-observation\n')
    print(json.dumps(report),flush=True)
raise SystemExit(report['status']!='restored-and-audited')
