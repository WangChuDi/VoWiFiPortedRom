# SPDX-License-Identifier: GPL-2.0
"""False-success and stale-report regression, with no Android/device operations."""
from pathlib import Path
import importlib.util,json,subprocess,sys,tempfile
B=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('persistence_checker',B/'check-persistence-emulator.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
valid=dict(schema=1,sdk=33,mask=2,persistent=True,production_snapshot=True,
           selected_live_and_native_stream_file=True,reopened_transaction_restored=True,
           entire_original_config_restored=True,selected_override_absent=True,restoration_retry_safe=True)
module.validate_result(valid,33,True)
cases=1
for key in ('production_snapshot','selected_live_and_native_stream_file','reopened_transaction_restored',
            'entire_original_config_restored','selected_override_absent','restoration_retry_safe'):
    for value in (False,None,'true'):
        changed=dict(valid);changed[key]=value
        try:module.validate_result(changed,33,True);raise AssertionError('incomplete-proof-accepted')
        except ValueError:cases+=1
try:module.validate_result(valid,36,True);raise AssertionError('other-version-proof-accepted')
except ValueError:cases+=1
with tempfile.TemporaryDirectory(prefix='modern-persistence-contract-') as temporary:
    report=Path(temporary)/'persistence-preflight.json'
    report.write_text(json.dumps(dict(status='passed',entire_original_config_restored=True)),encoding='utf-8')
    reply=subprocess.run([sys.executable,str(B/'check-persistence-emulator.py'),'--adb','DO-NOT-EXECUTE',
                          '--serial','physical-device-refused','--sdk','33','--avd-name','CodexVoWiFiApi33',
                          '--output-dir',temporary],capture_output=True,text=True)
    saved=json.loads(report.read_text(encoding='utf-8'))
    if reply.returncode!=1 or saved.get('status')!='failed' or saved.get('entire_original_config_restored') is not None:
        raise AssertionError('stale-success-survived')
    cases+=1
print('Modern persistence report contracts passed: '+str(cases))
