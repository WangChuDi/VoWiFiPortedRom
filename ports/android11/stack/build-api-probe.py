"""Build ABI inventory and opt-in ePDG probe; no device connection or installation."""
from pathlib import Path
import subprocess, sys, zipfile
B = Path(__file__).resolve().parent
sys.path.insert(0, str(B.parent))
from build_common import JAVA, TOOLS
out = B / 'out'
out.mkdir(exist_ok=True)
classes = out / 'probe-classes'
subprocess.run([JAVA, '-jar', str(TOOLS/'ecj.jar'), '-source', '8', '-target', '8',
    '-proc:none', '-classpath', str(TOOLS/'android-all-11.jar'), '-d', str(classes),
    str(B/'StackApiProbe.java'), str(B/'IndependentEpdgProbe.java')], check=True)
jar = out / 'api-probe-classes.jar'
with zipfile.ZipFile(jar, 'w') as z:
    for p in sorted(classes.rglob('*.class')):
        z.write(p, p.relative_to(classes).as_posix())
subprocess.run([JAVA, '-cp', str(TOOLS/'android-build-tools/d8.jar'), 'com.android.tools.r8.D8',
    '--min-api', '30', '--lib', str(TOOLS/'android-all-11.jar'), '--output', str(out/'api-probe.zip'), str(jar)], check=True)
print('API inventory and opt-in independent ePDG probe ready.')
