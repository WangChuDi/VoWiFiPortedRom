# SPDX-License-Identifier: GPL-2.0
"""Concurrent version checks: stage a fixed observer, never change phone configuration.

Root/qemu/SDK/owned-AVD guards precede staging. Raw telephony dumps remain in
the guest process; only safe explicit observations leave the guest.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import re
import subprocess

B = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('installation_audit', B / 'check-installation-matrix.py')
installation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installation)


def check(adb, sdk, serial, helper):
    record = installation.audit(adb, sdk, serial)
    record['observer_payload_staged'] = False
    record['agent_dispatch_concurrency_claim'] = False
    if record['status'] != 'observed':
        return record
    def command(*parts, timeout=30):
        return subprocess.run([adb, '-s', serial, *parts], capture_output=True,
                              text=True, timeout=timeout, check=True).stdout.strip()
    try:
        before = command('shell', 'pidof com.android.phone')
        if not re.fullmatch(r'[0-9]+', before):
            raise ValueError('single-phone-process-required')
        remote = '/data/local/tmp/codex-modern-mode-check.zip'
        digest = hashlib.sha256(helper.read_bytes()).hexdigest()
        command('push', str(helper), remote)
        record['observer_payload_staged'] = True
        if command('shell', 'sha256sum ' + remote).split()[0] != digest:
            raise ValueError('observer-payload-mismatch')
        reply = command('shell', 'CLASSPATH=' + remote +
                        ' timeout 25s app_process /system/bin ModernIwlanModeCheck')
        lines = [line for line in reply.splitlines() if line.startswith('{') and line.endswith('}')]
        if len(lines) != 1:
            raise ValueError('observer-json-unavailable')
        observation = json.loads(lines[0])
        if (observation.get('schema') != 1 or observation.get('sdk') != sdk or
            observation.get('status') != 'observed' or observation.get('read_only') is not True):
            raise ValueError('observer-result-unconfirmed')
        record['iwlan_mode_observation'] = observation
        record['observer_sha256'] = digest
        record['phone_process_unchanged'] = command('shell', 'pidof com.android.phone') == before
        if not record['phone_process_unchanged']:
            raise ValueError('phone-process-changed-during-observation')
        record['configuration_read_only'] = True
    except Exception as error:
        record.update(status='failed', error=type(error).__name__)
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--guest', action='append', required=True, help='SDK:emulator-SERIAL')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    guests = []
    for guest in args.guest:
        match = re.fullmatch(r'(3[1-7]):(emulator-[0-9]+)', guest)
        if not match:
            parser.error('explicit-owned-emulator-profile-required')
        guests.append((int(match[1]), match[2]))
    if (len(guests) > 10 or len({sdk for sdk, _ in guests}) != len(guests) or
        len({serial for _, serial in guests}) != len(guests)):
        parser.error('distinct-version-and-serial-required')
    helper = B.parent / 'out/runtime/runtime-check.zip'
    output = args.output.absolute()
    if output.resolve() != output or output.exists() or not helper.is_file() or helper.resolve() != helper.absolute():
        parser.error('fresh-canonical-report-and-built-helper-required')
    output.parent.mkdir(parents=True, exist_ok=True)
    report = dict(schema=1, status='started', concurrent_device_workers=len(guests),
                  agent_dispatch_concurrency_claim=False, versions=[])
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    with ThreadPoolExecutor(max_workers=len(guests)) as pool:
        report['versions'] = list(pool.map(lambda guest: check(args.adb, *guest, helper), guests))
    report['status'] = 'observed' if all(v['status'] == 'observed' for v in report['versions']) else 'failed'
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    return 0 if report['status'] == 'observed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
