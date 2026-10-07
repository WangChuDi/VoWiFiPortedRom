# SPDX-License-Identifier: GPL-2.0
"""Host-only synthetic SIM AKA framing checks; no device, network or live material."""
from pathlib import Path
import argparse,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',type=Path,required=True);p.add_argument('--ecj',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
out=a.output.absolute()
if out.resolve()!=out or not out.is_relative_to((B/'out').resolve())or out.exists():raise SystemExit('fresh-canonical-stack-output-required')
out.mkdir(parents=True);classes=out/'classes'
with(out/'compile.log').open('wb')as log:subprocess.run([str(a.java),'-jar',str(a.ecj),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(classes),str(B/'ims/AkaResponseCodec.java'),str(B/'host-tests/AkaResponseCodecContract.java')],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=45)
r=subprocess.run([str(a.java),'-cp',str(classes),'AkaResponseCodecContract'],capture_output=True,text=True,check=True,timeout=20)
if r.stdout.strip()!='aka-framing-contracts=20 passed':raise SystemExit('framing-contract-result-unconfirmed')
value=dict(schema=1,status='passed',synthetic_contracts=20,live_sim_authentication_attempted=False)
(out/'report.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8');print(json.dumps(value))
