# SPDX-License-Identifier: GPL-2.0
"""Package independently signed modern APKs; never read private signing material."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,zipfile
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--signed-dir',required=True,type=Path)
args=parser.parse_args()
signed=args.signed_dir.absolute()
if signed.resolve()!=signed:raise SystemExit('signed-directory-alias-refused')
output=B/'out/modern-services-installation-stage.zip'
if output.resolve()!=output.absolute():raise SystemExit('module-output-alias-refused')
output.unlink(missing_ok=True)
payload={}
profile=['schema=1']
def digest(data):return hashlib.sha256(data).hexdigest()
def entries(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(set(names))!=len(names):raise ValueError('duplicate-apk-entry')
        return {name:archive.read(name) for name in names if not name.startswith('META-INF/')}
for index,(role,directory) in enumerate([('iwlan','Api31Iwlan'),('qns','Api31Qns'),('ims','Api31Ims')]):
    apk=signed/(role+'-sdk31-experiment.apk')
    if apk.resolve()!=apk:raise SystemExit('signed-apk-alias-refused')
    subprocess.run([JAVA,'-jar',str(TOOLS/'android-build-tools/apksigner.jar'),'verify',str(apk)],check=True)
    if entries(apk)!=entries(B/'out'/(role+'-unsigned.apk')):raise SystemExit('signed-apk-does-not-match-current-build')
    data=apk.read_bytes();payload['system/priv-app/'+directory+'/'+directory+'.apk']=data
    profile.append('apk.'+str(index)+'='+digest(data))
payload['installation.properties']=('\n'.join(profile)+'\n').encode()
payload['controller.zip']=(B/'out/runtime/runtime-check.zip').read_bytes()
payload['system/etc/permissions/privapp-permissions-codex-vowifi-modern.xml']=(B/'runtime/privapp-permissions-codex-vowifi-modern.xml').read_bytes()
for name in ('module.prop','customize.sh','control.sh','service.sh','uninstall.sh','recovery-boot.sh'):
    path=B/'module'/name
    if not path.is_file() or path.is_symlink():raise SystemExit('module-source-entry-refused')
    data=path.read_bytes()
    if b'\r' in data:raise SystemExit('module-script-crlf-refused')
    payload[path.name]=data
payload['THIRD_PARTY.md']=(B.parent/'android11/THIRD_PARTY.md').read_bytes()
payload['LICENSE-phhusson-ims']=(B.parent/'android11/vendor/phhusson-ims/LICENSE').read_bytes()
payload['LICENSE-upstream']=(B.parent.parent/'LICENSE').read_bytes()
metadata=dict(schema=1,stage='privileged-installation-and-permission-preparation',sdk_min=31,sdk_max=37,
              carrier_selection_included=False,boot_selection_supervisor_included=False,magisk_mount_verified=False,
              carrier_call_sms_verified=False,dual_active_sim_verified=False)
payload['module-profile.json']=(json.dumps(metadata,indent=2)+'\n').encode()
payload['payload.sha256']=''.join(digest(data)+'  '+name+'\n' for name,data in sorted(payload.items())).encode()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for name,data in sorted(payload.items()):archive.writestr(name,data)
print(json.dumps(dict(artifact=output.name,sha256=digest(output.read_bytes()),profile=metadata)))
