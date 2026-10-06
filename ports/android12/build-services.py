# SPDX-License-Identifier: GPL-2.0
"""Build independent unsigned API31 IWLAN/QNS/IMS; no signing or installation."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,zipfile
BASE=Path(__file__).resolve().parent
SHARED=BASE.parent/'android11/stack'
sys.path.insert(0,str(BASE.parent/'android11'))
from build_common import JAVA,TOOLS
FRAMEWORK=BASE.parent/'compatibility/out/frameworks/android-all-12-robolectric-7732740.jar'
PIN='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c'
def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
if digest(FRAMEWORK)!=PIN:raise RuntimeError('API31-framework-checksum-mismatch')
AAPT=Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe' if os.name=='nt' else 'aapt2'))))
OUT=BASE/'out';OUT.mkdir(exist_ok=True)
if OUT.resolve()!=OUT.absolute():raise RuntimeError('modern-output-alias-refused')
# Compare the complete existing API30 output and upstream source, not just APKs.
def inventory(directory):
    return {path.relative_to(directory).as_posix():digest(path) for path in directory.rglob('*') if path.is_file()}
protected={directory:inventory(directory) for directory in (SHARED/'out',BASE.parent/'android11/vendor/phhusson-ims')}
# A failed rebuild must not leave a prior success advertised as the current bundle.
for generated in ('services-manifest.json','modern-services-unsigned.zip'):
    (OUT/generated).unlink(missing_ok=True)
def run(name,args):
    result=subprocess.run([str(arg) for arg in args],capture_output=True,timeout=180)
    (OUT/(name+'.log')).write_bytes(result.stdout+result.stderr)
    print(name,result.returncode,flush=True)
    if result.returncode:
        print((result.stdout+result.stderr).decode('utf-8','replace'));raise RuntimeError(name+'-failed')
def compile_java(name,sources):
    directory=OUT/(name+'-classes')
    if directory.resolve()!=directory.absolute():raise RuntimeError('modern-classes-alias-refused')
    if directory.exists():shutil.rmtree(directory)
    run(name,[JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',FRAMEWORK,'-d',directory,*sources])
    return directory
def package(name,manifest,directories,deps=()):
    classes_jar=OUT/(name+'-classes.jar')
    with zipfile.ZipFile(classes_jar,'w') as archive:
        seen=set()
        for directory in directories:
            for path in sorted(directory.rglob('*.class')):
                relative=path.relative_to(directory).as_posix()
                if relative in seen:raise RuntimeError('duplicate-modern-class')
                seen.add(relative);archive.write(path,relative)
    dex=OUT/(name+'-dex.zip')
    run(name+'-dex',[JAVA,'-Xmx2g','-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','31','--lib',FRAMEWORK,'--output',dex,classes_jar,*deps])
    apk=OUT/(name+'-unsigned.apk')
    run(name+'-manifest',[AAPT,'link','-I',FRAMEWORK,'--manifest',manifest,'--min-sdk-version','31','--target-sdk-version','31','-o',apk])
    with zipfile.ZipFile(dex) as source,zipfile.ZipFile(apk,'a') as target:
        for entry in source.namelist():target.writestr(entry,source.read(entry))
try:
    run('modern-iwlan-build',[sys.executable,BASE/'build-iwlan.py'])
    common=sorted((SHARED/'common').glob('*.java'))
    qns=compile_java('qns',common+sorted((SHARED/'qns').glob('*.java')))
    package('qns',BASE/'qns/AndroidManifest.xml',[qns])
    run('modern-ims-source',[sys.executable,SHARED/'prepare-ims-source.py','--variant','api31'])
    source=OUT/'ims-src'
    java=compile_java('ims-java',sorted(source.rglob('*.java')))
    compiler=os.pathsep.join(str(TOOLS/name) for name in ('compiler.jar','stdlib.jar','script.jar','reflect.jar','trove-ready.jar','annotations.jar'))
    classpath=os.pathsep.join(str(path) for path in (FRAMEWORK,TOOLS/'stdlib.jar',TOOLS/'coroutines-ready.jar',java))
    kotlin=OUT/'ims-kotlin-classes'
    if kotlin.resolve()!=kotlin.absolute():raise RuntimeError('modern-kotlin-alias-refused')
    if kotlin.exists():shutil.rmtree(kotlin)
    run('ims-kotlin',[JAVA,'-Xmx2g','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','1.8','-classpath',classpath,'-d',kotlin,*sorted(path for path in source.rglob('*.kt') if path.name!='MainActivity.kt')])
    package('ims',BASE/'ims/AndroidManifest.xml',[java,kotlin],[TOOLS/'stdlib.jar',TOOLS/'coroutines-ready.jar',TOOLS/'annotations.jar'])
finally:
    for directory,original in protected.items():
        if inventory(directory)!=original:raise RuntimeError('preserved-build-or-upstream-modified')
    print('API30-output-and-upstream-source=PRESERVED',flush=True)
names={'iwlan':'dev.codex.vowifi.iwlan','qns':'dev.codex.vowifi.qns','ims':'me.phh.ims'}
manifest={'schema':1,'kind':'unsigned-service-research-bundle','min_sdk':31,'target_sdk':31,
          'framework_sha256':PIN,'installer_included':False,'device_validated':False,
          'services':{name:{'package':package_name,'file':name+'-unsigned.apk','sha256':digest(OUT/(name+'-unsigned.apk'))} for name,package_name in names.items()},
          'upstream':'phhusson/ims','license':'GPL-2.0','attribution':'../android11/THIRD_PARTY.md'}
(OUT/'services-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
with zipfile.ZipFile(OUT/'modern-services-unsigned.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for name in names:archive.write(OUT/(name+'-unsigned.apk'),name+'-unsigned.apk')
    archive.write(OUT/'services-manifest.json','manifest.json')
    archive.write(SHARED.parent/'THIRD_PARTY.md','THIRD_PARTY.md')
    archive.write(SHARED.parent/'vendor/phhusson-ims/LICENSE','LICENSE-phhusson-ims')
print('Unsigned three-service bundle:',OUT/'modern-services-unsigned.zip',flush=True)
