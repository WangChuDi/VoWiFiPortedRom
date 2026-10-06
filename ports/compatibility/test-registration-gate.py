"""Exercise actual production callback fence with late and concurrent producers."""
from pathlib import Path
import os,subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/registration-gate-classes';out.mkdir(parents=True,exist_ok=True)
source=B.parent/'android11/stack/ims/RegistrationCallbackGate.java'
commands=[
    [JAVA,'-jar',TOOLS/'ecj.jar','-source','8','-target','8','-encoding','UTF-8','-proc:none','-d',out,source,B/'RegistrationCallbackGateTest.java'],
    [JAVA,'-cp',out,'RegistrationCallbackGateTest']]
for command in commands:
    result=subprocess.run(list(map(str,command)),capture_output=True,timeout=30)
    if result.returncode:
        print((result.stdout+result.stderr).decode('utf-8','replace'));raise SystemExit(result.returncode)
    print(result.stdout.decode('utf-8','replace').strip())
