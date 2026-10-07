#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Original owned guest only; compare fixed read-only commands without exporting values."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,time,zipfile
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--adb',required=True,type=Path);p.add_argument('--probe-dir',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--wait-owned-boot',action='store_true');a=p.parse_args()
O=a.output.absolute();assert O.resolve()==O and not O.exists()and O.parent.is_dir()
HELPER='/data/local/tmp/codex-modern-runtime-check.zip';PIN='ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d';REMOTE='/data/local/tmp/codex-api37-settings-readonly.zip'
KEYS=[('boot-count','boot_count'),('lease-sub','codex_wfc_stack_slot_0_sub'),('lease-boot','codex_wfc_stack_slot_0_boot'),('lease-until','codex_wfc_stack_slot_0_until')]
out=dict(schema=1,sdk=37,action='settings-read-only',status='preflight',read_only=True,physical_phone_modified=False,new_baseline_created=False,raw_output_exported=False);began=time.monotonic()
def save():O.write_bytes((json.dumps(out,indent=2)+'\n').encode('utf-8'))
def adb(*args,timeout=15):return subprocess.run([str(a.adb.resolve()),'-s','emulator-5582',*args],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
def shell(cmd,timeout=15):return adb('shell',cmd,timeout=timeout)
def require(ok,reason):
 if not ok:raise ValueError(reason)
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def category(value):
 if value=='null':return 'NULL_LITERAL'
 if not value:return 'EMPTY'
 return 'DECIMAL'if re.fullmatch('-?[0-9]{1,20}',value)else'UNEXPECTED'
def command_metadata(reply):
 known=['SecurityException','DeadObjectException','DeadSystemException','NullPointerException','IllegalStateException','IllegalArgumentException','RemoteException','TransactionTooLargeException','Permission denial','Unknown command',"Can't find service",'Failure calling service','Failed transaction']
 value=reply.stdout;one=value.endswith('\n')and value.find('\n')==len(value)-1
 result=dict(command_exit=reply.returncode,single_value_line=one,stdout_bytes=len(value.encode('utf-8')),stderr_bytes=len(reply.stderr.encode('utf-8')),error_categories=[x for x in known if x in value+'\n'+reply.stderr])
 if one:result['value_category']=category(value[:-1])
 return result
def process_identity(name):
 r=shell('pidof '+name);pid=r.stdout.strip()
 # Phone can be absent in a booted emulator. Observe that absence for this
 # read-only Settings comparison; recovery still requires its original SIM.
 if name=='com.android.phone'and r.returncode==1 and not pid:return None
 require(r.returncode==0 and re.fullmatch('[0-9]+',pid),'service-process-unconfirmed')
 r=shell("awk '{print $22}' /proc/"+pid+'/stat');require(r.returncode==0 and re.fullmatch('[0-9]+',r.stdout.strip()),'service-identity-unconfirmed');return pid,r.stdout.strip()
def journal_pins():
 r=shell("find /data/local/tmp/codex-modern-installation-tests /data/local/tmp/codex-modern-persistence-tests -type f -name '*.properties' -exec sha256sum {} \\;",timeout=20)
 require(r.returncode==0,'original-record-inventory-unconfirmed');result={}
 for line in r.stdout.splitlines():
  m=re.fullmatch(r'([a-f0-9]{64})  (/data/local/tmp/codex-modern-(?:installation|persistence)-tests/[^\r\n]+\.properties)',line);require(m is not None,'original-record-pin-format-refused');require(m[2]not in result,'duplicate-record-refused');result[m[2]]=m[1]
 require(result,'original-records-required');return result
try:
 meta=json.loads((a.probe_dir/'build.json').read_text());probe=a.probe_dir/'probe.zip'
 require(meta['source_sha256']==digest(B/'Api37SettingsReadOnlyProbe.java')and meta['probe_sha256']==digest(probe),'probe-bytes-source-mismatch')
 require(meta['retained_runtime_helper_sha256']==PIN and meta['helper_definition_packaged']is False,'retained-helper-profile-required')
 with zipfile.ZipFile(a.probe_dir/'probe-classes.jar')as z:require(bool(z.namelist())and all(name.startswith('Api37SettingsReadOnlyProbe')and name.endswith('.class')for name in z.namelist()),'helper-shadowing-refused')
 if a.wait_owned_boot:
  deadline=time.monotonic()+180
  while time.monotonic()<deadline:
   ready=True
   for key,value in [('ro.kernel.qemu','1'),('ro.build.version.sdk','37'),('ro.boot.qemu.avd_name','CodexVoWiFiApi37'),('sys.boot_completed','1')]:
    try:r=shell('getprop '+key,timeout=4);ready=ready and r.returncode==0 and r.stdout.strip()==value
    except subprocess.TimeoutExpired:ready=False
   if ready:break
   time.sleep(2)
  else:raise ValueError('owned-booted-api37-required')
 for key,value in [('ro.kernel.qemu','1'),('ro.build.version.sdk','37'),('ro.boot.qemu.avd_name','CodexVoWiFiApi37'),('sys.boot_completed','1')]:
  r=shell('getprop '+key);require(r.returncode==0 and r.stdout.strip()==value,'owned-booted-api37-required')
 require(adb('root').returncode==0,'adb-root-request-unconfirmed')
 for attempt in range(5):
  r=shell('id -u')
  if r.returncode==0 and r.stdout.strip()=='0':break
  time.sleep(1)
 else:raise ValueError('root-required')
 r=shell('sha256sum '+HELPER);require(r.returncode==0 and r.stdout.split()[0]==PIN,'retained-helper-mismatch')
 out['retained_helper_verified']=True;out['probe_sha256']=meta['probe_sha256'];out['source_sha256']=meta['source_sha256'];out['direct_queries']=[];save()
 pm=shell('cmd package list packages');packages=set(pm.stdout.splitlines());out['package_calibration']=dict(command_exit=pm.returncode,known_packages_present={'package:com.android.phone','package:com.android.settings'}.issubset(packages));require(pm.returncode==0 and out['package_calibration']['known_packages_present'],'package-calibration-unconfirmed')
 identities={name:process_identity(name)for name in ('system_server','com.android.phone')};out['phone_process_present_before']=identities['com.android.phone']is not None;records=journal_pins();out['original_record_count']=len(records)
 for label,key in KEYS:
  for command,kind in [('settings --user 0 get global ','adb-settings'),('cmd settings --user 0 get global ','adb-cmd')]:
   r=shell(command+key,timeout=15);out['direct_queries'].append(dict(query=label,kind=kind,**command_metadata(r)));save()
 require(adb('push',str(probe.resolve()),REMOTE).returncode==0,'probe-push-unconfirmed');r=shell('sha256sum '+REMOTE);require(r.returncode==0 and r.stdout.split()[0]==meta['probe_sha256'],'staged-probe-mismatch')
 out['status']='running';save();print(json.dumps(dict(stage='app-context-probe-running')),flush=True)
 r=shell('CLASSPATH='+REMOTE+':'+HELPER+' timeout 170s app_process /system/bin Api37SettingsReadOnlyProbe',timeout=190)
 values=[json.loads(line)for line in r.stdout.splitlines()if line.startswith('{')and line.endswith('}')]
 finals=[v for v in values if v.get('schema')==1 and v.get('sdk')==37 and v.get('action')=='settings-read-only']
 out['entry_exit']=r.returncode;out['checkpoints']=[v['checkpoint']for v in values if v.get('checkpoint')in {'context','boot-count','lease-sub','lease-boot','lease-until'}]
 require(len(finals)==1,'single-final-required');v=finals[0]
 require(r.returncode==0 and v.get('status')=='observed'and v.get('read_only')is True and v.get('settings_written')is False,'probe-observation-unconfirmed')
 require([row['query']for row in v.get('cases',[])]==[label for label,_ in KEYS],'query-coverage-unconfirmed')
 for row in v['cases']:require([x.get('kind')for x in row['results']]==['app-child-settings','app-child-cmd','retained-helper-get'],'query-kind-unconfirmed')
 out['result']=v;out['original_records_unchanged']=journal_pins()==records;after={name:process_identity(name)for name in identities};out['phone_process_present_after']=after['com.android.phone']is not None;out['service_processes_stable']=after==identities
 require(out['original_records_unchanged']and out['service_processes_stable'],'concurrent-state-change-observed');out['status']='observed'
except subprocess.TimeoutExpired:out.update(status='host-observation-timeout',guest_terminal_verified=False)
except Exception as error:
 known={'probe-bytes-source-mismatch','retained-helper-profile-required','helper-shadowing-refused','owned-booted-api37-required','adb-root-request-unconfirmed','root-required','retained-helper-mismatch','package-calibration-unconfirmed','original-record-inventory-unconfirmed','original-record-pin-format-refused','duplicate-record-refused','original-records-required','service-process-unconfirmed','service-identity-unconfirmed','probe-push-unconfirmed','staged-probe-mismatch','single-final-required','probe-observation-unconfirmed','query-coverage-unconfirmed','query-kind-unconfirmed','concurrent-state-change-observed'}
 out.update(status='failed',error=type(error).__name__,reason=str(error)if str(error)in known else'unclassified')
out['elapsed_seconds']=round(time.monotonic()-began,3);save();print(json.dumps(out),flush=True);raise SystemExit(out['status']!='observed')
