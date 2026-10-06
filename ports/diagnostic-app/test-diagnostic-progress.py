#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Exercise production continuity/policy/checkpoints and a genuinely blocked child watchdog."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import json, subprocess, sys, time
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
classes=B/'out/diagnostic-contracts';classes.mkdir(parents=True,exist_ok=True)
android=TOOLS/'android-all-11.jar'
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(android),'-d',str(classes),*[str(B/name)for name in ('PlatformHealth.java','DiagnosticProgress.java','DiagnosticPolicy.java','tests/DiagnosticContracts.java')]],check=True)
import os
command=[JAVA,'-cp',str(classes)+os.pathsep+str(android),'dev.codex.vowifi.tool.DiagnosticContracts']
subprocess.run(command,check=True,timeout=15)
def child_check(mode):
    began=time.monotonic()
    child=subprocess.run(command+[mode],capture_output=True,text=True,encoding='utf-8',timeout=40)
    elapsed=time.monotonic()-began
    rows=[line for line in child.stdout.splitlines()if line.startswith('{')]
    if child.returncode or len(rows)!=1:raise RuntimeError('watchdog-single-result-unconfirmed')
    result=json.loads(rows[0])
    if result.get('diagnostic_complete')is not False or result.get('diagnostic_stage')!='telephony' or result.get('diagnostic_error')!='TimeoutException' or result.get('engine_supported')is not False or result.get('wifi')!='observed' or not 30<=elapsed<39:raise RuntimeError('watchdog-partial-result-unconfirmed')
    if any(result.get('platform_health',{}).get(name,{}).get('state')!='stable'for name in ('phone','system_server')):raise RuntimeError('watchdog-health-unconfirmed')
    return dict(mode=mode,elapsed_seconds=round(elapsed,2),single_partial_result=True)
with ThreadPoolExecutor(max_workers=2)as pool:children=list(pool.map(child_check,('timeout-child','return-child')))
report=dict(schema=1,status='passed',production_continuity_and_policy_contracts=True,actual_blocked_child_watchdog=True,main_return_does_not_lose_watchdog_result=True,single_partial_result=True,children=children,platform_health_fixture_only=True,device_verified=False)
(B/'out/diagnostic-progress-contracts.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
