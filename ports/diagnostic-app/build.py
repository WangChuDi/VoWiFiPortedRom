"""Build/sign the standalone tool; bundle only the already-built replacement ZIP."""
from pathlib import Path
import os,sys,subprocess,zipfile,shutil
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out';out.mkdir(exist_ok=True)
classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
android=TOOLS/'android-all-11.jar'
def run(args):subprocess.run([str(x)for x in args],check=True)
run([JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',android,'-d',classes,*B.glob('*.java')])
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w')as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
dex=out/'dex.zip'
run([JAVA,'-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',android,'--output',dex,jar])
assets=out/'assets';assets.mkdir(exist_ok=True)
engine=B.parent/'android11/stack/out/vowifi-stack-api30-services.zip'
if not engine.exists():raise SystemExit('Build/sign the separate API30 engine first')
shutil.copyfile(engine,assets/engine.name)
unsigned=out/'vowifi-tool-unsigned.apk'
aapt=TOOLS/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2')
run([aapt,'link','-I',android,'--manifest',B/'AndroidManifest.xml','--min-sdk-version','30','--target-sdk-version','30','-A',assets,'-o',unsigned])
with zipfile.ZipFile(dex)as src,zipfile.ZipFile(unsigned,'a')as dst:
    for n in src.namelist():dst.writestr(n,src.read(n))
ks=os.environ.get('STACK_KEYSTORE')
if not ks or not os.environ.get('STACK_KEYSTORE_PASSWORD'):raise SystemExit('External signing key required; unsigned output retained')
signed=out/'vowifi-tool.apk'
run([JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','sign','--ks',ks,'--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD','--out',signed,unsigned])
run([JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','verify',signed])
print('Signed tool:',signed)
