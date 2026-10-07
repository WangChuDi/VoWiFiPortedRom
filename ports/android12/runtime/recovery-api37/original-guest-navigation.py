#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Reversible navigation-only diagnostic for the original API37 emulator."""
from pathlib import Path
import argparse,json,re,subprocess,time

p=argparse.ArgumentParser();p.add_argument('--adb',required=True,type=Path);p.add_argument('--report-dir',required=True,type=Path);p.add_argument('--action',choices=('apply','restore'),required=True);p.add_argument('--wait-owned-guest',action='store_true');p.add_argument('--report-name');a=p.parse_args()
D=a.report_dir.absolute()
if D.resolve()!=D or not D.parent.is_dir():raise SystemExit('canonical-report-required')
name=a.report_name or a.action+'.json'
if a.report_name and (a.action!='restore'or not re.fullmatch(r'restore-[a-z0-9-]+\.json',name)):raise SystemExit('fixed-restore-report-required')
if a.action=='apply':
 if D.exists():raise SystemExit('fresh-report-required')
 D.mkdir()
elif not D.is_dir()or not(D/'apply.json').is_file()or(D/name).exists():raise SystemExit('fresh-restore-report-required')
REPORT=D/name
PACKAGES={x:'com.android.internal.systemui.navbar.'+x for x in ('gestural','threebutton','twobutton')}
def shell(command,timeout=8):return subprocess.run([str(a.adb.resolve()),'-s','emulator-5582','shell',command],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
def require(ok,reason):
 if not ok:raise ValueError(reason)
def owned():
 for key,value in [('ro.kernel.qemu','1'),('ro.build.version.sdk','37'),('ro.boot.qemu.avd_name','CodexVoWiFiApi37')]:
  r=shell('getprop '+key);require(r.returncode==0 and r.stdout.strip()==value,'owned-api37-required')
def guard():
 owned()
 r=shell('id -u');require(r.returncode==0 and r.stdout.strip()=='0','root-required')
def overlays():
 r=shell('cmd overlay list --user 0');require(r.returncode==0,'overlay-service-unavailable');out={}
 for label,pkg in PACKAGES.items():
  rows=[m.group(1)for line in r.stdout.splitlines()if(m:=re.fullmatch(r'\[([ x])\] '+re.escape(pkg),line))]
  require(len(rows)<=1,'duplicate-overlay-refused')
  if rows:out[label]=rows[0]=='x'
 require('gestural'in out and 'threebutton'in out and sum(out.values())==1,'known-single-navigation-required');return out
def records():
 r=shell("find /data/local/tmp/codex-modern-installation-tests /data/local/tmp/codex-modern-persistence-tests -type f -name '*.properties' -exec sha256sum {} \\;",20);require(r.returncode==0,'original-records-unavailable');out={}
 for line in r.stdout.splitlines():
  m=re.fullmatch(r'([a-f0-9]{64})  (/data/local/tmp/codex-modern-(?:installation|persistence)-tests/[^\r\n]+\.properties)',line);require(m is not None and m[2]not in out,'original-record-format-refused');out[m[2]]=m[1]
 require(out,'original-records-required');return out
out=dict(schema=1,sdk=37,action='navigation-'+a.action,status='preflight',physical_phone_modified=False,new_baseline_created=False,raw_output_exported=False,write_attempted=False)
known={'owned-api37-required','root-required','overlay-service-unavailable','duplicate-overlay-refused','known-single-navigation-required','original-records-unavailable','original-record-format-refused','original-records-required','overlay-change-unconfirmed','overlay-readback-unconfirmed','original-records-changed','saved-navigation-required','navigation-owner-state-changed'}
try:
 if a.wait_owned_guest:
  deadline=time.monotonic()+180
  while True:
   try:owned();break
   except (ValueError,subprocess.TimeoutExpired):
    if time.monotonic()>=deadline:raise ValueError('owned-api37-required')
    time.sleep(1)
  r=subprocess.run([str(a.adb.resolve()),'-s','emulator-5582','root'],capture_output=True,timeout=10);require(r.returncode==0,'root-required')
  for attempt in range(10):
   try:guard();break
   except (ValueError,subprocess.TimeoutExpired):time.sleep(1)
  else:raise ValueError('root-required')
 guard();deadline=time.monotonic()+35
 while True:
  try:before=overlays();break
  except ValueError as error:
   if str(error)!='overlay-service-unavailable'or time.monotonic()>=deadline:raise
   time.sleep(1)
 pins=records();out['original_record_count']=len(pins);out['before']=before
 if a.action=='apply':out['original']=before;target='threebutton'
 else:
  saved=json.loads((D/'apply.json').read_text());original=saved.get('original');require(saved.get('schema')==1 and saved.get('sdk')==37 and saved.get('action')=='navigation-apply'and saved.get('physical_phone_modified')is False and saved.get('new_baseline_created')is False and saved.get('write_attempted')is True and saved.get('target')=='threebutton'and isinstance(original,dict)and set(original)==set(before)and all(type(v)is bool for v in original.values())and sum(original.values())==1 and saved.get('before')==original,'saved-navigation-required');out['original']=original;target=next(k for k,v in original.items()if v)
  expected={label:label=='threebutton'for label in original};require(before in (original,expected),'navigation-owner-state-changed')
 # Save the original state before the only write, so a failed observation can
 # still be restored. Neither Settings nor telephony records are written.
 out['target']=target;REPORT.write_text(json.dumps(out,indent=2)+'\n')
 out['write_attempted']=True;REPORT.write_text(json.dumps(out,indent=2)+'\n')
 r=shell('cmd overlay enable-exclusive --user 0 --category '+PACKAGES[target]);out['change_exit']=r.returncode;require(r.returncode==0,'overlay-change-unconfirmed')
 after=overlays();out['after']=after;require(after.get(target)is True,'overlay-readback-unconfirmed');out['original_records_unchanged']=records()==pins;require(out['original_records_unchanged'],'original-records-changed');out['status']='applied'if a.action=='apply'else'restored'
except subprocess.TimeoutExpired:out.update(status='observation-timeout',operation_terminal_verified=False)
except Exception as error:out.update(status='failed',error=type(error).__name__,reason=str(error)if str(error)in known else'unclassified')
if D.is_dir():REPORT.write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps(out));raise SystemExit(out['status']not in ('applied','restored'))
