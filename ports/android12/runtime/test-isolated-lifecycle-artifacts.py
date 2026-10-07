# SPDX-License-Identifier: GPL-2.0
"""Host contracts for artifact selection. No ADB, signing or device mutation."""
from pathlib import Path
from unittest import mock
import contextlib,hashlib,importlib.util,io,json,os,runpy,subprocess,sys,tempfile,unittest,zipfile
B=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('isolated_runner',B/'check-installation-emulator.py')
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)

class Artifacts(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name).resolve()
  self.signed=self.root/'signed';self.build=self.root/'build'
  self.signed.mkdir();self.build.mkdir();self.helper=self.root/'selected.zip';self.helper.write_bytes(b'isolated-controller')
  for role in ('iwlan','qns','ims'):
   for path in (self.signed/(role+'-sdk31-experiment.apk'),self.build/(role+'-unsigned.apk')):
    with zipfile.ZipFile(path,'w')as z:z.writestr('classes.dex',role.encode())
  self.output=self.root/'module.zip';self.report=self.root/'report.json'
 def tearDown(self):self.temp.cleanup()
 def package(self,extra=()):
  args=['package-module.py','--signed-dir',str(self.signed),'--build-dir',str(self.build),'--helper',str(self.helper),'--output',str(self.output),*extra]
  with mock.patch.object(sys,'argv',args),mock.patch.dict(os.environ,{'JAVA':'test-only-java'}),mock.patch('subprocess.run')as signing,contextlib.redirect_stdout(io.StringIO()):
   runpy.run_path(str(B.parent/'package-module.py'),run_name='__main__')
  self.assertEqual(signing.call_count,3)
 def invoke(self,extra=()):
  args=['check-installation-emulator.py','--adb','never-executed-adb','--guest','37:emulator-5582','--module',str(self.output),'--output',str(self.report),'--helper',str(self.helper),'--helper-slot','audit',*extra]
  with mock.patch.object(sys,'argv',args),mock.patch.object(runner,'trial',return_value={'status':'passed'})as trial,contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
   result=runner.main()
  return result,trial
 def test_packaged_controller_is_selected_helper(self):
  self.package()
  with zipfile.ZipFile(self.output)as z:
   self.assertEqual(z.read('controller.zip'),self.helper.read_bytes())
   pins=dict(line.split('  ',1)[::-1]for line in z.read('payload.sha256').decode().splitlines())
   self.assertEqual(pins['controller.zip'],hashlib.sha256(self.helper.read_bytes()).hexdigest())
 def test_output_keeps_default_module_unchanged(self):
  default=B.parent/'out/modern-services-installation-stage.zip';before=default.read_bytes()if default.exists()else None
  self.package();self.assertEqual(default.read_bytes()if default.exists()else None,before)
 def test_existing_custom_output_refused_without_overwrite(self):
  self.output.write_bytes(b'original')
  with self.assertRaisesRegex(SystemExit,'fresh-module-output-required'):self.package()
  self.assertEqual(self.output.read_bytes(),b'original')
 def test_signed_unsigned_difference_refused(self):
  with zipfile.ZipFile(self.signed/'qns-sdk31-experiment.apk','a')as z:z.writestr('foreign.dex',b'foreign')
  with self.assertRaisesRegex(SystemExit,'signed-apk-does-not-match-current-build'):self.package()
  self.assertFalse(self.output.exists())
 def test_duplicate_apk_entry_refused(self):
  import warnings
  with warnings.catch_warnings():
   warnings.simplefilter('ignore')
   with zipfile.ZipFile(self.signed/'iwlan-sdk31-experiment.apk','a')as z:z.writestr('classes.dex',b'duplicate')
  with self.assertRaisesRegex(ValueError,'duplicate-apk-entry'):self.package()
 def test_build_directory_alias_refused(self):
  with self.assertRaisesRegex(SystemExit,'canonical-build-directory-required'):self.package(['--build-dir',str(self.build/'..'/'build')])
 def test_helper_alias_refused(self):
  with self.assertRaisesRegex(SystemExit,'canonical-helper-file-required'):self.package(['--helper',str(self.build/'..'/'selected.zip')])
 def test_missing_helper_refused(self):
  self.helper.unlink()
  with self.assertRaisesRegex(SystemExit,'canonical-helper-file-required'):self.package()
 def test_runner_uses_selected_helper_and_audit_slot(self):
  self.package();code,trial=self.invoke();self.assertEqual(code,0)
  self.assertEqual(trial.call_args.args[-2:],(self.helper,'audit'))
 def test_runner_mismatched_helper_before_worker(self):
  self.package();self.helper.write_bytes(b'different')
  with mock.patch.object(runner,'trial')as trial:
   with self.assertRaises(SystemExit):self.invoke()
   trial.assert_not_called()
  self.assertFalse(self.report.exists())
 def test_runner_existing_report_refused(self):
  self.package();self.report.write_bytes(b'previous')
  with self.assertRaises(SystemExit):self.invoke()
  self.assertEqual(self.report.read_bytes(),b'previous')
 def test_runner_wrong_guest_refused(self):
  self.package()
  with self.assertRaises(SystemExit):self.invoke(['--guest','37:physical-phone'])
  self.assertFalse(self.report.exists())
 def test_runner_duplicate_version_refused(self):
  self.package()
  with self.assertRaises(SystemExit):self.invoke(['--guest','37:emulator-5584'])
 def test_audit_resident_not_claimed_supported(self):
  self.package()
  with self.assertRaises(SystemExit):self.invoke(['--resident'])
 def test_audit_preparing_interruption_allowed(self):
  self.package();code,trial=self.invoke(['--preparing']);self.assertEqual(code,0)
  self.assertTrue(trial.call_args.args[6])
 def test_resident_slot_full_resident_allowed(self):
  self.package();code,trial=self.invoke(['--helper-slot','resident','--resident']);self.assertEqual(code,0)
  self.assertTrue(trial.call_args.args[4]);self.assertFalse(trial.call_args.args[5])
  self.assertEqual(trial.call_args.args[-1],'resident')
 def test_resident_scoped_requires_resident(self):
  self.package()
  with self.assertRaises(SystemExit):self.invoke(['--helper-slot','resident','--resident-only'])
 def test_resident_and_preparing_still_separate(self):
  self.package()
  with self.assertRaises(SystemExit):self.invoke(['--helper-slot','resident','--resident','--preparing'])
 def isolated_branch(self,presence,remote_hash=None,slot='audit'):
  calls=[];remote=runner.HELPER_SLOTS[slot]
  digest=hashlib.sha256(self.helper.read_bytes()).hexdigest()
  def command(args,**kwargs):
   calls.append(args[3:]);parts=args[3:]
   if parts[0]=='push':return subprocess.CompletedProcess(args,0,'','')
   text=parts[-1]
   fixed={'getprop ro.kernel.qemu':'1','getprop ro.build.version.sdk':'37',
    'getprop ro.boot.qemu.avd_name':'CodexVoWiFiApi37','id -u':'0'}
   if text in fixed:return subprocess.CompletedProcess(args,0,fixed[text]+'\n','')
   if text=='test -e '+remote:return subprocess.CompletedProcess(args,presence,'','')
   if text=='sha256sum '+remote:return subprocess.CompletedProcess(args,0,(remote_hash or digest)+'  '+remote+'\n','')
   # Deliberately stop before creating a fixture; test only staging decisions.
   raise ValueError('test-stop-before-fixture')
  with mock.patch.object(runner.subprocess,'run',side_effect=command):
   value=runner.trial('never-executed-adb',37,'emulator-5582',self.output,helper=self.helper,helper_slot=slot)
  return value,calls
 def test_matching_audit_helper_reused_without_push(self):
  value,calls=self.isolated_branch(0)
  self.assertTrue(value['matching_existing_audit_helper_reused'])
  self.assertTrue(value['original_runtime_helper_preserved'])
  self.assertFalse(any(c[0]=='push'for c in calls))
 def test_different_audit_helper_refused_without_push(self):
  value,calls=self.isolated_branch(0,'0'*64)
  self.assertEqual(value['status'],'failed')
  self.assertFalse(any(c[0]=='push'for c in calls))
  self.assertFalse(any('mkdir' in c[-1]for c in calls))
 def test_missing_audit_helper_pushed_only_to_audit(self):
  value,calls=self.isolated_branch(1)
  pushed=[c for c in calls if c[0]=='push']
  self.assertEqual(pushed,[['push',str(self.helper),'/data/local/tmp/codex-modern-owner-audit.zip']])
  self.assertTrue(value['original_runtime_helper_preserved'])
 def test_unknown_audit_presence_refused_without_push(self):
  value,calls=self.isolated_branch(20)
  self.assertEqual(value['status'],'failed')
  self.assertFalse(any(c[0]=='push'for c in calls))
 def test_matching_resident_helper_reused_without_push(self):
  value,calls=self.isolated_branch(0,slot='resident')
  self.assertTrue(value['matching_existing_resident_helper_reused'])
  self.assertTrue(value['original_runtime_helper_preserved'])
  self.assertTrue(value['original_audit_helper_preserved'])
  self.assertFalse(any(c[0]=='push'for c in calls))
 def test_different_resident_helper_refused_without_push(self):
  value,calls=self.isolated_branch(0,'0'*64,slot='resident')
  self.assertEqual(value['status'],'failed')
  self.assertFalse(any(c[0]=='push'for c in calls))
  self.assertFalse(any('mkdir' in c[-1]for c in calls))
 def test_missing_resident_helper_pushed_only_to_resident(self):
  value,calls=self.isolated_branch(1,slot='resident')
  self.assertEqual([c for c in calls if c[0]=='push'],[['push',str(self.helper),runner.HELPER_SLOTS['resident']]])
  self.assertTrue(value['original_runtime_helper_preserved'])
  self.assertTrue(value['original_audit_helper_preserved'])
 def test_unknown_resident_presence_refused_without_push(self):
  value,calls=self.isolated_branch(20,slot='resident')
  self.assertEqual(value['status'],'failed')
  self.assertFalse(any(c[0]=='push'for c in calls))
 def test_unknown_direct_slot_refused_before_adb(self):
  with mock.patch.object(runner.subprocess,'run')as adb:
   with self.assertRaisesRegex(ValueError,'fixed-helper-slot-required'):
    runner.trial('never-executed-adb',37,'emulator-5582',self.output,helper=self.helper,helper_slot='foreign')
   adb.assert_not_called()

if __name__=='__main__':unittest.main()
