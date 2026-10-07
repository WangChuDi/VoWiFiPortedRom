#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Host-only privacy, identity and reversible-navigation failure contracts."""
import ast,contextlib,io,json,re,runpy,subprocess,sys,tempfile
from pathlib import Path
from unittest.mock import patch
B=Path(__file__).resolve().parent;results=[]
def passed(name):results.append(dict(case=name,passed=True))
def require(ok,reason):
 if not ok:raise ValueError(reason)
source=ast.parse((B/'run-settings-probe.py').read_text());nodes=[x for x in source.body if isinstance(x,ast.FunctionDef)and x.name in {'category','command_metadata','process_identity'}]
namespace={'re':re,'require':require};exec(compile(ast.Module(body=nodes,type_ignores=[]),'diagnostic-functions','exec'),namespace)
def reply(code=0,stdout='',stderr=''):return subprocess.CompletedProcess([],code,stdout=stdout,stderr=stderr)
namespace['shell']=lambda command:reply(1)
assert namespace['process_identity']('com.android.phone')is None;passed('absent-phone-observed')
for name,responses in [('missing-system-server',[reply(1)]),('duplicate-process',[reply(stdout='17 18\n')]),('invalid-starttime',[reply(stdout='17\n'),reply(stdout='unavailable\n')])]:
 sequence=iter(responses);namespace['shell']=lambda command:next(sequence)
 try:namespace['process_identity']('system_server');raise AssertionError('identity-accepted')
 except ValueError:passed(name)
secret='synthetic-private-sentinel';value=namespace['command_metadata'](reply(stdout=secret+'\n',stderr=secret));assert secret not in json.dumps(value)and value['value_category']=='UNEXPECTED';passed('raw-values-redacted')
value=namespace['command_metadata'](reply(20,stderr='DeadObjectException '+secret));assert value['error_categories']==['DeadObjectException']and secret not in json.dumps(value);passed('fixed-command-error-only')
packages={x:'com.android.internal.systemui.navbar.'+x for x in ('gestural','threebutton','twobutton')}
original={'gestural':True,'threebutton':False,'twobutton':False}
pin='a'*64+'  /data/local/tmp/codex-modern-installation-tests/'+'0'*32+'/outer.properties\n'
cases=['apply','restore','saved-action-forged','foreign-navigation','existing-apply','existing-restore','wrong-sdk','command-timeout','records-changed']
for case in cases:
 with tempfile.TemporaryDirectory(prefix='api37-nav-contract-')as temp:
  D=Path(temp)/'trial';calls=[];state=original.copy();record_reads=0
  restore=case in {'restore','saved-action-forged','foreign-navigation','existing-restore'}
  if restore or case=='existing-apply':
   D.mkdir();saved=dict(schema=1,sdk=37,action='navigation-apply',physical_phone_modified=False,new_baseline_created=False,write_attempted=True,target='threebutton',before=original,original=original)
   if case=='saved-action-forged':saved['action']='unrelated'
   (D/'apply.json').write_text(json.dumps(saved));state={key:key=='threebutton'for key in original}
  if case=='foreign-navigation':state={key:key=='twobutton'for key in original}
  if case=='existing-restore':(D/'restore.json').write_text('preserved')
  def fake(command,**kwargs):
   global record_reads
   text=command[-1];calls.append(text)
   if text.startswith('getprop '):return reply(stdout={'ro.kernel.qemu':'1','ro.build.version.sdk':'36'if case=='wrong-sdk'else'37','ro.boot.qemu.avd_name':'CodexVoWiFiApi37'}[text.split()[-1]]+'\n')
   if text=='id -u':return reply(stdout='0\n')
   if text=='cmd overlay list --user 0':return reply(stdout='\n'.join(('['+('x'if state[k]else' ')+'] '+v)for k,v in packages.items())+'\n')
   if text.startswith('find '):
    record_reads+=1;return reply(stdout=pin.replace('a'*64,'b'*64)if case=='records-changed'and record_reads>1 else pin)
   if text.startswith('cmd overlay enable-exclusive '):
    if case=='command-timeout':raise subprocess.TimeoutExpired(command,8)
    target=text.split('.')[-1]
    for key in state:state[key]=key==target
    return reply()
   raise AssertionError('unexpected-command')
  argv=[str(B/'original-guest-navigation.py'),'--adb',str(Path(temp)/'unused-adb'),'--report-dir',str(D),'--action','restore'if restore else'apply']
  with patch.object(sys,'argv',argv),patch('subprocess.run',fake),contextlib.redirect_stdout(io.StringIO()):
   try:runpy.run_path(argv[0],run_name='__main__')
   except SystemExit as error:exit_code=error.code
  if case=='existing-apply':assert not calls;passed(case);continue
  if case=='existing-restore':assert not calls and(D/'restore.json').read_text()=='preserved';passed(case);continue
  result=json.loads((D/('restore.json'if restore else'apply.json')).read_text())
  writes=[text for text in calls if text.startswith('cmd overlay enable-exclusive ')]
  if case in {'apply','restore'}:assert exit_code==0 and len(writes)==1 and result['original_records_unchanged']is True
  elif case=='command-timeout':assert exit_code!=0 and result['status']=='observation-timeout'and result['operation_terminal_verified']is False and result['original']==original
  elif case=='records-changed':assert exit_code!=0 and result['reason']=='original-records-changed'
  else:assert exit_code!=0 and not writes
  passed(case)
print(json.dumps(dict(schema=1,status='passed',host_only=True,adb_invoked=False,guest_mutation_performed=False,cases=results)))
