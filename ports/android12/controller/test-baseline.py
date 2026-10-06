# SPDX-License-Identifier: GPL-2.0
"""Run dump-format contracts against the built production parser, without ADB."""
from pathlib import Path
import subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parents[1]/'android11'))
from build_common import JAVA,TOOLS
out=B.parent/'out/contracts';out.mkdir(parents=True,exist_ok=True)
if out.resolve()!=out.absolute():raise SystemExit('contract-output-alias-refused')
classes=B.parent/'out/runtime/classes'
framework=B.parents[1]/'compatibility/out/frameworks/android-all-12-robolectric-7732740.jar'
import os
classpath=os.pathsep.join(map(str,(classes,framework)))
tests=('ModernCarrierBaselineTest','ModernPhoneIdleTest','ModernIwlanObservationTest')
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',classpath,'-d',str(out),*[str(B/'tests'/(name+'.java')) for name in tests]],check=True)
for name in tests:subprocess.run([JAVA,'-cp',str(out)+os.pathsep+classpath,name],check=True)
