"""Compile only: does not package, install, or change Android services.

Uses Android 11 android-all for hidden platform APIs; vendor runtime ABI
remain unverified. No post-API30 IMS media stubs are supplied.
"""
import os
from pathlib import Path
import subprocess
import shutil

BASE = Path(__file__).resolve().parent

from build_common import JAVA, TOOLS
SRC = BASE / 'vendor/phhusson-ims/app/src/main/java'

# Removed Kotlin classes must not survive into the diagnostic DEX.
for name in ('api30-java-classes', 'api30-kotlin-classes'):
    output = (BASE / name).resolve()
    if output.parent != BASE.resolve():
        raise RuntimeError('Refusing cleanup outside build directory')
    if output.exists():
        shutil.rmtree(output)

def run(name, args):
    result = subprocess.run([str(JAVA), *map(str, args)], capture_output=True, timeout=90)
    (BASE / (name + '.log')).write_bytes(result.stdout + result.stderr)
    print(name, result.returncode)
    if result.returncode:
        print((result.stdout + result.stderr).decode('utf-8', 'replace'))
        raise SystemExit(result.returncode)

run('api30-java', ['-jar', TOOLS/'ecj.jar', '-source', '8', '-target', '8',
    '-proc:none', '-classpath', TOOLS/'android-all-11.jar', '-d', BASE/'api30-java-classes',
    SRC/'me/phh/ims/PhhMmTelFeatureProtected.java'])
compiler = os.pathsep.join(str(TOOLS/n) for n in [
    'compiler.jar', 'stdlib.jar', 'script.jar', 'reflect.jar', 'trove-ready.jar', 'annotations.jar'])
classpath = os.pathsep.join(map(str, [TOOLS/'android-all-11.jar', TOOLS/'stdlib.jar',
    TOOLS/'coroutines-ready.jar',
    BASE/'api30-java-classes']))
sources = sorted(str(p) for p in SRC.rglob('*.kt') if p.name != 'MainActivity.kt')
run('api30-kotlin', ['-Xmx2g', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib', '-no-reflect', '-jvm-target', '1.8', '-classpath', classpath,
    '-d', BASE/'api30-kotlin-classes', *sources])
print('API30 core compiled; this command does not build or deploy the full IMS APK.')
