"""Build/sign the standalone tool; bundle only the already-built replacement ZIP."""
from pathlib import Path
import os,sys,subprocess,zipfile,shutil,hashlib,argparse
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out';out.mkdir(exist_ok=True)
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--unsigned',action='store_true',help='Build and verify bytecode/assets without reading signing material');args=parser.parse_args()
engines=[B.parent/'android11/stack/out/vowifi-stack-api30-services.zip',B.parent/'android12/out/modern-services-installation-stage.zip']
for engine in engines:
    if engine.resolve()!=engine.absolute() or not engine.is_file():raise SystemExit('Build/sign both separate engine bundles first')
def digest(data):return hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(engines[1])as archive:
    names=archive.namelist()
    if len(names)!=len(set(names)):raise SystemExit('Duplicate modern engine entry')
    profile=archive.read('module-profile.json')
    import json
    metadata=json.loads(profile)
    if metadata.get('sdk_min')!=31 or metadata.get('sdk_max')!=37 or not metadata.get('carrier_selection_included') or not metadata.get('boot_selection_supervisor_included'):raise SystemExit('Modern engine profile mismatch')
    apk_hashes=[digest(archive.read('system/priv-app/'+name+'/'+name+'.apk')) for name in ('Api31Iwlan','Api31Qns','Api31Ims')]
    controller_hash=digest(archive.read('controller.zip'));control_hash=digest(archive.read('control.sh'))
generated=out/'EngineAssets.java'
generated.write_text('// Generated artifact identities; no private signing data.\npackage dev.codex.vowifi.tool;\nfinal class EngineAssets {\n'+
    ''.join('static final String '+name+'="'+value+'";\n' for name,value in [
    ('API30_ASSET',engines[0].name),('API30_SHA256',digest(engines[0].read_bytes())),('MODERN_ASSET',engines[1].name),('MODERN_SHA256',digest(engines[1].read_bytes())),('MODERN_CONTROLLER_SHA256',controller_hash),('MODERN_CONTROL_SHA256',control_hash)])+
    'static final String[] MODERN_APK_SHA256={'+','.join('"'+value+'"'for value in apk_hashes)+'};\n}\n',encoding='utf-8')
classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
android=TOOLS/'android-all-11.jar'
def run(args):subprocess.run([str(x)for x in args],check=True)
run([JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',android,'-d',classes,*B.glob('*.java'),generated])
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w')as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
dex=out/'dex.zip'
run([JAVA,'-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',android,'--output',dex,jar])
assets=out/'assets';assets.mkdir(exist_ok=True)
for engine in engines:shutil.copyfile(engine,assets/engine.name)
unsigned=out/'vowifi-tool-unsigned.apk'
aapt=TOOLS/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2')
run([aapt,'link','-I',android,'--manifest',B/'AndroidManifest.xml','--min-sdk-version','30','--target-sdk-version','30','-A',assets,'-o',unsigned])
with zipfile.ZipFile(dex)as src,zipfile.ZipFile(unsigned,'a')as dst:
    for n in src.namelist():dst.writestr(n,src.read(n))
if args.unsigned:
    print('Unsigned tool built with both hash-identified engines:',unsigned);raise SystemExit(0)
ks=os.environ.get('STACK_KEYSTORE')
if not ks or not os.environ.get('STACK_KEYSTORE_PASSWORD'):raise SystemExit('External signing key required; unsigned output retained')
signed=out/'vowifi-tool.apk'
run([JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','sign','--ks',ks,'--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD','--out',signed,unsigned])
run([JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','verify',signed])
print('Signed tool:',signed)
