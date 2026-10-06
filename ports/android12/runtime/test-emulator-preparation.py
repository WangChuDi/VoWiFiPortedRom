# SPDX-License-Identifier: GPL-2.0
"""Host contracts: existing-payload verification must fail before policy writes."""
from pathlib import Path
import hashlib,importlib.util,json,subprocess,tempfile,unittest,zipfile
from unittest.mock import patch
B=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('owned_prepare',B/'prepare-emulator.py')
prepare=importlib.util.module_from_spec(spec);spec.loader.exec_module(prepare)

class ExistingPayloadContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module=B.parent/'out/modern-services-installation-stage.zip'
        with zipfile.ZipFile(cls.module)as archive:
            cls.hashes={name:hashlib.sha256(archive.read(name)).hexdigest()for name in archive.namelist()}

    def execute(self,failure=None):
        calls=[]
        def command(argv,**options):
            parts=argv[3:];calls.append(parts);output=''
            if parts[0]=='shell':
                query=parts[1]
                output={'getprop ro.kernel.qemu':'0'if failure=='guest'else'1',
                        'getprop ro.build.version.sdk':'37','getprop ro.boot.qemu.avd_name':'CodexVoWiFiApi37',
                        'id -u':'0','df -k /data/local/tmp':'Filesystem 1K-blocks Used Available Use% Mounted on\n/data 6000000 1000000 5000000 17% /data',
                        'CLASSPATH=/data/local/tmp/codex-modern-runtime-check.zip timeout 25s app_process /system/bin ModernPermissionPrep':'sms-test-restriction-exemption=true'}.get(query,'')
                if query.startswith('pm path '):
                    package=query[8:];folder=next(folder for folder,name in prepare.ROLES.values()if name==package)
                    output='package:/system/priv-app/'+folder+'/'+folder+'.apk'
                    if failure=='path'and package=='me.phh.ims':output='package:/data/app/foreign/base.apk'
                if query.startswith('sha256sum '):
                    remote=query[10:];name=remote[1:]
                    digest=self.hashes.get(name,self.hashes['controller.zip'])
                    if failure=='apk'and name.endswith('/Api31Ims.apk'):digest='0'*64
                    if failure=='xml'and name.startswith('system/etc/permissions/'):digest='0'*64
                    output=digest+'  '+remote
            return subprocess.CompletedProcess(argv,0,output+'\n','')
        staging=(B.parent/'out/runtime/emulator-preparation').absolute();staging.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='contract-report-',dir=staging)as directory:
            report=Path(directory)/'report.json'
            with patch.object(prepare.subprocess,'run',side_effect=command):result=prepare.prepare('adb',37,'emulator-5582',self.module,report,True)
            self.assertEqual(json.loads(report.read_text()),result)
        return result,calls

    def test_existing_identity_never_reboots_or_stages_system(self):
        result,calls=self.execute();self.assertEqual(result['status'],'passed')
        self.assertEqual(result['installation_method'],'existing-owned-avd-payload-verification')
        self.assertFalse(result['payloads_staged_this_run']);self.assertFalse(result['guest_reboot_requested'])
        self.assertTrue(result['installed_permission_xml_byte_identity_verified'])
        self.assertFalse(any(parts[0]in ('reboot','remount','disable-verity')or parts[0]=='push'and parts[-1].startswith('/system/')for parts in calls))
        self.assertFalse(result['magisk_mount_verified']);self.assertFalse(result['carrier_call_sms_verified'])

    def test_mismatch_refuses_before_permission_mutation(self):
        for failure,reason in [('guest','owned-root-emulator-required'),('path','installed-payload-path-mismatch'),('apk','installed-payload-byte-mismatch'),('xml','installed-permission-xml-byte-mismatch')]:
            with self.subTest(failure=failure):
                result,calls=self.execute(failure);self.assertEqual(result['status'],'failed');self.assertEqual(result['error_reason'],reason)
                self.assertFalse(any(parts[0]=='push'or parts[0]=='shell'and(parts[1].startswith(('pm grant ','appops set '))or'ModernPermissionPrep'in parts[1])for parts in calls))
                self.assertFalse(result['payloads_staged_this_run']);self.assertFalse(result['guest_reboot_requested'])

if __name__=='__main__':unittest.main()
