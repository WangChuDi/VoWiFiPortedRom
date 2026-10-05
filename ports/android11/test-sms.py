from pathlib import Path
import subprocess, os
B = Path(__file__).resolve().parent

from build_common import JAVA as J, TOOLS as T
S = B / 'vendor/phhusson-ims/app/src/main/java/me/phh/sip'
compiler = os.pathsep.join(str(T/n) for n in
    ['compiler.jar','stdlib.jar','script.jar','reflect.jar','trove-ready.jar','annotations.jar'])
commands = [
    [J, '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
     '-no-stdlib', '-no-reflect', '-jvm-target', '1.8', '-classpath', str(T/'stdlib.jar'),
     '-d', str(B/'sms-test-classes'), str(S/'SmsAddress.kt'), str(S/'SmsSubmission.kt'), str(B/'SmsProtocolTest.kt')],
    [J, '-cp', os.pathsep.join([str(B/'sms-test-classes'), str(T/'stdlib.jar')]), 'SmsProtocolTestKt']
]
output = bytearray()
for args in commands:
    r = subprocess.run(args, capture_output=True, timeout=60)
    output.extend(r.stdout+r.stderr)
    (B/'sms-tests.log').write_bytes(output)
    if r.returncode:
        print(output.decode(errors='replace'))
        raise SystemExit(r.returncode)
print(output.decode(errors='replace'))
