# Modern selected-subscription carrier transaction

This is the carrier-configuration layer for a future modern controller, not a
complete installer, boot supervisor or enabled diagnostic-app replacement engine.
The Android11 controller/profile and its working installed APKs remain separate.
The modern research APK bundle still does not contain an installer/controller.

## Why modern persistence needs a separate implementation

The exact AOSP Android12,13 and16 release loaders maintain both a temporary RAM
override and a persisted override. `overrideConfig(..., true)` changes both;
passing null clears both and schedules removal of the selected persisted file.
Configuration merging includes these layers. Saving/removal and phone updates
are asynchronous, so a Binder return is not evidence of disk persistence.

These loaders serialize the selected file through `PersistableBundle.writeToStream`
and read it with `readFromStream`, with a package-version entry. The new modern
reader uses the platform stream API; it does not reuse the API30 XML parser.
The file identity remains carrier package + ICCID + specific carrier ID, under
the phone application's device-protected files directory. Raw identities,
filenames, bundles and SMS data are not exported in runtime reports.

Exact primary release sources inspected:

| Android | Loader source | Decoded source SHA256 |
|---|---|---|
| 12 | [android-12.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-12.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `afb1558fdd2786f0602c6ff8aea956f5989d747313b5f79dabacc5e209c4e479` |
| 13 | [android-13.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-13.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `c5eb214ce3a84358ed6b112f44340c0b4d923d10c77bd5faf18f429dfc3b3e70` |
| 16 | [android-16.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-16.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `cdfef33ffe9584d3f2da705b781b77dbb8b4107593c49ddbaf6e922b2454c5a0` |

OEM changes and other modern releases require their own integration evidence.
The root guard accepts SDK31–37; that range is not a claim of runtime validation
on every SDK or of Android17 ROM support.

## Implemented transaction

`ModernProviderTransaction` shares the production subscription-target and exact
file snapshot/restore implementation with the API30 controller. Production
ownership requires an active, ready VOXI/Vodafone23415 subscription at the explicit
slot/sub tuple; the separate test profile requires a non-VOXI disposable emulator.
Private paths reject aliases, symlinks and non-files. A controller-wide file lock
serializes these transactions. Recovery refuses a changed SDK/build fingerprint
or selected persistence identity.

Preparation requires an already-loaded carrier bundle, an empty temporary layer
and no untracked replacement provider. It snapshots the entire effective bundle
and the exact selected persisted file (including its prior absence). Unknown dump
formats and nonempty RAM overrides are refused because their prior contents
cannot be reconstructed losslessly from a text dump.

The AOSP save routine inserts `__carrier_config_package_version__` into its RAM
bundle, while the read routine removes that serialization entry. Preparation
refuses a baseline still containing it; otherwise exact restoration after a
disk reload could appear different. The existing-file fixture loads its seeded
original from disk before the production snapshot and observes this difference
on both tested SDKs. Effective carrier values are never silently dropped from
the saved baseline or its full comparison.

Masks are IWLAN=1, QNS=2, IMS=4. Apply checks the unchanged baseline and original
file, then writes the selected provider keys with persistent=true. Success
requires both live readback and the selected native-stream file to match.

Phases are persisted before mutations: PREPARING, PREPARED, APPLYING, ACTIVE,
CLEARING, FILE_RESTORING, RESTORED_FILE and RESTORED. Restoration clears the loader's
two override layers and waits for deletion of the selected file and an empty
temporary layer before restoring the original XML. FILE_RESTORING is committed
before writing the original file; resumption at this phase never repeats loader
clearing against an already restored file. RESTORED_FILE/RESTORED retries verify
the original file without deleting it. An unused PREPARED transaction can finish
only if the original baseline is still unchanged. PREPARING remains refused and
retained for future supervisor-controlled archival; partial backups are not reused.

If the original XML existed, restoring it also requires the loader to consume
that original layer again. `reloadRestored()` checks every configured modem's
observed call state, revalidates the owner and calls the selected phone's
`updateConfigForPhoneId(slot, "LOADED")`. It polls full confirmation without
killing the shared phone process. The AOSP Android13/16 source routes this API
to the selected phone's persistent XML reader; actual emulator tests confirm
the same phone PID across the entire trial. Confirmation compares
the entire original bundle and requires an empty temporary layer, rather than
masking restoration by applying a full bundle as a new temporary override.

## Actual tests and remaining work

On 2026-10-06, the preparation owner ran the same newly built helper concurrently
on owned Android13/API33 and Android16/API36 Google-APIs emulators. Both used a
fresh absent selected-override baseline and a single non-VOXI test SIM. The QNS-only
persistent trial passed live/disk confirmation, restoration by a reopened
transaction, full original bundle comparison, absent selected file and safe repeat
restoration. Independent read-only agents then checked each version's cleaned
baseline and native cache stream reader. Those final agent checks ran sequentially
because this conversation temporarily reached its agent limit; the mutation
trials were concurrent. The earlier three-service runtime checks used simultaneous
version agents as documented in the runtime README.

The parser's12 host contracts include null/empty/nonempty/array layers, selection
among different slots, CRLF, duplicate/malformed/missing layers and unterminated
input. They caught and fixed a trailing-blank-line split error. The report checker
has21 contracts for incomplete proof, wrong-version proof and stale success after
refusing a physical-device serial. These checks do not operate on a phone.

The follow-up existing-file fixture also passed on API33/API36 concurrently.
Each prepare/apply/restore/arm/resume/cleanup stage runs in a separate app_process.
It uses a synthetic persisted marker as the original layer, proves that file-only
restoration remains unconfirmed, models FILE_RESTORING after the file was already
copied, then resumes and reloads through the production method. It compares the
entire seeded original bundle, tests repeat restore and restores the entire
pristine pre-test bundle with no selected override. It also verifies the recorded
mask and rejects a different mask. The interruption is simulated persisted state;
no actual abrupt kill or OS reboot was performed.

The phone-idle parser has13 host contracts, including both slots idle, another
slot ringing/offhook, missing/duplicate states, unknown values and incomplete
phone inventory. It requires an observed state for every active modem, including
empty slots; unknown observations refuse a reload rather than defaulting to idle.

Actual OS reboot/externally killed helper recovery remains untested.
Ordinary exceptions attempt recovery; a forced timeout/kill is a failed
disposable test and cannot guarantee cleanup. Private phase evidence is retained.
It does not prove real carrier authentication, IMS registration, call/SMS/native
delivery, simultaneous active SIMs or a production installer/lease/watchdog/UI.
All those remain separate work; modern buttons in the diagnostic app stay disabled.

## Production command backend

`ModernCarrierController` now exposes this carrier layer to a future supervisor
and application root worker. It accepts root, SDK31–37, an explicit ready23415
slot/sub owner and fixed commands; it has no emulator/test-owner override.
The private state is exactly
`/data/adb/codex_vowifi_stack_modern/transactions/slot-N-sub-S`.

```sh
CLASSPATH=/path/to/runtime-check.zip app_process /system/bin \
  ModernCarrierController ACTION SLOT SUB [MASK]
```

| Action | Result |
|---|---|
| owner-check | Read-only profile/owner check; no transaction directory creation |
| snapshot | Prepare a clean disk-loaded original bundle and selected file |
| apply MASK | Require idle phones, select mask1–7 and verify live/disk plus recorded mask |
| verify MASK | Recheck ACTIVE phase, exact recorded mask, build/owner and live/disk selections |
| restore | Require idle phones, resume original-file restore, reload if needed, compare full bundle |
| confirm-restored | Recheck original file, full original bundle and empty temporary layer |
| status | Fixed phase/transaction presence only; no subscriber identity or carrier dump |

All successful commands still report installer/supervisor/carrier/dual-SIM proof
false. The completed API33/API36 guard suite checks actual nonroot, non-VOXI and
invalid-mask refusals with transaction-inventory presence unchanged. It does not
prove a successful production VOXI command on a real modern phone. This backend
does not own APK installation, IWLAN operation mode, companion conflicts, leases,
boot recovery or lifecycle archival; the future coordinator must supply those.

Build and run the parser/report contracts:

```sh
python ../build-runtime.py
python test-baseline.py
python ../runtime/test-persistence-report.py
```

See [runtime commands and reports](../runtime/README.md) for guarded emulator
execution. New sources retain GPL-2.0. The attributed phhusson/ims source and
license remain under the Android11 port and are not replaced by this carrier layer.

## Owner selection coordinator

`ModernSelectionTransaction` now coordinates the carrier transaction, prepared
installation, per-slot component lease and shared IWLAN resource under the same
device-wide lock. This backend is not yet connected to a production command,
boot watchdog or application selector. Its production owner remains a ready23415
SIM; disposable non-VOXI fixtures have separate paths and QEMU/AVD guards.

Each private schema3 owner record keeps its original slot lease settings,
component mask, unexported token, build and subscription ownership, boot count,
five-minute trial deadline, retention choice and shared-mode ownership. The
90-second component lease is published with `until=0` first and a journaled
intent before updating the other keys. Reopening can finish a partial publication;
foreign settings and stale tokens are refused. Expiry restores a trial, while
retention permits renewal. A new cycle archives the fully restored original
carrier snapshot instead of overwriting it.

The shared IWLAN journal separately records property changes and pending phone
manager refresh. Resuming a refresh uses the previously observed phone PID and
boot count to avoid a second kill if reconstruction already happened. Original
property presence and value are retained; another recorded IWLAN owner prevents
release of the shared mode. The legacy/resetprop/cache-refresh branches and real
two-SIM ownership remain untested; the default-mode fixture does not exercise them.

Root app_process is not a registered ActivityManager application thread. The
fixed `ModernRootSettings` wrapper uses the platform settings CLI for only the
component-lease keys and boot count, preserving absent and literal-null values.
It does not accept arbitrary settings names or shell command strings.

Telephony provider selection also changes native role permission grants and
flags. `ModernSelectedPermissions` records only the selected fixed IWLAN/IMS
role permissions and journals the apply intent before changing providers.
Restoration refuses flag changes outside the role-owned default/system-fixed
bits and requires the entire original grant/flag state to remain stable for an
observed interval. This interval is bounded observation, not an acknowledgement
that no future OEM callback can occur. On the tested Android13 image, root UID0
could not clear the role's SYSTEM_FIXED bit; the fixed system-UID broker restores
only three hard-coded package/permission pairs after UID and privileged APK hash
checks. Only root-owned code is staged for that child; private baselines are not
made readable. Real Magisk `su 1000` and SELinux behavior still need device evidence.

The selected IWLAN role also snapshots and restores its effective IPsec and fine
location AppOps. [AOSP DataServiceManager](https://android.googlesource.com/platform/frameworks/opt/telephony/+/ee88fa09b5e59a3960ba0c096164c2e803b90c2f/src/java/com/android/internal/telephony/data/DataServiceManager.java)
sets both operations to allowed on activation and errored on deactivation.
Restoration accepts only the original or those role-produced values; other policy
changes are refused. These AppOps participate in the stable full-role comparison.
The earlier prototype owner schemas are not silently rebaselined or migrated.

An incomplete core PREPARING snapshot is preserved rather than reused. Modern
watchdog/disable/remove/boot recovery and UI integration remain pending. See the
[owner-selection batch reports](../runtime/reports/20261006-owner-selection/README.md)
for actual per-version evidence and failed intermediate attempts.
