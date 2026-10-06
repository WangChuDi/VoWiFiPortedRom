"""Build a separate unsigned API31 IWLAN experiment; never deploy or overwrite API30."""
from pathlib import Path
import hashlib,os,shutil,subprocess,sys,zipfile
BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE.parent/'android11'))
from build_common import JAVA,TOOLS
FRAMEWORK=BASE.parent/'compatibility/out/frameworks/android-all-12-robolectric-7732740.jar'
PIN='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c'
with FRAMEWORK.open('rb') as f:
    if hashlib.file_digest(f,'sha256').hexdigest()!=PIN:raise RuntimeError('API31-framework-checksum-mismatch')
AAPT=Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe' if os.name=='nt' else 'aapt2'))))
OUT=BASE/'out'
OUT.mkdir(exist_ok=True)
def run(name,args):
    result=subprocess.run([str(a)for a in args],capture_output=True,timeout=120)
    (OUT/(name+'.log')).write_bytes(result.stdout+result.stderr)
    print(name,result.returncode,flush=True)
    if result.returncode:
        print((result.stdout+result.stderr).decode('utf-8','replace'));raise SystemExit(result.returncode)
SHARED=BASE.parent/'android11/stack'
classes=OUT/'iwlan-classes'
if classes.exists():shutil.rmtree(classes)
sources=sorted((SHARED/'common').glob('*.java'))+[
    SHARED/'iwlan'/name for name in ('EpdgSession.java','EpdgAddressRequest.java','IkeApiCompat.java','TrialIwlanNetworkService.java')]
sources+=sorted((BASE/'iwlan').glob('*.java'))
run('iwlan',[JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none',
    '-classpath',FRAMEWORK,'-d',classes,*sources])
jar=OUT/'iwlan-classes.jar'
with zipfile.ZipFile(jar,'w')as z:
    for p in sorted(classes.rglob('*.class')):z.write(p,p.relative_to(classes).as_posix())
dex=OUT/'iwlan-dex.zip'
run('iwlan-dex',[JAVA,'-Xmx2g','-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8',
    '--min-api','31','--lib',FRAMEWORK,'--output',dex,jar])
apk=OUT/'iwlan-unsigned.apk'
run('iwlan-manifest',[AAPT,'link','-I',FRAMEWORK,'--manifest',BASE/'iwlan/AndroidManifest.xml',
    '--min-sdk-version','31','--target-sdk-version','31','-o',apk])
with zipfile.ZipFile(dex)as source,zipfile.ZipFile(apk,'a')as target:
    for entry in source.namelist():target.writestr(entry,source.read(entry))
print('Unsigned experimental APK:',apk)
