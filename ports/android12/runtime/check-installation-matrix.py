# SPDX-License-Identifier: GPL-2.0
"""Read-only, concurrent installation audit of explicitly named owned emulators.

No push, package install, permission grant, binding request or carrier change.
Only fixed booleans and AppOp modes are exported, never raw service/package dumps.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import json
import re
import subprocess

PACKAGES = {
    'dev.codex.vowifi.iwlan': ('READ_PHONE_STATE', 'READ_PRIVILEGED_PHONE_STATE',
        'CONNECTIVITY_USE_RESTRICTED_NETWORKS', 'BIND_IMS_SERVICE'),
    'dev.codex.vowifi.qns': ('READ_PHONE_STATE', 'READ_PRIVILEGED_PHONE_STATE'),
    'me.phh.ims': ('READ_PHONE_STATE', 'READ_PRIVILEGED_PHONE_STATE',
        'CONNECTIVITY_USE_RESTRICTED_NETWORKS', 'MODIFY_PHONE_STATE', 'RECORD_AUDIO', 'SEND_SMS'),
}


def granted(dump, permission):
    """A requested permission line alone is never evidence of a grant."""
    return bool(re.search(r'^\s*android\.permission\.' + re.escape(permission) +
                          r': granted=true(?:,|\s|$)', dump, re.M))


def phone_bound(block):
    for binding in re.split(r'(?m)^\s*\* IntentBindRecord\{', block)[1:]:
        if (re.search(r'\brequested=true received=true hasBound=true\b', binding) and
            re.search(r'\* Client AppBindRecord\{[^\r\n]*ProcessRecord\{[^\r\n]*'
                      r':com\.android\.phone/1001\}', binding)):
            return True
    return False


def primary_user_only(dump):
    return re.findall(r'^\s*User (\d+):[^\r\n]*\binstalled=', dump, re.M) == ['0']


def audit(adb, sdk, serial):
    record = dict(sdk=sdk, status='started', audit_read_only=True)
    def shell(command):
        reply = subprocess.run([adb, '-s', serial, 'shell', command], capture_output=True,
                               text=True, timeout=25, check=True)
        if len(reply.stdout) > 4 * 1024 * 1024:
            raise ValueError('observation-too-large')
        return reply.stdout.strip()
    try:
        if (shell('getprop ro.kernel.qemu') != '1' or
            shell('getprop ro.build.version.sdk') != str(sdk) or
            shell('getprop ro.boot.qemu.avd_name') != 'CodexVoWiFiApi' + str(sdk) or
            shell('id -u') != '0'):
            raise ValueError('named-root-emulator-required')
        packages = {}
        for package, permissions in PACKAGES.items():
            paths = shell('pm path ' + package).splitlines()
            dump = shell('dumpsys package ' + package)
            # This profile intentionally checks the primary user's grants. Refuse
            # ambiguous additional user blocks instead of mixing their permissions.
            if not primary_user_only(dump):
                raise ValueError('primary-user-profile-unconfirmed')
            flags = re.search(r'^\s*(?:pkgFlags|flags)=\[([^\]]*)\]', dump, re.M)
            private = re.search(r'^\s*(?:privatePkgFlags|privateFlags)=\[([^\]]*)\]', dump, re.M)
            item = dict(
                single_privapp_apk=len(paths) == 1 and bool(re.fullmatch(
                    r'package:/system/priv-app/Api31(?:Iwlan|Qns|Ims)/[^/]+\.apk', paths[0])),
                system_app=bool(flags and 'SYSTEM' in flags[1].split()),
                privileged_app=bool(private and 'PRIVILEGED' in private[1].split()),
                permissions={permission: granted(dump, permission) for permission in permissions},
                declared_library_runtime_lookup_verified=False,
            )
            if package == 'me.phh.ims':
                line = re.search(r'^\s*android\.permission\.SEND_SMS: granted=true[^\r\n]*', dump, re.M)
                item['sms_system_restriction_exemption'] = bool(line and 'RESTRICTION_SYSTEM_EXEMPT' in line[0])
            packages[package] = item
        appop = shell('cmd appops get dev.codex.vowifi.iwlan MANAGE_IPSEC_TUNNELS')
        modes = re.findall(r'MANAGE_IPSEC_TUNNELS:\s*(allow|deny|ignore|default|foreground)\b', appop)
        record['iwlan_ipsec_appop'] = modes[0] if len(modes) == 1 else 'unknown'
        record['packages'] = packages
        services = shell('dumpsys activity services')
        bindings = {}
        # Split by actual ServiceRecord headers. A remote/client service mentioned
        # inside another record cannot count as a target service or phone binding.
        blocks = re.split(r'(?m)^\s*\* ServiceRecord\{', services)[1:]
        for package in PACKAGES:
            owned = [block for block in blocks if re.search(r'\bu0\s+' + re.escape(package) + r'/', block.splitlines()[0])]
            bindings[package] = dict(
                activity_service_record_present=bool(owned),
                phone_binding_observed=any(phone_bound(block) for block in owned),
            )
        record['framework_bindings'] = bindings
        ready = all(item['single_privapp_apk'] and item['system_app'] and item['privileged_app'] and
                    all(item['permissions'].values()) for item in packages.values())
        record['installation_permission_profile_ready'] = bool(ready and
            packages['me.phh.ims']['sms_system_restriction_exemption'] and modes == ['allow'])
        record.update(status='observed', carrier_registration_verified=False,
                      call_sms_verified=False, dual_active_sim_verified=False,
                      magisk_installation_verified=False)
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
    if len(guests) > 10 or len(set(s for _, s in guests)) != len(guests) or len(set(v for v, _ in guests)) != len(guests):
        parser.error('distinct-versions-and-serials-required')
    output = args.output.absolute()
    if output.resolve() != output or output.exists():
        parser.error('fresh-canonical-report-path-required')
    output.parent.mkdir(parents=True, exist_ok=True)
    record = dict(schema=1, status='started', concurrent_device_workers=len(guests),
                  agent_dispatch_concurrency_claim=False, versions=[])
    output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    with ThreadPoolExecutor(max_workers=len(guests)) as pool:
        record['versions'] = list(pool.map(lambda guest: audit(args.adb, *guest), guests))
    record['status'] = 'observed' if all(v['status'] == 'observed' for v in record['versions']) else 'failed'
    output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(record))
    return 0 if record['status'] == 'observed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
