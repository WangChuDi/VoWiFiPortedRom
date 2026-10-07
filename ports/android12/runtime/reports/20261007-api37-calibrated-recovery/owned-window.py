"""Bounded owned original-AVD recovery window; no wipe/rebaseline/payload staging."""
from pathlib import Path
import argparse, json, os, subprocess, time
parser=argparse.ArgumentParser();parser.add_argument('--backend',choices=('lavapipe','swiftshader'),default='lavapipe');parser.add_argument('--disable-vulkan',action='store_true');parser.add_argument('--fixed-system',action='store_true');parser.add_argument('--disable-file-backed',action='store_true');parser.add_argument('--window-seconds',type=int,default=1800);parser.add_argument('--report-name');args=parser.parse_args()
assert 60<=args.window_seconds<=1800
if args.disable_file_backed:assert args.backend=='swiftshader'and args.disable_vulkan and not args.fixed_system
W=Path(__file__).resolve().parent; SDK=W/'android-runtime-sdk'; D=W/('api37-recovery-4g-no-file-backed-20261007' if args.disable_file_backed else 'api37-recovery-4g-fixed-system-20261007' if args.fixed_system else 'api37-recovery-4g-no-vulkan-20261007' if args.disable_vulkan else 'api37-recovery-4g-20261007' if args.backend=='lavapipe' else 'api37-recovery-4g-swiftshader-20261007')
if args.report_name:
    import re
    assert args.disable_file_backed and re.fullmatch(r'api37-recovery-[a-z0-9-]+',args.report_name);D=W/args.report_name
P=r'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
A=r'C:/Users/WangChuDi/Documents/Codex/2026-09-27/a-d-b/work/platform-tools/adb.exe'
def inventory():
    source="$ErrorActionPreference='Stop'; $rows=@(Get-CimInstance Win32_Process -Filter \"Name LIKE 'qemu%' OR Name = 'emulator.exe'\" | Where-Object { $_.CommandLine -match 'CodexVoWiFiApi37' } | Select-Object ProcessId,ParentProcessId,ExecutablePath,CreationDate,PrivatePageCount); $sys=Get-CimInstance Win32_OperatingSystem; [pscustomobject]@{processes=$rows;disk_free_bytes=(Get-PSDrive C).Free;free_physical_bytes=[int64]$sys.FreePhysicalMemory*1024} | ConvertTo-Json -Depth 4 -Compress"
    r=subprocess.run([P,'-NoProfile','-Command',source],capture_output=True,text=True,timeout=20,check=True)
    return json.loads(r.stdout)
assert D.resolve()==D and not D.exists()
initial=inventory(); assert not initial['processes'] and initial['disk_free_bytes']>=(5 if args.fixed_system else 9)*1024**3 and initial['free_physical_bytes']>=8*1024**3
D.mkdir(); report=dict(schema=1,sdk=37,status='starting',gpu_backend=args.backend,vulkan_disabled=args.disable_vulkan,quickboot_file_backed_requested=not args.disable_file_backed,window_seconds=args.window_seconds,system_writable_requested=not args.fixed_system,userdata_writable=True,configured_guest_memory_mib=4096,original_avd_preserved=True,wipe_requested=False,snapshot_load_save_requested=False,physical_phone_modified=False,payload_staging_requested=False,samples=[])
def save(): (D/'monitor.json').write_bytes((json.dumps(report,indent=2)+'\n').encode('utf-8'))
env=dict(os.environ,JAVA_HOME=r'D:/ProgramFiles/DBeaver/jre',ANDROID_AVD_HOME=str(SDK/'avd'),ANDROID_USER_HOME=str(SDK/'user'),ANDROID_SDK_ROOT=str(SDK),ANDROID_HOME=str(SDK))
arguments=[str(SDK/'emulator/emulator.exe'),'-avd','CodexVoWiFiApi37','-port','5582','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-memory','4096','-cores','2','-gpu',args.backend]
if not args.fixed_system: arguments+=['-writable-system']
if args.disable_vulkan: arguments+=['-feature','-Vulkan']
if args.disable_file_backed: arguments+=['-feature','-QuickbootFileBacked']
save()
with (D/'emulator-private.log').open('wb') as log:
    launcher=subprocess.Popen(arguments,stdout=log,stderr=subprocess.STDOUT,env=env,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
identity=None; started=time.monotonic(); booted=False
def stop_owned():
    global identity
    if identity is None:
        if launcher.poll() is None: raise RuntimeError('unconfirmed-process-stop-refused')
        return
    matches=[row for row in inventory()['processes'] if row['ProcessId']==identity['ProcessId']]
    if matches:
        row=matches[0]
        assert all(row[k]==identity[k] for k in identity)
        assert row['ParentProcessId']==launcher.pid and Path(row['ExecutablePath']).resolve().is_relative_to((SDK/'emulator').resolve())
        subprocess.run([P,'-NoProfile','-Command',"$ErrorActionPreference='Stop'; Stop-Process -Id "+str(row['ProcessId'])+" -Force"],capture_output=True,text=True,timeout=20,check=True)
        for _ in range(15):
            if not any(r['ProcessId']==row['ProcessId'] for r in inventory()['processes']): break
            time.sleep(1)
        assert not any(r['ProcessId']==row['ProcessId'] for r in inventory()['processes'])
    if launcher.poll() is None: launcher.terminate(); launcher.wait(timeout=15)
try:
    while time.monotonic()-started<args.window_seconds:
        current=inventory(); rows=[r for r in current['processes'] if r['ParentProcessId']==launcher.pid and Path(r['ExecutablePath']).name=='qemu-system-x86_64-headless.exe']
        if len(rows)>1: raise RuntimeError('nonunique-owned-qemu-refused')
        if rows:
            row=rows[0]
            if identity is None:
                identity={k:row[k] for k in ('ProcessId','ParentProcessId','ExecutablePath','CreationDate')}
                (D/'owned-process-private.json').write_text(json.dumps(identity),encoding='utf-8')
            else: assert all(row[k]==identity[k] for k in identity)
            report['samples'].append(dict(seconds=int(time.monotonic()-started),private_bytes=row['PrivatePageCount'],disk_free_bytes=current['disk_free_bytes'],free_physical_bytes=current['free_physical_bytes']))
            if row['PrivatePageCount']>12*1024**3 or current['disk_free_bytes']<3*1024**3 or current['free_physical_bytes']<2*1024**3:
                report['status']='resource-floor-reached';report['resource_reason']='private-memory-limit' if row['PrivatePageCount']>12*1024**3 else 'disk-floor' if current['disk_free_bytes']<3*1024**3 else 'physical-memory-floor'; break
            if not booted:
                try:
                    r=subprocess.run([A,'-s','emulator-5582','shell','getprop sys.boot_completed; getprop ro.kernel.qemu; getprop ro.build.version.sdk; getprop ro.boot.qemu.avd_name'],capture_output=True,text=True,timeout=4)
                    if r.returncode==0 and r.stdout.split()==['1','1','37','CodexVoWiFiApi37']:
                        booted=True; report['status']='booted-recovery-window'; print(json.dumps(dict(status=report['status'],memory_mib=4096)),flush=True)
                except subprocess.TimeoutExpired: report['boot_observation_timeouts']=report.get('boot_observation_timeouts',0)+1
            if (D/'finish.request').exists(): report['status']='requested-window-finish'; break
        elif launcher.poll() is not None: report['status']='launcher-terminal'; break
        save(); time.sleep(4)
    else: report['status']='bounded-window-deadline'
    stop_owned(); report['owned_processes_terminal']=not inventory()['processes']; report['boot_observed']=booted; save()
    print(json.dumps({k:v for k,v in report.items() if k!='samples'}),flush=True)
except Exception as e:
    report.update(status='monitor-failed',error=type(e).__name__); save(); print(json.dumps(dict(status=report['status'],error=report['error'])),flush=True); raise SystemExit(1)
