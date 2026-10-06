# SPDX-License-Identifier: GPL-2.0
"""Install exact module payloads on an explicitly owned, writable-system test AVD.

This is a disposable emulator prerequisite, not a Magisk installer or phone tool.
It never downloads/signs APKs, accepts a physical serial, or selects a carrier.
"""
from pathlib import Path
from contextlib import contextmanager
import argparse,hashlib,json,re,shutil,stat,subprocess,tempfile,time,zipfile
B=Path(__file__).resolve().parent
ROLES={'iwlan':('Api31Iwlan','dev.codex.vowifi.iwlan'),
       'qns':('Api31Qns','dev.codex.vowifi.qns'),'ims':('Api31Ims','me.phh.ims')}
XML='privapp-permissions-codex-vowifi-modern.xml'

@contextmanager
def owned_staging():
    base=(B.parent/'out/runtime/emulator-preparation').absolute()
    if base.resolve()!=base:raise ValueError('canonical-staging-parent-required')
    base.mkdir(parents=True,exist_ok=True)
    directory=Path(tempfile.mkdtemp(prefix='codex-modern-avd-',dir=base)).absolute()
    def verify():
        if (base.resolve()!=base or directory.resolve()!=directory or
            directory.parent!=base or not directory.name.startswith('codex-modern-avd-')):
            raise ValueError('owned-staging-cleanup-target-required')
    verify()
    try:yield directory
    finally:
        # Resolve and check the exact target immediately before recursive cleanup.
        # If ownership changed, retain it for inspection instead of following it.
        verify();shutil.rmtree(directory)

def prepare(adb,sdk,serial,module,output):
    report=dict(schema=1,sdk=sdk,status='started',phase='input',
                installation_method='owned-avd-writable-system',
                magisk_mount_verified=False,carrier_call_sms_verified=False,
                dual_active_sim_verified=False,physical_phone_modified=False)
    def save():output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    def command(*parts,check=True,timeout=45):
        return subprocess.run([adb,'-s',serial,*parts],capture_output=True,text=True,
                              encoding='utf-8',check=check,timeout=timeout)
    def shell(text,**options):return command('shell',text,**options).stdout.strip()
    def guard(root=False):
        if (shell('getprop ro.kernel.qemu')!='1' or shell('getprop ro.build.version.sdk')!=str(sdk) or
            shell('getprop ro.boot.qemu.avd_name')!='CodexVoWiFiApi'+str(sdk) or
            root and shell('id -u')!='0'):raise ValueError('owned-root-emulator-required')
    def boot():
        deadline=time.monotonic()+180
        while time.monotonic()<deadline:
            value=command('shell','getprop sys.boot_completed',check=False,timeout=10)
            if value.returncode==0 and value.stdout.strip()=='1':guard();return
            time.sleep(2)
        raise ValueError('existing-emulator-boot-observation-timeout')
    def root():
        guard();command('root');command('wait-for-device',timeout=30);guard(True)
    save()
    try:
        expected={'controller.zip','installation.properties','module-profile.json','payload.sha256',
                  'THIRD_PARTY.md','LICENSE-phhusson-ims','LICENSE-upstream','module.prop',
                  'customize.sh','control.sh','service.sh','uninstall.sh','recovery-boot.sh',
                  'system/etc/permissions/'+XML}|{
                  'system/priv-app/'+folder+'/'+folder+'.apk'for folder,_ in ROLES.values()}
        with zipfile.ZipFile(module)as archive:
            names=archive.namelist()
            if set(names)!=expected or len(set(names))!=len(names)or any(
                stat.S_ISLNK(e.external_attr>>16)or e.file_size>8*1024*1024 for e in archive.infolist()):
                raise ValueError('fixed-module-inventory-required')
            inventory={}
            for line in archive.read('payload.sha256').decode('utf-8').splitlines():
                match=re.fullmatch(r'([0-9a-f]{64})  (.+)',line)
                if not match or match[2]in inventory:raise ValueError('fixed-module-digests-required')
                inventory[match[2]]=match[1]
            if set(inventory)!=expected-{'payload.sha256'}or any(
                hashlib.sha256(archive.read(name)).hexdigest()!=value for name,value in inventory.items()):
                raise ValueError('module-digest-mismatch')
            profile=json.loads(archive.read('module-profile.json'))
            if profile.get('sdk_min')!=31 or profile.get('sdk_max')!=37:raise ValueError('module-sdk-profile-refused')
            helper=archive.read('controller.zip')
            if helper!=(B.parent/'out/runtime/runtime-check.zip').read_bytes():raise ValueError('current-helper-required')
            payload={name:archive.read(name)for name in names if name.startswith('system/')}
        report.update(module_sha256=hashlib.sha256(module.read_bytes()).hexdigest(),
                      helper_sha256=hashlib.sha256(helper).hexdigest(),phase='guest-identity')
        save();root();report['phase']='remount';save()
        for attempt in range(3):
            guard(True);reply=command('remount',check=False)
            if reply.returncode==0 and command('shell','test -w /system/priv-app',check=False).returncode==0:break
            command('disable-verity');command('reboot');boot();root()
        else:raise ValueError('writable-system-test-profile-required')
        report['phase']='privileged-payload';save()
        # Only fixed, already-built payload entries are extracted, never scripts.
        with owned_staging()as directory:
            for role,(folder,package)in ROLES.items():
                guard(True);remote='/system/priv-app/'+folder+'/'+folder+'.apk'
                local=directory/(role+'.apk');local.write_bytes(payload[remote[1:]])
                shell('mkdir -p /system/priv-app/'+folder);command('push',str(local),remote);shell('chmod 644 '+remote)
            xml=directory/XML;xml.write_bytes(payload['system/etc/permissions/'+XML])
            command('push',str(xml),'/system/etc/permissions/'+XML);shell('chmod 644 /system/etc/permissions/'+XML)
            shell('restorecon -RF /system/priv-app/Api31Iwlan /system/priv-app/Api31Qns /system/priv-app/Api31Ims /system/etc/permissions/'+XML)
            report['phase']='reboot-and-observe';save();command('reboot');boot();root()
            for role,(folder,package)in ROLES.items():
                remote='/system/priv-app/'+folder+'/'+folder+'.apk'
                if shell('pm path '+package)!='package:'+remote or shell('sha256sum '+remote).split()[0]!=inventory[remote[1:]]:raise ValueError('installed-payload-identity-unconfirmed')
                shell('pm grant '+package+' android.permission.READ_PHONE_STATE')
            report['phase']='fixed-permissions';save()
            local=directory/'controller.zip';local.write_bytes(helper)
            remote='/data/local/tmp/codex-modern-runtime-check.zip';command('push',str(local),remote)
            if shell('sha256sum '+remote).split()[0]!=report['helper_sha256']:raise ValueError('staged-helper-mismatch')
            reply=command('shell','CLASSPATH='+remote+' timeout 25s app_process /system/bin ModernPermissionPrep',check=False)
            if reply.returncode or reply.stdout.strip()!='sms-test-restriction-exemption=true':raise ValueError('fixed-sms-exemption-unconfirmed')
            for permission in ('RECORD_AUDIO','SEND_SMS'):shell('pm grant me.phh.ims android.permission.'+permission)
            shell('appops set dev.codex.vowifi.iwlan MANAGE_IPSEC_TUNNELS allow')
        report.update(status='passed',phase='installed-byte-identity-verified',
                      installed_apk_sha256={role:inventory['system/priv-app/'+folder+'/'+folder+'.apk']for role,(folder,_)in ROLES.items()},
                      helper_staged_and_verified=True,requested_permission_preparation_completed=True,
                      permission_profile_independently_verified=False,
                      staging_cleanup_target_verified=True)
    except Exception as error:report.update(status='failed',error=type(error).__name__)
    save();return report

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True);parser.add_argument('--sdk',required=True,type=int,choices=range(31,38))
    parser.add_argument('--serial',required=True);parser.add_argument('--module',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+',args.serial):parser.error('emulator-only-serial-required')
    module=args.module.absolute();output=args.output.absolute()
    if module.resolve()!=module or not module.is_file()or output.resolve()!=output or output.exists():parser.error('canonical-module-and-fresh-report-required')
    output.parent.mkdir(parents=True,exist_ok=True)
    result=prepare(args.adb,args.sdk,args.serial,module,output);print(json.dumps(result))
    return 0 if result['status']=='passed'else 1
if __name__=='__main__':raise SystemExit(main())
