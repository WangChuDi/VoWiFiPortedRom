"""Build unsigned API30 replacement apps. Signing and deployment are separate."""
from pathlib import Path
import os,subprocess,sys,zipfile,shutil
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent))
from build_common import JAVA,TOOLS
ANDROID=TOOLS/'android-all-11.jar'
AAPT=Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe' if os.name=='nt' else 'aapt2'))))
OUT=B/'out'
OUT.mkdir(exist_ok=True)
def run(name,args):
    r=subprocess.run([str(x) for x in args],capture_output=True,timeout=120)
    (OUT/(name+'.log')).write_bytes(r.stdout+r.stderr)
    print(name,r.returncode)
    if r.returncode:print((r.stdout+r.stderr).decode('utf-8','replace'));raise SystemExit(r.returncode)
def compile_java(name,sources,extra=None):
    d=OUT/(name+'-classes')
    if d.exists():shutil.rmtree(d)
    cp=os.pathsep.join(map(str,[ANDROID,* (extra or [])]))
    run(name,[JAVA,'-jar',TOOLS/'ecj.jar','-source','8','-target','8','-proc:none','-classpath',cp,'-d',d,*sources])
    return d
def package(name,manifest,dirs,deps=(),target=28):
    jar=OUT/(name+'-classes.jar')
    with zipfile.ZipFile(jar,'w')as z:
        for directory in dirs:
            for p in sorted(directory.rglob('*.class')):z.write(p,p.relative_to(directory).as_posix())
    dex=OUT/(name+'-dex.zip')
    run(name+'-dex',[JAVA,'-Xmx2g','-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',ANDROID,'--output',dex,jar,*deps])
    apk=OUT/(name+'-unsigned.apk')
    run(name+'-manifest',[AAPT,'link','-I',ANDROID,'--manifest',manifest,'--min-sdk-version','30','--target-sdk-version',str(target),'-o',apk])
    with zipfile.ZipFile(dex)as src,zipfile.ZipFile(apk,'a')as dst:
        for entry in src.namelist():dst.writestr(entry,src.read(entry))
    print('Unsigned app:',apk.name)
iwlan=compile_java('iwlan',sorted((B/'iwlan').glob('*.java')))
package('iwlan',B/'iwlan/AndroidManifest.xml',[iwlan])
qns=compile_java('qns',sorted((B/'qns').glob('*.java')))
package('qns',B/'qns/AndroidManifest.xml',[qns],target=30)
run('ims-source',[sys.executable,B/'prepare-ims-source.py'])
src=OUT/'ims-src'
java=compile_java('ims-java',sorted(src.rglob('*.java')))
compiler=os.pathsep.join(str(TOOLS/n)for n in ['compiler.jar','stdlib.jar','script.jar','reflect.jar','trove-ready.jar','annotations.jar'])
cp=os.pathsep.join(map(str,[ANDROID,TOOLS/'stdlib.jar',TOOLS/'coroutines-ready.jar',java]))
kotlin=OUT/'ims-kotlin-classes'
if kotlin.exists():shutil.rmtree(kotlin)
run('ims-kotlin',[JAVA,'-Xmx2g','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','1.8','-classpath',cp,'-d',kotlin,
    *sorted(p for p in src.rglob('*.kt')if p.name!='MainActivity.kt')])
package('ims',B/'ims/AndroidManifest.xml',[java,kotlin],[TOOLS/'stdlib.jar',TOOLS/'coroutines-ready.jar',TOOLS/'annotations.jar'])
