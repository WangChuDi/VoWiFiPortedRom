"""Package the standalone API30 receiver and compiled core into an app_process DEX."""
from pathlib import Path
import subprocess, zipfile, sys
B = Path(__file__).resolve().parent

from build_common import JAVA as J, TOOLS as T
subprocess.run([sys.executable, str(B / 'build-probe.py')], check=True)
with zipfile.ZipFile(B / 'core-probe-classes.jar', 'w') as z:
    for directory in ['probe-classes', 'api30-java-classes', 'api30-kotlin-classes']:
        root = B / directory
        for p in root.rglob('*.class'):
            z.write(p, p.relative_to(root).as_posix())
args = [J, '-Xmx2g', '-cp', str(T/'android-build-tools/d8.jar'),
        'com.android.tools.r8.D8', '--min-api', '30', '--lib', str(T/'android-all-11.jar'),
        '--output', str(B/'ims-api30-core-probe.zip'), str(B/'core-probe-classes.jar'),
        str(T/'stdlib.jar'), str(T/'coroutines-ready.jar'), str(T/'annotations.jar')]
r = subprocess.run(args, capture_output=True, timeout=90)
(B/'core-probe-d8.log').write_bytes(r.stdout+r.stderr)
if r.returncode:
    print((r.stdout+r.stderr).decode(errors='replace'))
    raise SystemExit(r.returncode)
print('IMS core diagnostic DEX ready; no provider installation.')
