"""Isolated context probe and direct Settings comparison; only allowlisted fixed fields."""
from pathlib import Path
import hashlib,json,subprocess,time,zipfile
W=Path(__file__).resolve().parent;R=W/'vowifi-portedrom-fork';T=W/'ims-api30-port/toolchain';D=W/'api37-context-stages-20261007';assert not D.exists();D.mkdir()
F=R/'ports/compatibility/out/frameworks/android-all-12-robolectric-7732740.jar';J='D:/ProgramFiles/DBeaver/jre/bin/java.exe';A='C:/Users/WangChuDi/Documents/Codex/2026-09-27/a-d-b/work/platform-tools/adb.exe'
C=D/'classes';C.mkdir();assert hashlib.sha256(F.read_bytes()).hexdigest()=='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c'
reply=subprocess.run([J,'-jar',str(T/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(F),'-d',str(C),str(W/'Api37ContextStages.java')],capture_output=True,timeout=60);assert reply.returncode==0
jar=D/'classes.jar'
with zipfile.ZipFile(jar,'w')as z:
    for path in sorted(C.glob('*.class')):z.write(path,path.name)
probe=D/'probe.zip';reply=subprocess.run([J,'-cp',str(T/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(F),'--output',str(probe),str(jar)],capture_output=True,timeout=60);assert reply.returncode==0
def adb(*parts,timeout=45):return subprocess.run([A,'-s','emulator-5582',*parts],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
for key,value in [('ro.kernel.qemu','1'),('ro.build.version.sdk','37'),('ro.boot.qemu.avd_name','CodexVoWiFiApi37')]:assert adb('shell','getprop '+key).stdout.strip()==value
assert adb('shell','id -u').stdout.strip()=='0';remote='/data/local/tmp/codex-api37-context-stages.zip';digest=hashlib.sha256(probe.read_bytes()).hexdigest();assert adb('push',str(probe),remote).returncode==0 and adb('shell','sha256sum '+remote).stdout.split()[0]==digest
started=time.monotonic();reply=adb('shell','CLASSPATH='+remote+' timeout 35s app_process /system/bin Api37ContextStages');rows=[json.loads(line)for line in reply.stdout.splitlines()if line.startswith('{')and line.endswith('}')];final=[row for row in rows if row.get('status')in ('observed','failed')]
assert len(final)<=1;report=dict(schema=1,sdk=37,read_only=True,probe_sha256=digest,source_sha256=hashlib.sha256((W/'Api37ContextStages.java').read_bytes()).hexdigest(),entry_exit=reply.returncode,host_elapsed_seconds=round(time.monotonic()-started,3),timeout_cause_verified=False,checkpoints=[row['checkpoint']for row in rows if row.get('checkpoint')in {'looper-prepare','looper-prepared','activity-thread-main','activity-thread-main-created','system-context','system-context-created','telephony-bootstrap','telephony-bootstrap-ready','settings-content-query','settings-content-query-completed'}],result=final[0]if final else None,physical_phone_modified=False,raw_logs_exported=False,direct_settings=[])
for kind,command in [('settings','settings --user 0 get global boot_count'),('cmd-settings','cmd settings --user 0 get global boot_count')]:
    r=adb('shell',command,timeout=10);report['direct_settings'].append(dict(kind=kind,exit=r.returncode,integer_result_observed=r.stdout.strip().isdigit(),cannot_find_settings_service="Can't find service: settings"in r.stdout+r.stderr))
(D/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode('utf-8'));print(json.dumps(report),flush=True)
