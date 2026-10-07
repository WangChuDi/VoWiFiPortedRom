#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Build a same-signature instrumentation APK; never rebuild the installed tool."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile
parser=argparse.ArgumentParser()
for name in ('framework','toolchain','output'):parser.add_argument('--'+name,required=True,type=Path)
args=parser.parse_args();source=Path(__file__).resolve().parent;out=args.output.absolute()
if out.resolve()!=out or out.exists():raise SystemExit('fresh-canonical-output-required')
java=Path(os.environ['JAVA_HOME'])/'bin'/('java.exe'if os.name=='nt'else'java');tools=args.toolchain.resolve();framework=args.framework.resolve()
key=os.environ.get('STACK_KEYSTORE')
if not key or not os.environ.get('STACK_KEYSTORE_PASSWORD'):raise SystemExit('external-matching-signing-key-required')
out.mkdir(parents=True);classes=out/'classes';classes.mkdir()
def run(command):
    result=subprocess.run(list(map(str,command)),capture_output=True,timeout=60)
    if result.returncode:raise SystemExit('test-apk-build-stage-failed')
run([java,'-jar',tools/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',framework,'-d',classes,source/'OwnUidTrial.java'])
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w')as archive:
    for entry in sorted(classes.rglob('*.class')):archive.write(entry,entry.relative_to(classes).as_posix())
dex=out/'dex.zip';run([java,'-cp',tools/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',framework,'--output',dex,jar])
unsigned=out/'unsigned.apk';run([tools/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2'),'link','-I',framework,'--manifest',source/'AndroidManifest.xml','-o',unsigned])
with zipfile.ZipFile(dex)as src,zipfile.ZipFile(unsigned,'a')as dst:
    for name in src.namelist():dst.writestr(name,src.read(name))
signed=out/'own-uid-trial.apk';run([java,'-jar',tools/'android-build-tools/apksigner.jar','sign','--ks',key,'--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD','--out',signed,unsigned]);run([java,'-jar',tools/'android-build-tools/apksigner.jar','verify',signed])
report=dict(schema=1,status='passed',test_apk_sha256=hashlib.sha256(signed.read_bytes()).hexdigest(),source_sha256=hashlib.sha256((source/'OwnUidTrial.java').read_bytes()).hexdigest(),target_apk_rebuilt=False,engine_rebuilt=False,phone_modified=False)
(out/'build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
