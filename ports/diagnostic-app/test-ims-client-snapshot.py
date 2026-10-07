#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Production closed-schema/provenance tests; no phone or private inputs."""
from pathlib import Path
import json,re,subprocess,sys
B=Path(__file__).resolve().parent;sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/ims-client-contracts';out.mkdir(parents=True,exist_ok=True)
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(out),str(B/'ImsClientSnapshot.java'),str(B/'NativeSmsQuerySnapshot.java'),str(B/'tests/ImsClientSnapshotContract.java'),str(B/'tests/NativeSmsQueryContract.java')],check=True)
r=subprocess.run([JAVA,'-cp',str(out),'dev.codex.vowifi.tool.ImsClientSnapshotContract'],capture_output=True,text=True,check=True,timeout=15)
m=re.fullmatch(r'native-sms-query-contracts=([0-9]+)\s+ims-client-snapshot-contracts=([0-9]+)\s*',r.stdout);assert m and int(m[1])>=40 and int(m[2])>=35
report=dict(schema=1,status='passed',contract_checks=int(m[2]),native_query_contract_checks=int(m[1]),production_validator_used=True,own_uid_query_validator_used=True,cross_slot_or_sub_query_refused=True,combined_ims_or_radio_scope_preserved=True,unknown_callback_count_preserved=True,wrong_owner_nonce_pid_boot_and_stale_rejected=True,modern_rebind_profile_refused=True,private_fields_rejected=True,fixture_only=True,device_validated=False)
(B/'out/ims-client-contracts.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
