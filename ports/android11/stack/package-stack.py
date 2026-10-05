"""Sign built APKs with external local key and package the service-only Magisk stage."""
from pathlib import Path
import os,subprocess,sys,zipfile,hashlib
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent))
from build_common import JAVA,TOOLS
ks=os.environ.get('STACK_KEYSTORE')
if not ks or not os.environ.get('STACK_KEYSTORE_PASSWORD'):raise SystemExit('Set STACK_KEYSTORE and STACK_KEYSTORE_PASSWORD; keys remain outside repository')
out=B/'out';payload={}
payload['carrier-trial.zip']=(out/'api-probe.zip').read_bytes()
for name,directory in [('iwlan','Api30Iwlan'),('qns','Api30Qns'),('ims','Api30PhhIms')]:
    unsigned=out/(name+'-unsigned.apk');signed=out/(name+'.apk')
    subprocess.run([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'sign','--ks',ks,
        '--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD',
        '--out',str(signed),str(unsigned)],check=True)
    subprocess.run([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'verify',str(signed)],check=True)
    payload['system/priv-app/'+directory+'/'+directory+'.apk']=signed.read_bytes()
for p in (B/'module').iterdir():
    if p.is_file():
        name='system/etc/permissions/'+p.name if p.suffix=='.xml' else p.name
        data=p.read_bytes()
        if b'\r' in data:raise RuntimeError('CRLF module script: '+p.name)
        payload[name]=data
manifest=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n'for name,data in sorted(payload.items()))
payload['payload.sha256']=manifest.encode()
archive=out/'vowifi-stack-api30-services.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED)as z:
    for name,data in sorted(payload.items()):z.writestr(name,data)
print('Service-only Magisk stage:',archive)
print('sha256='+hashlib.sha256(archive.read_bytes()).hexdigest())
