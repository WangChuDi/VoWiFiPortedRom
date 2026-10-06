# SPDX-License-Identifier: GPL-2.0
"""Regression checks for stale evidence and physical-target refusal, without ADB."""
from pathlib import Path
import json,subprocess,sys,tempfile
checker=Path(__file__).resolve().with_name('check-emulator.py')
with tempfile.TemporaryDirectory(prefix='codex-vowifi-reports-') as directory:
    out=Path(directory)
    for name in ('emulator-validation.json','emulator-runtime-check.json','emulator-framework-trial.json'):
        (out/name).write_text('{"status":"passed"}\n',encoding='utf-8')
    missing=out/'absent-adb.exe'
    reply=subprocess.run([sys.executable,str(checker),'--adb',str(missing),'--serial','emulator-5599','--sdk','33','--avd-name','CodexVoWiFiApi33','--output-dir',str(out)],capture_output=True,text=True)
    report=json.loads((out/'emulator-validation.json').read_text(encoding='utf-8'))
    assert reply.returncode==1 and report['status']=='failed' and report['error']=='FileNotFoundError'
    assert not (out/'emulator-runtime-check.json').exists() and not (out/'emulator-framework-trial.json').exists()
    reply=subprocess.run([sys.executable,str(checker),'--adb',str(missing),'--serial','physical-device-refused','--stdout-only'],capture_output=True,text=True)
    report=json.loads(reply.stdout)
    assert reply.returncode==1 and report['status']=='failed' and report['error']=='emulator-serial-required'
print('PASS failed-report freshness and physical-serial refusal; no device operation')
