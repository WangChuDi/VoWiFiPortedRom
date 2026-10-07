"""Package verified built services; optionally reuse independently signed APK bytes."""
from pathlib import Path
import argparse,os,subprocess,sys,zipfile,hashlib
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent))
from build_common import JAVA,TOOLS
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--signed-dir',type=Path,help='Reuse exact signed iwlan.apk/qns.apk/ims.apk after matching each current unsigned build')
args=parser.parse_args()
signed_dir=args.signed_dir.absolute() if args.signed_dir else None
if signed_dir is not None and (signed_dir.resolve()!=signed_dir or not signed_dir.is_dir()):raise SystemExit('signed-directory-alias-or-missing')
ks=os.environ.get('STACK_KEYSTORE')
if signed_dir is None and (not ks or not os.environ.get('STACK_KEYSTORE_PASSWORD')):raise SystemExit('Set STACK_KEYSTORE and STACK_KEYSTORE_PASSWORD; keys remain outside repository')
def entries(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)):raise SystemExit('duplicate-apk-entry')
        return {name:archive.read(name) for name in names if not name.startswith('META-INF/')}
out=B/'out';payload={}
payload['carrier-trial.zip']=(out/'api-probe.zip').read_bytes()
for name,directory in [('iwlan','Api30Iwlan'),('qns','Api30Qns'),('ims','Api30PhhIms')]:
    unsigned=out/(name+'-unsigned.apk');signed=(signed_dir if signed_dir is not None else out)/(name+'.apk')
    if signed_dir is None:
        subprocess.run([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'sign','--ks',ks,
            '--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD',
            '--out',str(signed),str(unsigned)],check=True)
    if signed.resolve()!=signed.absolute():raise SystemExit('signed-apk-alias-refused')
    subprocess.run([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'verify',str(signed)],check=True)
    if entries(signed)!=entries(unsigned):raise SystemExit('signed-apk-does-not-match-current-build')
    payload['system/priv-app/'+directory+'/'+directory+'.apk']=signed.read_bytes()
for p in (B/'module').iterdir():
    if p.is_file():
        name='system/etc/permissions/'+p.name if p.suffix=='.xml' else p.name
        data=p.read_bytes()
        if b'\r' in data:raise RuntimeError('CRLF module script: '+p.name)
        payload[name]=data
payload['THIRD_PARTY.md']=(B.parent/'THIRD_PARTY.md').read_bytes()
payload['LICENSE-phhusson-ims']=(B.parent/'vendor/phhusson-ims/LICENSE').read_bytes()
payload['LICENSE-upstream']=(B.parent.parent.parent/'LICENSE').read_bytes()
manifest=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n'for name,data in sorted(payload.items()))
payload['payload.sha256']=manifest.encode()
archive=out/'vowifi-stack-api30-services.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED)as z:
    for name,data in sorted(payload.items()):z.writestr(name,data)
print('Service-only Magisk stage:',archive)
print('sha256='+hashlib.sha256(archive.read_bytes()).hexdigest())
