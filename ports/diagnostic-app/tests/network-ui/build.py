# SPDX-License-Identifier: GPL-2.0
"""Build same-signature network UI instrumentation against an exact target APK."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,zipfile
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parents[2]/'android11'))
from build_common import JAVA,TOOLS
parser=argparse.ArgumentParser();parser.add_argument('--target-apk',type=Path,required=True);args=parser.parse_args()
target=args.target_apk.resolve();assert target.is_file()
out=B/'out';out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
def run(*args):subprocess.run(list(map(str,args)),check=True)
framework=TOOLS/'android-all-11.jar'
run(JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',framework,'-d',classes,B/'NetworkUiTrial.java')
with zipfile.ZipFile(out/'classes.jar','w')as archive:
 for path in sorted(classes.rglob('*.class')):archive.write(path,path.relative_to(classes).as_posix())
run(JAVA,'-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',framework,'--output',out/'dex.zip',out/'classes.jar')
aapt=TOOLS/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2')
run(aapt,'link','-I',framework,'--manifest',B/'AndroidManifest.xml','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'dex.zip')as src,zipfile.ZipFile(out/'unsigned.apk','a')as dst:
 for name in src.namelist():dst.writestr(name,src.read(name))
if not os.environ.get('STACK_KEYSTORE')or not os.environ.get('STACK_KEYSTORE_PASSWORD'):raise SystemExit('External same-signature key required')
run(JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','sign','--ks',os.environ['STACK_KEYSTORE'],'--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD','--out',out/'network-ui-test.apk',out/'unsigned.apk')
run(JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','verify',out/'network-ui-test.apk')
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
report=dict(schema=1,target_tool_sha256=digest(target),test_apk_sha256=digest(out/'network-ui-test.apk'),source_sha256=digest(B/'NetworkUiTrial.java'),manifest_sha256=digest(B/'AndroidManifest.xml'),phone_modified=False)
(out/'build.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
