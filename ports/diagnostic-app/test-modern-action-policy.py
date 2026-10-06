# SPDX-License-Identifier: GPL-2.0
"""Production result policy contracts; no device or signing material."""
from pathlib import Path
import os,subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/modern-action-contracts';out.mkdir(parents=True,exist_ok=True)
if out.resolve()!=out.absolute():raise SystemExit('contract-output-alias-refused')
framework=TOOLS/'android-all-11.jar'
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(framework),'-d',str(out),str(B/'ModernActionPolicy.java'),str(B/'tests/ModernActionPolicyTest.java')],check=True)
subprocess.run([JAVA,'-cp',str(out)+os.pathsep+str(framework),'dev.codex.vowifi.tool.ModernActionPolicyTest'],check=True)
