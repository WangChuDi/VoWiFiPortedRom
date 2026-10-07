"""Existing stock AVD: matched bounded fixed/writable-system host control, no engine."""
from pathlib import Path
import json,os,re,subprocess,time
W=Path(__file__).resolve().parent;SDK=W/'android-runtime-sdk';D=W/'api37-matched-stock-controls-20261007';NAME='CodexVoWiFiApi37StockControl';SERIAL='emulator-5590';AVD=SDK/'avd'/NAME
A='C:/Users/WangChuDi/Documents/Codex/2026-09-27/a-d-b/work/platform-tools/adb.exe';P='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
assert not D.exists()and AVD.is_dir()and AVD.resolve().is_relative_to((SDK/'avd').resolve());D.mkdir()
def inventory():
    script="$ErrorActionPreference='Stop'; $rows=@(Get-CimInstance Win32_Process -Filter \"Name LIKE 'qemu%' OR Name = 'emulator.exe'\" | Select-Object ProcessId,ParentProcessId,ExecutablePath,CreationDate,CommandLine,PrivatePageCount); $sys=Get-CimInstance Win32_OperatingSystem; [pscustomobject]@{processes=$rows;disk_free_bytes=(Get-PSDrive C).Free;free_physical_bytes=[int64]$sys.FreePhysicalMemory*1024} | ConvertTo-Json -Depth 4 -Compress"
    r=subprocess.run([P,'-NoProfile','-Command',script],capture_output=True,text=True,encoding='utf-8',timeout=20,check=True);return json.loads(r.stdout)
def shell(text,seconds=5):return subprocess.run([A,'-s',SERIAL,'shell',text],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=seconds)
def save(path,value):path.write_bytes((json.dumps(value,indent=2)+'\n').encode('utf-8'))
env=dict(os.environ,JAVA_HOME='D:/ProgramFiles/DBeaver/jre',ANDROID_AVD_HOME=str(SDK/'avd'),ANDROID_USER_HOME=str(SDK/'user'),ANDROID_SDK_ROOT=str(SDK),ANDROID_HOME=str(SDK))
def trial(writable,window):
    folder=D/('writable'if writable else'fixed');folder.mkdir();initial=inventory()
    assert not initial['processes']and initial['disk_free_bytes']>=9*1024**3 and initial['free_physical_bytes']>=8*1024**3
    report=dict(schema=1,sdk=37,status='starting',existing_stock_userdata_preserved=True,original_avd_modified=False,physical_phone_modified=False,wipe_requested=False,snapshot_load_save_requested=False,engine_installed=False,carrier_policy_changed=False,system_writable_requested=writable,quickboot_file_backed_requested=False,gpu_backend='swiftshader',vulkan_disabled=True,configured_guest_ram_mib=4096,requested_post_boot_window_seconds=window,samples=[],guest_samples=[])
    args=[str(SDK/'emulator/emulator.exe'),'-avd',NAME,'-port','5590','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-memory','4096','-cores','2','-gpu','swiftshader','-feature','-Vulkan','-feature','-QuickbootFileBacked']
    if writable:args.append('-writable-system')
    with(folder/'emulator-private.log').open('wb')as log:launcher=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT,env=env,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    identity=None;boot_at=None;started=time.monotonic();rooted=False;last_guest=0;observations=[]
    def stop():
        if identity is None:
            if launcher.poll()is None:raise RuntimeError('unconfirmed-owned-stop-refused')
            return
        rows=[row for row in inventory()['processes']if row['ProcessId']==identity['ProcessId']]
        if rows:
            row=rows[0];assert all(row[k]==identity[k]for k in identity)and row['ParentProcessId']==launcher.pid and NAME in row['CommandLine']and Path(row['ExecutablePath']).resolve().is_relative_to((SDK/'emulator').resolve())
            subprocess.run([P,'-NoProfile','-Command',"$ErrorActionPreference='Stop'; Stop-Process -Id "+str(row['ProcessId'])+' -Force'],capture_output=True,timeout=20,check=True)
            for _ in range(15):
                if not any(row['ProcessId']==identity['ProcessId']for row in inventory()['processes']):break
                time.sleep(1)
            assert not any(row['ProcessId']==identity['ProcessId']for row in inventory()['processes'])
        if launcher.poll()is None:launcher.terminate();launcher.wait(timeout=15)
    try:
        while time.monotonic()-started<600:
            current=inventory();rows=[row for row in current['processes']if row['ParentProcessId']==launcher.pid and NAME in str(row['CommandLine'])and Path(row['ExecutablePath']).name=='qemu-system-x86_64-headless.exe']
            if len(rows)>1:raise RuntimeError('unique-owned-guest-required')
            if rows:
                row=rows[0]
                if identity is None:identity={k:row[k]for k in ('ProcessId','ParentProcessId','ExecutablePath','CreationDate')}
                else:assert all(row[k]==identity[k]for k in identity)
                report['samples'].append(dict(seconds=round(time.monotonic()-started,1),private_bytes=row['PrivatePageCount'],disk_free_bytes=current['disk_free_bytes'],free_physical_bytes=current['free_physical_bytes']))
                if row['PrivatePageCount']>12*1024**3 or current['disk_free_bytes']<3*1024**3 or current['free_physical_bytes']<2*1024**3:
                    report.update(status='resource-floor-reached',resource_reason='private-memory-limit'if row['PrivatePageCount']>12*1024**3 else'disk-floor'if current['disk_free_bytes']<3*1024**3 else'physical-memory-floor');break
                try:
                    if boot_at is None:
                        r=shell('getprop sys.boot_completed; getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop ro.boot.qemu.avd_name')
                        if r.returncode==0 and r.stdout.split()==['1','1','37',NAME]:boot_at=time.monotonic();report.update(status='booted',boot_observed=True);print(json.dumps(dict(status='stock-control-booted',writable_system=writable)),flush=True)
                    elif not rooted:
                        subprocess.run([A,'-s',SERIAL,'root'],capture_output=True,timeout=10);r=shell('id -u');rooted=r.returncode==0 and r.stdout.strip()=='0'
                        if rooted:
                            r=shell('cmd package list packages',10);packages=set(re.findall(r'(?m)^package:([a-zA-Z0-9_.]+)\s*$',r.stdout))
                            report['package_inventory_calibrated']=r.returncode==0 and {'com.android.phone','com.android.settings'}.issubset(packages);report['three_replacement_packages_absent']=report['package_inventory_calibrated']and not {'dev.codex.vowifi.iwlan','dev.codex.vowifi.qns','me.phh.ims'}.intersection(packages)
                            assert report['three_replacement_packages_absent']
                    elif time.monotonic()-last_guest>=15:
                        private={}
                        for name in ('system_server','com.android.phone'):
                            pid=shell('pidof '+name).stdout.strip();private[name]=None
                            if pid.isdigit():
                                ticks=shell("awk '{print $22}' /proc/"+pid+'/stat').stdout.strip()
                                if ticks.isdigit():private[name]=(pid,ticks)
                        observations.append(private);r=shell('cmd settings --user 0 get global boot_count');sample=dict(seconds_after_boot=round(time.monotonic()-boot_at,1),settings_exit=r.returncode,settings_integer_observed=r.stdout.strip().isdigit(),settings_not_found_marker='Can\'t find service: settings'in (r.stdout+r.stderr),system_server_identity_observed=private['system_server']is not None,phone_identity_observed=private['com.android.phone']is not None)
                        report['guest_samples'].append(sample);last_guest=time.monotonic()
                except subprocess.TimeoutExpired:report['observation_timeouts']=report.get('observation_timeouts',0)+1
                if boot_at is not None and time.monotonic()-boot_at>=window:report['status']='control-window-completed';break
            elif launcher.poll()is not None:report['status']='launcher-terminal';break
            save(folder/'report.json',report);time.sleep(4)
        else:report['status']='bounded-window-deadline'
    except Exception as e:report.update(status='observer-failed',error=type(e).__name__)
    stop();report.update(owned_processes_terminal=not any(NAME in str(row['CommandLine'])for row in inventory()['processes']),stock_ram_file_recreated=(AVD/'snapshots/default_boot/ram.img').exists(),boot_observed=boot_at is not None)
    for name,label in [('system_server','system_server'),('com.android.phone','phone')]:report[label+'_identity_continuity_observed']=bool(observations)and observations[0][name]is not None and all(obs[name]==observations[0][name]for obs in observations)
    save(folder/'report.json',report);print(json.dumps({k:v for k,v in report.items()if k not in ('samples','guest_samples')}),flush=True)
    return report
first=trial(False,120);assert first['status']=='control-window-completed'and first['owned_processes_terminal']and not first['stock_ram_file_recreated']
second=trial(True,360);summary=dict(schema=1,sdk=37,status='passed'if second['status']=='control-window-completed'else'partial',same_existing_stock_avd=True,all_owned_guests_terminal=first['owned_processes_terminal']and second['owned_processes_terminal'],original_avd_modified=False,physical_phone_modified=False,fixed_settings_all_successful=bool(first['guest_samples'])and all(row['settings_exit']==0 and row['settings_integer_observed']for row in first['guest_samples']),writable_settings_all_successful=bool(second['guest_samples'])and all(row['settings_exit']==0 and row['settings_integer_observed']for row in second['guest_samples']),writable_peak_private_bytes=max(row['private_bytes']for row in second['samples']),writable_last_private_bytes=second['samples'][-1]['private_bytes'],original_fixture_cleanup_confirmed=False,full_api37_lifecycle_verified=False)
save(D/'summary.json',summary);print(json.dumps(summary),flush=True);raise SystemExit(summary['status']!='passed')
