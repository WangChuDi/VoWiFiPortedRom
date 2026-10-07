#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Build only the isolated recorded-fixture probe; do not rebuild an engine."""
import argparse, hashlib, json, subprocess, zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
for name in ('framework','headers','toolchain','output'):parser.add_argument('--'+name,required=True,type=Path)
args=parser.parse_args();out=args.output.absolute()
if out.resolve()!=out or out.exists():raise SystemExit('fresh-canonical-output-required')
framework=args.framework.resolve();headers=args.headers.resolve();tools=args.toolchain.resolve()
if hashlib.sha256(framework.read_bytes()).hexdigest()!='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c':raise SystemExit('retained-framework-required')
source=Path(__file__).resolve().parent/'Api37CompatibleFixtureRecovery.java'
out.mkdir(parents=True);classes=out/'classes';classes.mkdir()
import os
java=Path(os.environ['JAVA_HOME'])/'bin'/('java.exe'if os.name=='nt'else'java')
classpath=os.pathsep.join(map(str,(framework,headers)))
commands=[[str(java),'-jar',str(tools/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',classpath,'-d',str(classes),str(source)]]
for command in commands:
    result=subprocess.run(command,capture_output=True,timeout=60)
    if result.returncode:raise SystemExit('probe-compile-failed')
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w')as archive:
    for entry in sorted(classes.glob('*.class')):archive.write(entry,entry.name)
probe=out/'probe.zip'
result=subprocess.run([str(java),'-cp',str(tools/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(framework),'--classpath',str(headers),'--output',str(probe),str(jar)],capture_output=True,timeout=60)
if result.returncode:raise SystemExit('probe-dex-failed')
identity=dict(schema=1,source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),probe_sha256=hashlib.sha256(probe.read_bytes()).hexdigest(),compile_headers_sha256=hashlib.sha256(headers.read_bytes()).hexdigest(),runtime_helper_sha256='ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d',retained_engine_rebuilt=False)
(out/'build.json').write_text(json.dumps(identity,indent=2)+'\n',encoding='utf-8')
print(json.dumps(identity))
