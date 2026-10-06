# Modern service runtime integration checks

The API31 IWLAN experiment now includes a separate root-only runtime provider.
It executes the shared23-signature probe **inside the IWLAN service APK's class
loader**, which declares the required `android.net.ipsec.ike` shared library.
The ABI lookup never initiates IKE/EAP/AKA, reads SIM identities or changes configuration.
The endpoint is `content://dev.codex.vowifi.iwlan.runtime`, method `abi`, with a
32-lowercase-hex nonce. Method `bindings` uses the same root gate and nonce to
request bounded explicit connections from the registered privileged application.
Its exported manifest requires privileged phone-state
permission; its implementation independently requires Binder UID0. Query/write
operations are refused. Snapshots contain fixed lookup metadata, package/PID,
nonce and monotonic sample time. The telemetry subscription endpoint is unchanged.
Method `guard` independently checks that a valid own-app IPC with privileged
phone-state permission is still refused after clearing the incoming root identity;
this checks the implementation UID gate, distinct from the shell permission gate.

`ModernRuntimeCheck` is a separate API31–37 root app_process helper. It records:

* Actual installed package/system/privileged flags and required permission grants.
* Root-loader and service-declared-library lookup results, keeping scopes separate.
  The service needs20IKE/EAP/IPsec lookups; the three hidden CarrierConfig members
  belong to the root helper and need not be exposed to an application class loader.
* Exact installed service action/export/permission declarations, plus privileged-app explicit
  bindings to data, network, QNS and IMS Binder interfaces with descriptor/liveness
  checks and bounded waits. Each binding is released, including on failure.
* Actual CarrierConfig Binder read through the production ABI resolver and active
  subscription count, without exporting subscriber identities or carrier dumps.

It does not create data providers, select a carrier override, authenticate a SIM,
register IMS, place calls or read/send SMS. The content CLI carries root requests;
the provider clears calling identity before using its registered app context for
binding and restores it in `finally`. A detached root app_process thread cannot
be treated as a registered ActivityManager client. Explicit binding is distinct from
the telephony framework automatically selecting/binding these packages. The
report keeps provider selection, carrier/call/SMS and dual-SIM proof false.

Build the related changes as one batch using the documented Java17/toolchain:

```sh
python ../build-runtime.py --services
```

This compiles the three modern APKs once, the isolated root helper once, then
checks actual compiled APK/bundle declarations, roles, SDKs and provenance.
The helper is `../out/runtime/runtime-check.zip`. It is separate from the
unsigned research bundle; building does not install or enable modern engines.
Existing API30 outputs and attributed upstream source remain protected by the
three-service builder's full inventory check. No API30 manifest/APK changes occur.

After an IWLAN-only source correction, `python ../build-runtime.py --iwlan`
recompiles that APK and the helper, retaining prior hash-verified QNS/IMS outputs
and marking the retained roles in the bundle manifest. It does not claim to freshly
rebuild their sources; use `--services` when their inputs change. A failed partial
build removes old manifest/bundle success before rebuilding.

The modern permission XML grants the IWLAN validation caller the privileged
IMS binding permission. DATA/NETWORK permissions on the tested API31 image are
platform-only and are not falsely whitelisted. Own-package IWLAN bindings need
no cross-package grant. Explicit package queries permit QNS/IMS visibility under
Android12's filtering. QNS reports `requires_platform_caller` for a client without
the platform permission; this is not relabeled as a failed QNS implementation.
The emulator-only
`ModernPermissionPrep` adds the fixed IMS SEND_SMS system restriction exemption;
ordinary `pm grant` alone does not remove its hard restriction. Its guard requires
root, SDK31–37 and `ro.kernel.qemu=1`; the host runner additionally checks the owned
AVD identity. This is test-environment preparation, not carrier SMS acceptance or
a production permission/installation profile for arbitrary devices.

`ModernFrameworkTrial` is a separate, bounded SDK31–37/emulator-only test. It requires
one non-VOXI test subscription, no persisted carrier override and a clean QNS
baseline. It temporarily writes only the QNS package override with
`persistent=false`, reads it back, and observes the current received service
binding from `com.android.phone` through the ActivityManager service record.
It clears the transient override in `finally`, compares the entire returned
CarrierConfig bundle with its original values and verifies that no persisted
override file was created. Run only in a fresh disposable owned emulator: it
is not a general recovery transaction for arbitrary pre-existing RAM overrides.
It validates a real modern Binder write/read/clear and a platform QNS connection,
not VOXI acceptance, QNS selection of IWLAN, or telephony's IMS/data selection.

Installing the three modern packages as privileged apps requires a separately
validated system/Magisk installation profile, signed packages, matching privileged
permissions and runtime grants/AppOps. An owned API31 Google-APIs emulator can
test that integration without changing the connected API30 phone. It cannot
provide a VOXI USIM/EAP-AKA or prove genuine carrier voice/SMS/dual-SIM behavior.

Source retains GPL-2.0. IMS remains the attributed phhusson port; see the modern
build README and Android11 THIRD_PARTY/source license. This runtime probe is an
integration addition, not a replacement of that implementation.

For parallel version checks, prepare/sign/install the same APKs once before
dispatching independent validators. Each emulator must be named
`CodexVoWiFiApi<SDK>` and each invocation uses its explicit serial, expected SDK
and separate output directory:

```sh
python check-emulator.py --adb /path/to/adb --serial emulator-5574 \
  --sdk 33 --avd-name CodexVoWiFiApi33 --output-dir /path/to/results/api33
```

`--no-push --stdout-only` permits a verifier to use the already-staged helper
without writing host files or pushing artifacts. Do not add `--framework-trial`
to a read-only check: that option temporarily changes the disposable emulator's
QNS selection, with the cleanup described above. No validator may target a real
phone; SDK/QEMU/owned-AVD/root guards precede the helper invocation. A new file
validation starts with `status=started`, replacing any prior successful summary;
only a complete new invocation writes `status=passed`. Failures write
`status=failed` and discard prior raw runtime/trial files at invocation start.

## Actual version evidence: 2026-10-06

Two independent agents concurrently ran the read-only validator on owned
Android13/API33 and Android16/API36 emulators. Both passed installed system/
privileged flags and permissions, all20 service-library lookups, IWLAN data/
network and IMS Binder connections, shell and privileged-nonroot refusal, and
actual CarrierConfig Binder reads. The preparation owner subsequently ran only
the additional QNS framework-selection checks on both emulators in parallel;
both observed a phone-process binding and restored the **entire** original
configuration with no persisted override. The final report-writer correction
was checked against failure fixtures and both existing emulator integrations.

The same signed IWLAN/QNS/IMS artifacts were used for both versions. The Google
APIs x86_64 images were API33 revision17 and API36 revision7 (extension17).
Their system apps were staged under `/system/priv-app/Api31{Iwlan,Qns,Ims}`;
the `Api31` folder prefix denotes the build profile, not the guest SDK.
API36 required a second overlayfs/verity preparation reboot before system
writes became available. The test preparation verifies writability and actual
installed paths/hashes instead of assuming that a successful `adb remount`
exit code means the filesystem is writable.

| Android | SDK | Current evidence |
|---|---:|---|
| 12 | 31 | Earlier isolated emulator integration and QNS restoration passed |
| 12L | 32 | Static271 references /22 superclass contracts passed; runtime not run |
| 13 | 33 | Independent parallel agent integration and QNS framework/restoration passed |
| 14 | 34 | Static271 references /22 superclass contracts passed; runtime not run |
| 15 | 35 | Static271 references /22 superclass contracts passed; runtime not run |
| 16 | 36 | Independent parallel agent integration and QNS framework/restoration passed |
| 17 sample | 37 | Static service/root-helper checks passed; matching official image not listed |

Safe results, artifact hashes and limitations are archived in
[the version matrix](reports/20261006/matrix.json) and its per-version JSON
files. API31's earlier check used an earlier helper revision; it is not claimed
as a fresh run of the final SDK31–37 helper. The Android17 Robolectric sample
does not establish Android17 ROM or release support. This batch made no changes
to the connected API30 phone. Carrier authentication, calls/SMS, native delivery,
dual active SIMs and a modern production install/controller profile remain
unverified by these emulator checks.

## Selected-subscription persistence continuation

The modern helper now includes read-only `ModernPersistenceCheck <slot> <sub>`
and a disposable `ModernPersistentFrameworkTrial`. The latter exercises the
[shared modern carrier transaction](../controller/README.md), using persistent
QNS selection, native-stream file readback, a reopened recovery transaction,
full configuration restoration and safe repeat restoration. It requires one
non-VOXI test subscription and an originally absent selected override file.
This is not a production installation/boot supervisor.

Use the already-prepared, rooted named emulator. This checker does not install
packages, authorize root, reboot or change physical devices:

```sh
python check-persistence-emulator.py --adb /path/to/adb --serial emulator-5574 \
  --sdk 33 --avd-name CodexVoWiFiApi33 --output-dir /path/to/results/api33
python check-persistence-emulator.py --adb /path/to/adb --serial emulator-5580 \
  --sdk 36 --avd-name CodexVoWiFiApi36 --output-dir /path/to/results/api36 \
  --persistent-trial
```

The default only checks the fresh test baseline using slot0/sub1, the test
emulators' tuple. `--persistent-trial` explicitly permits the QNS-only configuration
mutation/restoration; do not pass it to read-only scouts. `--no-push --stdout-only`
with the default check leaves host/device configuration unchanged. Both modes
verify qemu, SDK, exact owned AVD, root and the staged helper hash before running.
The selected trial report starts fresh; malformed/incomplete/wrong-version results
fail rather than keeping a prior success. Separate output directories prevent
concurrent version checks from overwriting each other.

The persistence continuation passed concurrently on API33 and API36, with the
cleaned baseline independently checked afterward. Safe results are in
[persistence reports](reports/20261006-persistence/matrix.json). Only the new root
helper was rebuilt; the three signed modern service APKs and API30 phone were
unchanged. Earlier root-helper static reference counts describe the earlier helper,
not this expanded carrier-transaction helper. Existing-original-XML/reboot/crash
recovery and real carrier/voice/SMS/dual-SIM proofs remain outside this test.

## Existing original file and selected-loader reload

The next continuation adds `ModernSeededPersistenceTrial`, a controlled fake-SIM
fixture that seeds an original file, loads it as a normal disk layer and uses
separate app_process invocations for all six stages. It verifies the production
snapshot/selection, refuses file-only restoration as complete, models the
FILE_RESTORING handoff left after copying the original, resumes, and invokes
`updateConfigForPhoneId(slot, "LOADED")` through the production idle/owner gate.
Full seeded and pristine bundle comparisons, repeat recovery, mask ownership and
phone PID continuity passed on Android13/API33 and Android16/API36 concurrently.

The first run failed full comparison because its freshly saved RAM baseline still
contained AOSP's serialization version entry. The corrected fixture loads that
baseline from disk before snapshot; the production transaction refuses the
unnormalized RAM layer instead of weakening full comparison. The initial failure
and successful final results are separately preserved in
[reload/recovery reports](reports/20261006-recovery/matrix.json).

```sh
python check-seeded-emulator.py --adb /path/to/adb --serial emulator-5574 \
  --sdk 33 --avd-name CodexVoWiFiApi33 --output-dir /path/to/results/api33
python check-modern-controller-guard.py --adb /path/to/adb --serial emulator-5580 \
  --sdk 36 --avd-name CodexVoWiFiApi36 --output-dir /path/to/results/api36
```

The seeded checker explicitly mutates/restores only the owned fake-SIM guest,
validates its staged helper hash and attempts outer-baseline cleanup in finally.
The guard checker is read-only and never pushes the helper. `--stdout-only` makes
that guard check suitable for a scout without host result writes. Failed cleanup
is a failed trial, not restoration proof. The production CLI itself remains
strictly ready23415 and root, without the fixture's non-VOXI mode.

The two-slot phone-idle parser has13 contracts alongside the12 baseline contracts.
These are controlled parser tests, not two active real SIMs. Production VOXI
commands, full modern installer/boot supervision/app UI, abrupt kill/OS reboot,
real IMS/call/SMS and simultaneous dual-SIM behavior remain unverified. This
continuation made no changes to the connected API30 phone or three modern APKs.

## Installation audit matrix and AppOp gap

`check-installation-matrix.py` checks explicitly selected owned rooted emulators
concurrently, without installing, granting, requesting bindings or changing
CarrierConfig. One invocation covers system/privileged installation, primary-user
permission grants, the fixed SMS system restriction exemption, the IWLAN IPsec
AppOp and current phone-process service bindings. Requested permissions and
service declarations alone do not count as grants or received phone bindings.
The output contains fixed metadata, never raw package/service dumps. Twelve host
contracts cover misleading requested permission lines, wrong/incomplete binding
clients and secondary user blocks. The initial parser mistook the package dump's
additional `User 0:` summary for a second installed user; it was corrected to
inspect actual installed-user declarations before recording final observations.

```sh
python check-installation-matrix.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --output /path/to/fresh-installation-matrix.json
```

Reports require a fresh canonical path, distinct SDKs/serials, SDK31–37,
`ro.kernel.qemu=1`, exact `CodexVoWiFiApi<SDK>` identity and root. `observed`
means the observations completed; use `installation_permission_profile_ready`
for the narrow installation-permission result. Neither means carrier readiness.
This audit does not repeat the declared-library runtime lookup or establish real
Magisk mounting, EAP/AKA, IMS registration, voice/SMS or dual active SIMs.

The new independent API33/API36 scout audits ran sequentially because the second
concurrent agent dispatch hit the thread limit. The consolidated device workers
ran concurrently. Both guests had the required installed grants and IMS SMS
exemption. API33's `MANAGE_IPSEC_TUNNELS` AppOp was `allow`; API36's was `deny`.
The owner corrected only that API36 emulator AppOp after verifying QEMU, SDK,
owned AVD, root, exact privileged APK path and the signed APK hash. The final
concurrent audit observed both permission profiles ready. Only QNS showed a
received binding from `com.android.phone`; IWLAN and IMS did not show active
bindings. The AppOp gap is an installation prerequisite, not proof of the cause
of those absent bindings. No phone, APK or carrier configuration was modified.
Before/after safe observations are in
[the installation matrix reports](reports/20261006-installation/final.json).

## Packaged installation and permission recovery

The [modern installation-stage module](../module/README.md) now includes the
signed services, privilege XML, root helper, SDK/profile-gated scripts and an
independent permission recovery entry. It is not yet the carrier-selection/boot
supervisor. New `ModernInstallationTransaction`/`ModernInstallationController`
prepare/recover fixed package grants, SMS system exemption and effective IPsec
AppOp with private UID/APK/build snapshots and carrier-lock coordination.

`check-installation-emulator.py` runs different guest SDKs concurrently and
uses the separate owned fake-SIM fixture. Both API33/API36 passed eight process
stages, external IPsec change refusal, complete outer fixed-policy/flag cleanup,
production nonroot/missing-module refusal, Android shell syntax and phone PID
continuity. Final helper/module hashes and limitations are in
[installation-stage reports](reports/20261006-installation-module/final.json).
Library/call/SMS proofs were not repeated; true Magisk mount/reboot/disable/remove,
modern provider lifecycle/application entry, real carrier behavior and dual active
SIM remain incomplete. The connected API30 phone and three signed APKs were unchanged.
