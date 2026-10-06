"""Build/check API30 isolation and validate the real compiled modern setup override."""
from pathlib import Path
import os,runpy,shutil,subprocess,sys,zipfile
BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE.parent/'android11'))
from build_common import JAVA,TOOLS
decode=runpy.run_path(str(BASE/'read-framework-abi.py'))['decode_class']
SHARED=BASE.parent/'android11/stack'
OLD=BASE/'out/api30-iwlan-boundary'
MODERN=BASE.parent/'android12/out/iwlan-classes'
if OLD.exists():shutil.rmtree(OLD)
sources=sorted((SHARED/'common').glob('*.java'))+sorted((SHARED/'iwlan').glob('*.java'))
result=subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none',
    '-classpath',str(TOOLS/'android-all-11.jar'),'-d',str(OLD),*map(str,sources)],capture_output=True)
(BASE/'out/api30-iwlan-boundary.log').write_bytes(result.stdout+result.stderr)
if result.returncode:
    print((result.stdout+result.stderr).decode('utf-8','replace'));raise SystemExit(result.returncode)
for p in OLD.rglob('*.class'):
    data=p.read_bytes()
    for forbidden in (b'ModernIwlanDataService',b'android/telephony/data/NetworkSliceInfo',b'android/telephony/data/TrafficDescriptor'):
        if forbidden in data:raise AssertionError('API31 type leaked into API30: '+str(p))
descriptor='(ILandroid/telephony/data/DataProfile;ZZILandroid/net/LinkProperties;ILandroid/telephony/data/NetworkSliceInfo;Landroid/telephony/data/TrafficDescriptor;ZLandroid/telephony/data/DataServiceCallback;)V'
provider=MODERN/'dev/codex/vowifi/iwlan/ModernIwlanDataService$Provider.class'
methods=decode(provider.read_bytes())['methods']
if not any(m['name']=='setupDataCall' and m['descriptor']==descriptor and m['access']&1 for m in methods):
    raise AssertionError('Modern public setup override missing')
if (MODERN/'dev/codex/vowifi/iwlan/TrialIwlanDataService.class').exists():raise AssertionError('Legacy provider leaked into modern variant')
apk=BASE.parent/'android12/out/iwlan-unsigned.apk'
with zipfile.ZipFile(apk)as z:
    dex=b''.join(z.read(n)for n in z.namelist()if n.startswith('classes')and n.endswith('.dex'))
    if b'Ldev/codex/vowifi/iwlan/ModernIwlanDataService;'not in dex:raise AssertionError('Modern provider missing from APK DEX')
    if b'Ldev/codex/vowifi/iwlan/TrialIwlanDataService;'in dex:raise AssertionError('Legacy provider leaked into modern APK DEX')
aapt=Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2'))))
badging=subprocess.run([str(aapt),'dump','badging',str(apk)],capture_output=True,check=True).stdout.decode('utf-8')
(BASE/'out/modern-iwlan-badging.txt').write_text(badging,encoding='utf-8')
if "sdkVersion:'31'"not in badging or "targetSdkVersion:'31'"not in badging:raise AssertionError('Modern APK API boundary incorrect')
print('IWLAN variant isolation PASS: API30 has no API31 types; modern full overload and DEX verified; APK min/target SDK31')
