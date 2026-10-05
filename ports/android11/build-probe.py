from pathlib import Path
import subprocess, zipfile
B=Path(__file__).resolve().parent
from build_common import JAVA as J, TOOLS as T

def run(name,args):
    r=subprocess.run(args,capture_output=True,timeout=60)
    (B/(name+'.log')).write_bytes(r.stdout+r.stderr)
    if r.returncode: raise RuntimeError((r.stdout+r.stderr).decode(errors='replace'))
run('probe-java',[J,'-jar',str(T/'ecj.jar'),'-source','8','-target','8','-proc:none',
    '-classpath',str(T/'android-all-11.jar'),'-d',str(B/'probe-classes'),str(B/'ImsApi30Probe.java'),str(B/'ImsRegistrationProbe.java'),str(B/'ImsAuthenticatedProbe.java'),str(B/'ImsNestedPolicy.java'),str(B/'ImsPolicyBroker.java'),str(B/'ImsSmsProbe.java')])
with zipfile.ZipFile(B/'probe-classes.jar','w') as z:
    for p in (B/'probe-classes').rglob('*.class'):z.write(p,p.relative_to(B/'probe-classes').as_posix())
run('probe-d8',[J,'-cp',str(T/'android-build-tools/d8.jar'),'com.android.tools.r8.D8',
    '--min-api','30','--lib',str(T/'android-all-11.jar'),'--output',str(B/'ims-api30-probe.zip'),str(B/'probe-classes.jar')])
print('Diagnostic DEX built; no APK install or IMS provider change.')
