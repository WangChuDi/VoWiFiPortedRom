from pathlib import Path
import subprocess, hashlib, os
B=Path(__file__).resolve().parent
from build_common import JAVA as J, TOOLS as T
cp=os.pathsep.join([str(B/'probe-classes'),str(T/'android-all-11.jar')])
r=subprocess.run([J,'-jar',str(T/'ecj.jar'),'-source','8','-target','8','-proc:none','-classpath',cp,
                  '-d',str(B/'digest-test-classes'),str(B/'DigestProbeTest.java'),str(B/'SmsFrameTest.java')],capture_output=True)
if r.returncode: raise RuntimeError(r.stderr.decode(errors='replace'))
h=lambda x:hashlib.md5(x).hexdigest()
expected=h((h(b'user:realm:'+bytes([1,2,3,4]))+':nonce:'+h(b'REGISTER:sip:realm')).encode())
r=subprocess.run([J,'-cp',cp+os.pathsep+str(B/'digest-test-classes'),'DigestProbeTest',expected],capture_output=True)
(B/'digest-tests.log').write_bytes(r.stdout+r.stderr)
print((r.stdout+r.stderr).decode(errors='replace'))
if r.returncode:raise SystemExit(r.returncode)
r=subprocess.run([J,'-cp',cp+os.pathsep+str(B/'digest-test-classes'),'SmsFrameTest'],capture_output=True)
(B/'sms-frame-tests.log').write_bytes(r.stdout+r.stderr)
print((r.stdout+r.stderr).decode(errors='replace'))
raise SystemExit(r.returncode)
