#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Adversarial query-window/privacy checks using the production metadata parser."""
from pathlib import Path
import json,os,re,subprocess,sys
B=Path(__file__).resolve().parent;sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/sms-window-contracts';out.mkdir(parents=True,exist_ok=True)
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(out),str(B/'SmsDispatcherWindow.java'),str(B/'tests/SmsDispatcherWindowContract.java')],check=True)
r=subprocess.run([JAVA,'-cp',str(out),'dev.codex.vowifi.tool.SmsDispatcherWindowContract'],capture_output=True,text=True,check=True,timeout=15)
m=re.fullmatch(r'sms-dispatcher-window-contracts=([0-9]+)\s*',r.stdout);assert m and int(m[1])>=30
report=dict(schema=1,status='passed',contract_checks=int(m[1]),production_parser_used=True,wrong_pid_slot_time_rejected=True,conflicting_state_not_reported_available=True,process_change_not_reported_available=True,sample_overflow_not_reported_available=True,raw_content_and_process_identity_not_exported=True,fixture_only=True,device_validated=False)
(B/'out/sms-dispatcher-window-contracts.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
