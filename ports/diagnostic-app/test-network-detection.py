# SPDX-License-Identifier: GPL-2.0
"""Validate actual codec/APN parsing after a production tool build; no phone or traffic."""
from pathlib import Path
import json, os, subprocess, sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
classes=B/'out/network-contracts';classes.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(map(str,[B/'out/classes',TOOLS/'android-all-11.jar']))
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',classpath,'-d',str(classes),str(B/'tests/NetworkContracts.java')],check=True)
output=subprocess.check_output([JAVA,'-cp',str(classes)+os.pathsep+classpath,'dev.codex.vowifi.tool.NetworkContracts'],text=True,encoding='utf-8',timeout=20)
report=json.loads(output.strip());assert report['status']=='passed'and not report['network_traffic']
(B/'out/network-contracts.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
