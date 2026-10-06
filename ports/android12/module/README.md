# Modern privileged installation stage

This module is the installation prerequisite for the modern IWLAN/QNS/IMS port.
It mounts the three independently signed SDK31 APKs and their privileged permission
XML, verifies the installed APK bytes, then prepares their fixed runtime permissions,
IMS SEND_SMS system restriction exemption and IWLAN IPsec AppOp. It does not yet
select carrier providers or implement the modern selection/lease/boot supervisor.
The application's modern replacement buttons remain disabled. Real Magisk mounting,
modern VOXI authentication, voice/SMS and dual active SIM behavior are unverified.
Do not install this SDK31–37 payload over the working SDK30 phone/module.

Build the helper once, then package matching independently signed service APKs:

```sh
python ../build-runtime.py
python ../package-module.py --signed-dir /path/to/signed-modern-apks
```

The signed directory supplies `iwlan-sdk31-experiment.apk`,
`qns-sdk31-experiment.apk`, `ims-sdk31-experiment.apk`. The packager checks
apksigner verification and exact unsigned APK entry equality before bundling.
It never reads a signing key or password. An earlier output is removed before
packaging so a failed attempt cannot leave a stale success. The generated module
is `../out/modern-services-installation-stage.zip`. Every payload file has a
digest inventory; attribution and phhusson/ims GPL-2.0 license are included.

## Command and recovery boundary

Use the module's `control.sh check|prepare|restore|status` as root. Its paths are
fixed; arbitrary packages, permissions or module paths cannot be passed. Module
SDK30 is refused, and customization refuses an enabled legacy API30 stack with
conflicting package IDs. Installed apps must match the payload's complete APK
hashes, have SYSTEM and PRIVILEGED flags, belong to user0, have no split APKs,
and already have their required privileged grants. Preparation never force-installs
over another APK and never clears user/policy-fixed permission flags.

`ModernInstallationTransaction` is separate from the selected-subscription
carrier transaction. It stores private recovery properties under
`/data/adb/codex_vowifi_stack_modern/installation`, retaining build fingerprint,
SDK, package UIDs/hashes, the original fixed runtime grant bits and flags, SMS
system exemption, and the effective IPsec AppOp mode. Phases are PREPARING,
PREPARED, RESTORING, RESTORED. The original record is committed before mutation;
PREPARING can resume, and RESTORING can resume without replacing the baseline.
PREPARED is retained only while the preparation readback remains valid. A RESTORED
record is not silently reused as a new baseline; lifecycle archiving is separate.

Preparation/recovery require all configured phones idle. They serialize on the
installation lock and production carrier controller's global lock. Recovery
requires every tracked slot/sub carrier transaction to be RESTORED first, so
another card's selected providers are not deprived of permissions. Changed
package UIDs/APK hashes/builds refuse recovery. An externally revoked original
grant, removed original SMS exemption, or a different external IPsec mode also
refuses recovery. Only grants added by preparation are revoked; original grant
bits/flags, exemption and effective AppOp are checked after recovery. This does
not claim an inventory restoration of unrelated AppOps or other application policy.

Before calling the preparation helper, `control.sh` preserves the helper and boot
script outside the module directory and publishes a service.d recovery entry.
On boot it attempts guarded restoration if the module is disabled/removed/missing.
`uninstall.sh` also attempts restoration. Failed recovery retains the record;
module deletion is not assumed to stop because an uninstall script failed. If
the matching privileged apps disappear with the mount, restoration is refused
rather than granting to another UID. Recovery hooks and real disable/remove/reboot
behavior have not yet run on a modern Magisk device. Calling the bare Java CLI
directly does not publish the shell recovery hook; normal installation uses
`control.sh`/`service.sh`.

## Actual scoped tests

The same final helper and module payload passed concurrently on owned Android13/
API33 and Android16/API36 Google-APIs emulators. Existing privileged installs were
used; the runner extracted the candidate only into a private temporary fixture.
It did not mount a Magisk module or write /system. Eight independent app_process
stages seeded missing RECORD_AUDIO/SMS system exemption and denied IPsec, prepared
permissions, reopened retention, refused an external IPsec policy change without
altering it, restored the seeded baseline, simulated a RESTORING handoff, resumed
and repeated restoration, then restored the complete original fixed-permission
observation including flags. Phone PID remained unchanged. Actual forced process
termination and OS reboot were not performed.

The runner also checked refusal of nonroot production preparation and a missing
production module without creating installation state, verified payload inventory/
digests/helper consistency, and checked the module shell scripts' Android syntax.
These tests do not execute the real customize/mount/service.d/uninstall lifecycle.
No physical phone, carrier override, call or SMS was modified/tested in this batch.

```sh
python ../runtime/check-installation-emulator.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --module /path/to/modern-services-installation-stage.zip \
  --output /path/to/fresh-permission-trial.json
```

The runner strictly requires root/QEMU/exact owned AVD/single ready non-VOXI SIM,
distinct version/serial workers, and a fresh report path. Production Java has no
non-VOXI fixture switch. The fixture entry is separate and can only use its fixed
UUID temporary paths. Cleanup is attempted after errors; failed cleanup fails
the report. Safe final evidence is in
[`runtime/reports/20261006-installation-module/final.json`](../runtime/reports/20261006-installation-module/final.json).
