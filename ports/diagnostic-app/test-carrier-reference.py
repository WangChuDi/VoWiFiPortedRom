# SPDX-License-Identifier: GPL-2.0
"""Run carrier attribution/reference and session-evidence contracts without traffic."""
from pathlib import Path
import json,os,subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/carrier-contracts';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[B/'out/classes',TOOLS/'android-all-11.jar']))
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',cp,'-d',str(out),str(B/'tests/CarrierReferenceContracts.java')],check=True)
result=json.loads(subprocess.check_output([JAVA,'-cp',str(out)+os.pathsep+cp,'dev.codex.vowifi.tool.CarrierReferenceContracts'],text=True,encoding='utf-8',timeout=20))
assert result['status']=='passed'and not result['network_traffic']
(B/'out/carrier-contracts.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
