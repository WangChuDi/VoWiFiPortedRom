# Modern privileged installation stage

This module is the installation prerequisite for the modern IWLAN/QNS/IMS port.
It mounts the three independently signed SDK31 APKs and their privileged permission
XML, verifies the installed APK bytes, then prepares their fixed runtime permissions,
IMS SEND_SMS system restriction exemption and IWLAN IPsec AppOp. It also includes
fixed owner selection commands and a resident selection/lease/recovery supervisor.
Diagnostic tool0.9.2 includes experimental modern replacement buttons. Real Magisk mounting,
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
system exemption, and the recorded IPsec AppOp mode. Phases are PREPARING,
PREPARED, RESTORING, RESTORED. The original record is committed before mutation;
PREPARING can resume, and RESTORING can resume without replacing the baseline.
PREPARED is retained only while the preparation readback remains valid. A RESTORED
record is not silently reused as a new baseline; a verified restored record is
atomically archived before a new preparation cycle. A saved baseline can be read
when current module APKs are missing; restoration still verifies the installed
apps against their original UID/hash before changing their permissions.

Root worker commands also include `select SLOT SUB MASK`, `retain|renew|verify
SLOT SUB TOKEN`, `selection-restore SLOT SUB TOKEN`, `recover|owner-status SLOT SUB`
and `restore-all|tick|supervise`. Mask bits are IWLAN=1, QNS=2, IMS=4. `select` first
checks the ready23415 owner without creating owner state, publishes independent
recovery, prepares installation permissions and confirms a supervisor before
starting the five-minute trial. Tokens are private root-worker data, not exported
diagnostic information. The application root worker consumes CLI tokens privately
and returns only fixed action-result fields; modern reload checks/renews the
selection without killing the shared phone process.

The supervisor validates the complete owner/carrier inventory under the shared
global lock. It renews active owners, restores expired/interrupted owners, and
restores installation policy only after every owner has completed recovery. A
module disable/remove, invalid payload or interrupted installation journal enters
recovery. A restored owner is checked without regranting old prepared permissions.
An empty archived owner directory can be skipped only when its carrier directory
is also absent; untracked state prevents changes. Shared role-policy resources now
retain the first baseline across overlapping owners and restore it at final release.
Synthetic pending-peer tests passed; this is not proof of actual two-active-SIM safety.

Preparation/recovery require all configured phones idle. They serialize on the
installation lock and production carrier controller's global lock. Recovery
requires every tracked slot/sub carrier transaction to be RESTORED first, so
another card's selected providers are not deprived of permissions. Changed
package UIDs/APK hashes/builds refuse recovery. An externally revoked original
grant, removed original SMS exemption, or a different external IPsec mode also
refuses recovery. Only grants added by preparation are revoked; original grant
bits/flags, exemption and recorded AppOp are checked after recovery. This does
not claim an inventory restoration of unrelated AppOps or other application policy.

Before preparation, `control.sh` publishes recovery under the global carrier lock.
Complete helper/script/checksum generations are copied and synced to a private
staging directory, atomically moved to their immutable generation directory, then
selected by one atomic `current` pointer. An incomplete staging operation does
not replace the previous selected generation. The independent dispatcher and
service.d entry are published before permission changes. A different generation
is refused while a baseline is pending; the restored baseline is verified first.
Old resident ticks stop before touching owners after the selected generation
changes. The resident lock records its generation, so a new helper does not count
an old resident as ready. On boot the independent entry starts supervision for a
saved installation/owner journal, including an interrupted preparation or invalid
enabled module, and attempts guarded restoration when recovery is required.
`uninstall.sh` also attempts restoration. Failed recovery retains the record;
module deletion is not assumed to stop because an uninstall script failed. If
the matching privileged apps disappear with the mount, restoration is refused
rather than granting to another UID. Recovery hooks and real disable/remove/reboot
behavior have not yet run on a modern Magisk device. Calling the bare Java CLI
directly does not publish the shell recovery hook; normal installation uses
`control.sh`/`service.sh`.

Supervisor startup now uses an overall monotonic uptime deadline of 60 seconds,
including its initial readiness observation. A final already-started 25-second
probe can finish beyond that deadline; it cannot launch another probe afterward.
This avoids treating sixty potentially 25-second probes as a sixty-second wait.
The shell contract tests exercise the actual readiness body with slow failing
probes, an already-ready resident and unavailable time observation. They suppress
only daemon-launch redirection and mock the daemon/probe/clock boundaries; they
do not prove production boot timing on a modern Magisk device.

## Actual scoped tests

The [tool0.9.2 bound-service batch](../runtime/reports/20261006-bound-iwlan/README.md)
passed 49 stages on each of API31/API34/API35 with matching final helper/module
bytes. It also checks that QNS-only selection preserves a denied, unselected
IWLAN IPsec AppOp while full selection is refused. Installation preparation
and complete APK/UID/privileged-identity guards remain strict. These fixtures
do not execute Magisk mounting or a real carrier registration.

The owner/supervisor integration subsequently passed 33 stages per version on
the same owned API33/API36 guests, including enabled-module APK loss, pending
helper upgrade refusal, atomic generation handoff, inventory refusal, expiration,
retention, disabled module recovery and original policy repeat checks. Evidence
and failure history are in
[the supervision report](../runtime/reports/20261006-selection-supervisor/README.md).
Those 33-stage reports invoke ticks directly. The later
[resident-process batch](../runtime/reports/20261006-resident-process/README.md)
also runs the shared production resident loop: 39 stages pass on each guest,
including real-time renewal, duplicate refusal, owned fixture SIGKILL/restart and
disable recovery. It still does not execute actual Magisk boot hooks or OS reboot.

The earlier installation-only evidence below retains its original scope.

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
