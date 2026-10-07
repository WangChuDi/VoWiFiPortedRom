#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Host-only adversarial command observations; never invokes ADB or an emulator."""
import argparse,contextlib,io,json,runpy,subprocess,sys,tempfile
from pathlib import Path
from unittest.mock import patch

parser=argparse.ArgumentParser();parser.add_argument('--probe-dir',required=True,type=Path);args=parser.parse_args()
runner=Path(__file__).resolve().with_name('run-original-child-probe.py');probe=args.probe_dir.resolve()
profile=json.loads((probe/'build.json').read_text());helper=profile['runtime_helper_sha256']
payloads=[('Api31Iwlan','dev.codex.vowifi.iwlan','5ee8d67d5b9a53d98646c7d64cfdae0261a072dbca05d3c570c3c2d602340528'),('Api31Qns','dev.codex.vowifi.qns','798ac76899c8dea99b587f1e0b47fb64708fb238b3323d6f21f5a9bc74d11800'),('Api31Ims','me.phh.ims','4d645baf4c0f7a744e3cefac4190ddc83f977790451cd67c155b15ca0ad1a009')]
names=['package:com.android.phone','package:com.android.settings']+['package:'+p for _,p,_ in payloads]
cases=[('package-command-fails','package-command-unavailable'),('platform-calibration-missing','package-inventory-uncalibrated'),('retained-package-absent','retained-package-absent'),('path-command-fails','package-path-command-unavailable'),('path-output-empty','package-path-observation-unconfirmed'),('path-different','retained-privileged-path-mismatch'),('valid-final','passed'),('failed-final','failed'),('duplicate-final','failed'),('wrong-schema-final','failed')]
results=[]
for case,expected in cases:
    calls=[]
    def fake(command,**kwargs):
        calls.append(command);text=command[-1];code=0;output=''
        if command[-3]=='push':output='staged'
        elif text.startswith('getprop '):output={'ro.kernel.qemu':'1','ro.build.version.sdk':'37','ro.boot.qemu.avd_name':'CodexVoWiFiApi37'}[text.split()[-1]]+'\n'
        elif text=='id -u':output='0\n'
        elif text=='cmd package list packages':
            output='\n'.join(names)+'\n'
            if case=='package-command-fails':code=224;output=''
            elif case=='platform-calibration-missing':output='\n'.join(names[2:])+'\n'
            elif case=='retained-package-absent':output='\n'.join(names[:2])+'\n'
        elif text.startswith('pm path '):
            folder=next(f for f,p,_ in payloads if text=='pm path '+p);output='package:/system/priv-app/'+folder+'/'+folder+'.apk\n'
            if case=='path-command-fails':code=20;output=''
            elif case=='path-output-empty':output=''
            elif case=='path-different':output='package:/data/app/other/base.apk\n'
        elif text.startswith('sha256sum '):
            digest=helper if text.endswith('/codex-modern-runtime-check.zip')else profile['probe_sha256']if text.endswith('/codex-api37-original-fixture-recovery-v6.zip')else next(h for f,_,h in payloads if text.endswith('/'+f+'.apk'));output=digest+'  ignored\n'
        elif text.startswith('CLASSPATH='):
            value=dict(schema=1,sdk=37,action='inventory',status='passed')
            if case=='failed-final':value['status']='failed'
            if case=='wrong-schema-final':value['schema']=9
            output=json.dumps(value)+'\n'
            if case=='duplicate-final':output+=json.dumps(value)+'\n'
        else:raise AssertionError('unexpected-command')
        return subprocess.CompletedProcess(command,code,stdout=output,stderr='')
    with tempfile.TemporaryDirectory(prefix='api37-runner-')as temp:
        report=Path(temp)/'report.json';argv=[str(runner),'--adb',str(Path(temp)/'unused-adb'),'--probe-dir',str(probe),'--output',str(report)]
        with patch.object(sys,'argv',argv),patch('subprocess.run',fake),contextlib.redirect_stdout(io.StringIO()):
            try:runpy.run_path(str(runner),run_name='__main__')
            except SystemExit as exit:code=exit.code
        value=json.loads(report.read_text());actual=value.get('reason',value['status']);assert actual==expected,(case,actual,expected)
        if expected not in ('passed','failed'):assert not any(c[-3]=='push'or c[-1].startswith('CLASSPATH=')for c in calls)
        assert (code==0)==(case=='valid-final')
        results.append(dict(case=case,expected=expected,passed=True))
print(json.dumps(dict(schema=1,status='passed',host_only=True,adb_invoked=False,guest_mutation_performed=False,cases=results)))
