# SPDX-License-Identifier: GPL-2.0
"""One modern batch: optional three-service build, root runtime helper, APK checks."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
parser=argparse.ArgumentParser()
group=parser.add_mutually_exclusive_group()
group.add_argument('--services',action='store_true',help='build all three modern services once')
group.add_argument('--iwlan',action='store_true',help='rebuild changed IWLAN, retaining hash-verified prior QNS/IMS artifacts')
args=parser.parse_args()
for path in [B/'out',B/'out/runtime',B/'out/runtime/classes']+[B/'out/runtime'/name for name in ('classes.jar','runtime-check.zip')]+[B/'out'/name for name in ('services-manifest.json','modern-services-unsigned.zip','iwlan-unsigned.apk','qns-unsigned.apk','ims-unsigned.apk')]:
    if path.resolve()!=path.absolute():raise SystemExit('runtime-artifact-alias-refused')
framework=B.parent/'compatibility/out/frameworks/android-all-12-robolectric-7732740.jar'
if hashlib.sha256(framework.read_bytes()).hexdigest()!='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c':raise SystemExit('framework-hash-mismatch')
if args.services:subprocess.run([sys.executable,str(B/'build-services.py')],check=True)
if args.iwlan:
    manifest_path=B/'out/services-manifest.json'
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest['framework_sha256']!='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c' or manifest['installer_included'] or manifest['device_validated']:raise SystemExit('retained-bundle-profile-mismatch')
    for role in ('qns','ims'):
        if manifest['services'][role]['sha256']!=hashlib.sha256((B/'out'/ (role+'-unsigned.apk')).read_bytes()).hexdigest():raise SystemExit('retained-artifact-mismatch')
    manifest_path.unlink();(B/'out/modern-services-unsigned.zip').unlink(missing_ok=True)
    subprocess.run([sys.executable,str(B/'build-iwlan.py')],check=True)
    manifest['services']['iwlan']['sha256']=hashlib.sha256((B/'out/iwlan-unsigned.apk').read_bytes()).hexdigest()
    manifest['retained_prior_artifact_roles']=['qns','ims']
    manifest_path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    with zipfile.ZipFile(B/'out/modern-services-unsigned.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for role in ('iwlan','qns','ims'):archive.write(B/'out'/ (role+'-unsigned.apk'),role+'-unsigned.apk')
        archive.write(manifest_path,'manifest.json')
        archive.write(B.parent/'android11/THIRD_PARTY.md','THIRD_PARTY.md')
        archive.write(B.parent/'android11/vendor/phhusson-ims/LICENSE','LICENSE-phhusson-ims')
out=B/'out/runtime';out.mkdir(exist_ok=True)
classes=out/'classes'
if out.resolve()!=out.absolute() or classes.resolve()!=classes.absolute() or not classes.resolve().is_relative_to((B/'out').resolve()):raise SystemExit('runtime-output-alias-refused')
if classes.exists():shutil.rmtree(classes)
(out/'runtime-check.zip').unlink(missing_ok=True)
sources=[B/'runtime/ModernRuntimeCheck.java',B/'runtime/ModernPermissionPrep.java',B/'runtime/ModernFrameworkTrial.java',B.parent/'diagnostic-app/RuntimeAbiProbe.java',B.parent/'android11/stack/CarrierConfigReadCompat.java']
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(framework),'-d',str(classes),*map(str,sources)],check=True)
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w') as archive:
    for path in sorted(classes.rglob('*.class')):archive.write(path,path.relative_to(classes).as_posix())
subprocess.run([JAVA,'-cp',str(TOOLS/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(framework),'--output',str(out/'runtime-check.zip'),str(jar)],check=True)
subprocess.run([sys.executable,str(B.parent/'compatibility/check-modern-services.py')],check=True)
print('Modern runtime helper built; device validation remains separate')
