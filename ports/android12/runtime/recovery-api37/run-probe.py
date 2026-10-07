#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Bounded invocation for this exact old, owned API37 fixture. No physical serial."""
import argparse,hashlib,json,subprocess,time
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True,type=Path);parser.add_argument('--probe-dir',required=True,type=Path);parser.add_argument('--output',required=True,type=Path)
parser.add_argument('--action',choices=('inventory','selection','installation','outer','audit'),default='inventory');parser.add_argument('--ordinal',type=int,default=0)
args=parser.parse_args();output=args.output.absolute()
if output.resolve()!=output or output.exists() or not output.parent.is_dir() or not 0<=args.ordinal<64:raise SystemExit('fresh-canonical-report-required')
serial='emulator-5582';helper='/data/local/tmp/codex-modern-runtime-check.zip';remote='/data/local/tmp/codex-api37-original-fixture-recovery.zip'
expected_helper='ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d'
expected_source='94dcdbf50584e668f7718ab5ceb3cc6a5bcd8efb8bd1d4dcd9d39f175fe4952f'
expected_probe='2cb0989abaeea05dbb78cef3279596627061f3081b85e23407419ce4d7a1f913'
payloads=[('Api31Iwlan','dev.codex.vowifi.iwlan','5ee8d67d5b9a53d98646c7d64cfdae0261a072dbca05d3c570c3c2d602340528'),('Api31Qns','dev.codex.vowifi.qns','798ac76899c8dea99b587f1e0b47fb64708fb238b3323d6f21f5a9bc74d11800'),('Api31Ims','me.phh.ims','4d645baf4c0f7a744e3cefac4190ddc83f977790451cd67c155b15ca0ad1a009')]
def adb(*parts,timeout=55):return subprocess.run([str(args.adb.resolve()),'-s',serial,*parts],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
def shell(text,timeout=55):return adb('shell',text,timeout=timeout)
def require(condition,reason):
    if not condition:raise ValueError(reason)
report=dict(schema=1,sdk=37,action=args.action,read_only=args.action=='inventory',status='preflight',physical_phone_modified=False,raw_output_exported=False,timeout_cause_verified=False)
success=False;started=time.monotonic()
try:
    build=json.loads((args.probe_dir/'build.json').read_text(encoding='utf-8'));probe=args.probe_dir/'probe.zip'
    require(build['source_sha256']==expected_source and build['probe_sha256']==expected_probe,'tested-probe-profile-required')
    require(build['runtime_helper_sha256']==expected_helper,'retained-helper-required')
    require(hashlib.sha256(probe.read_bytes()).hexdigest()==build['probe_sha256'],'probe-bytes-mismatch')
    source=Path(__file__).resolve().parent/'Api37OriginalFixtureRecovery.java'
    require(hashlib.sha256(source.read_bytes()).hexdigest()==build['source_sha256'],'corresponding-source-mismatch')
    for key,value in [('ro.kernel.qemu','1'),('ro.build.version.sdk','37'),('ro.boot.qemu.avd_name','CodexVoWiFiApi37')]:require(shell('getprop '+key,timeout=8).stdout.strip()==value,'owned-api37-required')
    require(shell('id -u').stdout.strip()=='0','root-required')
    require(shell('sha256sum '+helper).stdout.split()[0]==expected_helper,'retained-helper-mismatch')
    for folder,package,digest in payloads:
        path='/system/priv-app/'+folder+'/'+folder+'.apk'
        require(shell('pm path '+package).stdout.strip()=='package:'+path,'retained-privileged-path-mismatch')
        require(shell('sha256sum '+path).stdout.split()[0]==digest,'retained-apk-mismatch')
    require(adb('push',str(probe.resolve()),remote).returncode==0,'probe-push-failed')
    require(shell('sha256sum '+remote).stdout.split()[0]==build['probe_sha256'],'staged-probe-mismatch')
    report.update(status='running',retained_helper_verified=True,retained_payloads_verified=True,probe_sha256=build['probe_sha256'])
    output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    run_started=time.monotonic();reply=shell('CLASSPATH='+remote+':'+helper+' timeout 45s app_process /system/bin Api37OriginalFixtureRecovery '+args.action+' '+str(args.ordinal))
    values=[json.loads(line)for line in reply.stdout.splitlines()if line.startswith('{')and line.endswith('}')]
    allowed={'context','subscription','telephony','sim-observed','inventory-observed','selection-restore','installation-restore','original-outer-entry'}
    report.update(entry_exit=reply.returncode,host_command_elapsed_seconds=round(time.monotonic()-run_started,3),exit_indicates_sigkill=reply.returncode==137,checkpoints=[v['checkpoint']for v in values if v.get('checkpoint')in allowed])
    finals=[v for v in values if v.get('sdk')==37 and (v.get('action')==args.action or v.get('stage')in ('cleanup','audit'))]
    if finals:report['result']=finals[-1]
    success=reply.returncode==0 and bool(finals);report['status']='passed'if success else'failed'
except subprocess.TimeoutExpired:
    # Loss of a host observation does not prove the guest process ended or rolled back.
    report.update(status='host-observation-timeout',guest_terminal_verified=False)
except Exception as error:
    known={'owned-api37-required','root-required','tested-probe-profile-required','retained-helper-required','probe-bytes-mismatch','corresponding-source-mismatch','retained-helper-mismatch','retained-privileged-path-mismatch','retained-apk-mismatch','probe-push-failed','staged-probe-mismatch'}
    report.update(status='failed',error=type(error).__name__,reason=str(error)if str(error)in known else'unclassified')
report['host_total_elapsed_seconds']=round(time.monotonic()-started,3)
output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report));raise SystemExit(0 if success else 1)
