"""Compile API30 QNS and package an unsigned APK; never installs or selects it."""
from pathlib import Path
import os, subprocess, sys, zipfile
B = Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent))
from build_common import JAVA, TOOLS
out = B/'out/qns'
out.mkdir(parents=True,exist_ok=True)
classes = out/'classes'
android = TOOLS/'android-all-11.jar'
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-source','8','-target','8','-proc:none',
    '-classpath',str(android),'-d',str(classes),str(B/'qns/TrialQnsService.java')],check=True)
jar = out/'qns-classes.jar'
with zipfile.ZipFile(jar,'w') as z:
    for p in sorted(classes.rglob('*.class')): z.write(p,p.relative_to(classes).as_posix())
dex = out/'qns-dex.zip'
subprocess.run([JAVA,'-cp',str(TOOLS/'android-build-tools/d8.jar'),'com.android.tools.r8.D8',
    '--min-api','30','--lib',str(android),'--output',str(dex),str(jar)],check=True)
aapt = Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe' if os.name=='nt' else 'aapt2'))))
apk = out/'qns-api30-unsigned.apk'
subprocess.run([str(aapt),'link','-I',str(android),'--manifest',str(B/'qns/AndroidManifest.xml'),
    '--min-sdk-version','30','--target-sdk-version','30','-o',str(apk)],check=True)
with zipfile.ZipFile(dex) as src, zipfile.ZipFile(apk,'a') as dst:
    dst.writestr('classes.dex',src.read('classes.dex'))
print('Unsigned API30 QNS APK:',apk)
print('No installation, privileges, trial settings or provider overrides applied.')
