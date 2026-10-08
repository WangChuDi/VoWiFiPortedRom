# SPDX-License-Identifier: GPL-2.0
"""Build only service diagnostic changes against the verified0.9.16 live artifacts.

Baseline needs its immutable source/ports/android11/stack/out and api30-engine.zip.
Replay original D8 output first, retain every class except StackTelemetryProvider,
then add ServiceAbiCatalog. No SIP/Kotlin/controller source is recompiled.
Signing key/password must be supplied externally in the standard environment.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,sys,zipfile
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
baseline=args.baseline.resolve();D=args.output.resolve();D.mkdir(exist_ok=False)
S=B.parent/'android11/stack';O=baseline/'source/ports/android11/stack/out';android=TOOLS/'android-all-11.jar'
def sha(data):return hashlib.sha256(data).hexdigest()
basezip=baseline/'api30-engine.zip'
assert sha(basezip.read_bytes())=='71b871ada6aa6c330791f330fd2588b086117fd1238139d74da3a7875dee1c6d'
assert os.environ.get('STACK_KEYSTORE')and os.environ.get('STACK_KEYSTORE_PASSWORD')
def run(*parts):subprocess.run(list(map(str,parts)),check=True)
def entries(path):
 with zipfile.ZipFile(path)as z:
  assert len(z.namelist())==len(set(z.namelist()))
  return {n:z.read(n)for n in z.namelist()}
def dex(jar,dest,role):
 deps=[TOOLS/n for n in ('stdlib.jar','coroutines-ready.jar','annotations.jar')]if role=='ims'else[]
 run(JAVA,'-Xmx2g','-cp',TOOLS/'android-build-tools/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',android,'--output',dest,jar,*deps)
 return entries(dest)
def cert(path):
 text=subprocess.check_output([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'verify','--print-certs',str(path)],text=True)
 matches=re.findall(r'^Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})$',text,re.M);assert len(matches)==1;return matches[0]
payload=entries(basezip);roles={'iwlan':'Api30Iwlan','qns':'Api30Qns','ims':'Api30PhhIms'};evidence={}
allowed={'dev/codex/vowifi/common/StackTelemetryProvider.class','dev/codex/vowifi/common/ServiceAbiCatalog.class','dev/codex/vowifi/common/ServiceAbiCatalog$Entry.class'}
for role,directory in roles.items():
 part=D/role;part.mkdir();basejar=O/(role+'-classes.jar');old=entries(basejar)
 original=entries(O/(role+'-unsigned.apk'));replayed=dex(basejar,part/'baseline-dex.zip',role)
 signed_original=D/(role+'-baseline-payload.apk');signed_original.write_bytes(payload['system/priv-app/'+directory+'/'+directory+'.apk']);known_dex=entries(signed_original)
 assert all(original.get(name)==known_dex.get(name)for name in replayed)
 assert all(original.get(name)==data for name,data in replayed.items())
 classes=part/'classes';cp=os.pathsep.join(map(str,[android,basejar]))
 run(JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',cp,'-d',classes,S/'common/StackTelemetryProvider.java',S/'common/ServiceAbiCatalog.java')
 replacement={f.relative_to(classes).as_posix():f.read_bytes()for f in classes.rglob('*.class')};assert set(replacement)==allowed
 merged=dict(old);merged.update(replacement);assert all(merged[n]==data for n,data in old.items()if n not in allowed)
 jar=part/'classes.jar'
 with zipfile.ZipFile(jar,'w')as z:
  for name,data in merged.items():z.writestr(name,data)
 newdex=dex(jar,part/'dex.zip',role)
 manifest=(baseline/'source/ports/android11/stack'/role/'AndroidManifest.xml').read_text(encoding='utf-8')
 code=46 if role=='ims'else int(re.search(r'android:versionCode="([0-9]+)"',manifest)[1])+1
 version='0.4.10-service-abi'if role=='ims'else re.search(r'android:versionName="([^"]+)"',manifest)[1]+'-service-abi'
 manifest=re.sub(r'android:versionCode="[0-9]+"',f'android:versionCode="{code}"',manifest);manifest=re.sub(r'android:versionName="[^"]+"',f'android:versionName="{version}"',manifest)
 mf=part/'AndroidManifest.xml';mf.write_bytes(manifest.encode('utf-8'));unsigned=part/'unsigned.apk'
 aapt=TOOLS/'android-build-tools'/('aapt2.exe'if os.name=='nt'else'aapt2')
 run(aapt,'link','-I',android,'--manifest',mf,'--min-sdk-version','30','--target-sdk-version','30'if role=='qns'else'28','-o',unsigned)
 with zipfile.ZipFile(unsigned,'a')as z:
  for name,data in newdex.items():z.writestr(name,data)
 signed=D/(role+'.apk')
 run(JAVA,'-jar',TOOLS/'android-build-tools/apksigner.jar','sign','--ks',os.environ['STACK_KEYSTORE'],'--ks-key-alias','stack','--ks-pass','env:STACK_KEYSTORE_PASSWORD','--key-pass','env:STACK_KEYSTORE_PASSWORD','--out',signed,unsigned)
 sourcepath='system/priv-app/'+directory+'/'+directory+'.apk';oldapk=part/'baseline.apk';oldapk.write_bytes(payload[sourcepath]);assert cert(oldapk)==cert(signed)
 payload[sourcepath]=signed.read_bytes()
 evidence[role]=dict(baseline_jar_sha256=sha(basejar.read_bytes()),baseline_dex_replay_exact=True,non_diagnostic_classes_unchanged=True,retained_class_count=sum(n not in allowed for n in old),changed_classes=sorted(allowed),version_code=code,version_name=version,apk_sha256=sha(signed.read_bytes()),same_signing_identity=True)
prop=payload['module.prop'].decode();prop=re.sub(r'(?m)^version=.*$', 'version=0.9.9-service-abi',prop);prop=re.sub(r'(?m)^versionCode=.*$','versionCode=22',prop);payload['module.prop']=prop.encode()
payload['SERVICE-ABI-PROFILE.json']=(json.dumps(dict(schema=1,baseline_source_commit='bda69909c8aefc20f9907d71dbfe8654134bc0b1',baseline_engine_sha256=sha(basezip.read_bytes()),scope='diagnostic_class_delta_only',roles=evidence),indent=2)+'\n').encode()
payload.pop('payload.sha256');payload['payload.sha256']=''.join(sha(data)+'  '+name+'\n'for name,data in sorted(payload.items())).encode()
module=D/'api30-engine.zip'
with zipfile.ZipFile(module,'w',zipfile.ZIP_DEFLATED)as z:
 for name,data in sorted(payload.items()):z.writestr(name,data)
report=dict(schema=1,status='passed',baseline_source_commit='bda69909c8aefc20f9907d71dbfe8654134bc0b1',baseline_engine_sha256=sha(basezip.read_bytes()),sip_kotlin_recompiled=False,controller_rebuilt=False,roles=evidence,module_sha256=sha(module.read_bytes()),source_sha256={f.name:sha(f.read_bytes())for f in (S/'common/StackTelemetryProvider.java',S/'common/ServiceAbiCatalog.java')})
(D/'delta-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
