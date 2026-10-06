# SPDX-License-Identifier: GPL-2.0
"""One release batch: optional build, production contracts and actual APK checks.

No device installation, root request, network traffic or module rebuild.
"""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, sys, zipfile

B = Path(__file__).resolve().parent
sys.path.insert(0, str(B.parent / 'android11'))
from build_common import JAVA, TOOLS

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', action='store_true', help='build/sign the tool once before checks')
    parser.add_argument('--artifacts-only', action='store_true', help='skip already-run production contracts')
    args = parser.parse_args()
    if args.build:
        subprocess.run([sys.executable, str(B / 'build.py')], check=True)
    if not args.artifacts_only:
        subprocess.run([sys.executable, str(B.parent / 'compatibility/test-runtime-abi-probe.py')], check=True)
    apk = B / 'out/vowifi-tool.apk'
    engine = B.parent / 'android11/stack/out/vowifi-stack-api30-services.zip'
    aapt = TOOLS / 'android-build-tools' / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    required = ["name='dev.codex.vowifi.tool'", "versionCode='11'", "versionName='0.8.0-diagnostic'", "sdkVersion:'30'", "targetSdkVersion:'30'"]
    for value in required:
        if value not in badging:
            raise SystemExit('compiled APK metadata mismatch: ' + value)
    subprocess.run([JAVA, '-jar', str(TOOLS / 'android-build-tools/apksigner.jar'), 'verify', str(apk)], check=True)
    with zipfile.ZipFile(apk) as package:
        name = 'assets/' + engine.name
        if package.namelist().count(name) != 1:
            raise SystemExit('embedded engine missing or duplicated')
        if package.read(name) != engine.read_bytes():
            raise SystemExit('embedded engine differs from independently built API30 engine')
        if 'classes.dex' not in package.namelist():
            raise SystemExit('tool DEX missing')
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    result = dict(schema=1, tool_version='0.8.0', tool_sha256=digest(apk),
                  engine_sha256=digest(engine), embedded_engine_exact=True,
                  build_run_in_this_batch=args.build,
                  source_freshness_verified=args.build,
                  production_contracts_run=not args.artifacts_only,
                  device_validated=False, modern_engine_enabled=False)
    (B / 'out/validation.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
