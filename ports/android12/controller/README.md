# Modern selected-subscription carrier transaction

This is the carrier-configuration layer used by the experimental modern owner
controller and installation/supervision module. Diagnostic tool0.9.0 now integrates
experimental modern controls; positive modern-device/dual-SIM evidence is pending.
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
only if the original baseline is still unchanged. PREPARING now resumes when its
saved original bundle, profile and exact persisted-file identity are complete and
still match the live original. A complete staged `config-before.bin.new` can be
atomically committed after those checks. Missing, malformed, conflicting or
identity-mismatched backups remain pending; recovery never samples a new baseline.

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

Actual OS reboot remains untested. The later
[preparation-interruption batch](../runtime/reports/20261006-preparing-interruption/README.md)
verifies actual core SIGKILL at two complete-snapshot PREPARING boundaries. The
resident batch below verifies a supervisor SIGKILL, a different boundary.
Ordinary exceptions attempt recovery; a forced timeout/kill is a failed
disposable test and cannot guarantee cleanup. Private phase evidence is retained.
It does not prove real carrier authentication, IMS registration, call/SMS/native
delivery, simultaneous active SIMs or real Magisk lifecycle behavior. The modern
installer/lease/watchdog backend is implemented below. Application integration
now exists in tool0.9.0; its modern root checks and refusal evidence are separate
from positive modern-device installation and carrier validation.

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
boot recovery or lifecycle archival; `ModernSelectionTransaction` and the
[module coordinator](../module/README.md) supply those experimental lifecycle paths.

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
device-wide lock. It is connected to fixed production commands and a resident
supervisor; the application selector remains pending. Its owner remains a ready23415
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
Restoration refuses authorization flag changes outside the role-owned default/system-fixed
bits and requires the original grants and authorization flags to remain stable for an
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

An incomplete core PREPARING snapshot is preserved rather than reused; a complete
saved original can now resume before the PREPARED phase commit. Modern
UI integration and shared role-policy ownership now exist; real multi-SIM operation
and real Magisk lifecycle remain pending. Watchdog/disable/remove/boot recovery is implemented by
`ModernSelectionSupervisor` and independently published recovery generations. See the
[owner-selection batch reports](../runtime/reports/20261006-owner-selection/README.md)
for actual per-version evidence and failed intermediate attempts.

## Actual resident process lifecycle

The production supervisor now exposes the same `runResident()` implementation
used by the separately guarded named-QEMU fixture. It holds its generation-bound
resident lock, records a private PID/boot/build/generation/start-time identity,
then runs the ordinary inventory tick every 15 seconds. A second worker waits
up to 60 seconds for the lock before refusing. The process journal does not
replace the held-lock and generation checks used for readiness.

The final [resident batch](../runtime/reports/20261006-resident-process/README.md)
passed 39 stages on each API33/API36 guest concurrently. It waits 105 real seconds
and proves renewal beyond the initial 90-second lease, refuses a second resident,
actually kills only the journal-matched nonce fixture, starts a new process over
the existing retained owner, then disables the fixture and verifies original
owner/installation policy before exit. Cleanup verifies every tracked host handle
is terminal before changing outer policy. The kill guard also compares Linux
process start time, UID and exact nonce command name; no host-provided PID is used.

Read-only audit contention retries only the exact validated
`modern-controller-busy` failure within bounded attempts/time. API31–33 shared-mode
observation retries a missing direct legacy field using fresh dumps, but still
refuses if it remains unknown. Conflicting/malformed fields are never converted
to success. The missing-field failure is retained in the report history; its
underlying cause was not established.

This proves a supervised process lifecycle on two single-fake-SIM emulators.
It does not prove phone-process kill/cache reconstruction, core PREPARING recovery,
OS reboot, real Magisk boot hooks, modem/USIM authentication, carrier call/SMS,
simultaneous dual SIMs or complete SDK31–37 runtime coverage.

## Shared original role policy across owners

`ModernSharedSelectedRoles` stores one original permission/AppOp baseline per
component group (IWLAN1, IMS4), independent of slot. New overlapping owners copy
that original baseline and reference its private resource identity rather than
resampling policy already changed for another owner. The same carrier lock protects
the complete owner inventory, resource journals, native apply intent and release.
QNS2 has no additional tracked role-policy resource.

An actual selected ACTIVE peer must pass current live/disk provider verification
before its recorded native role policy can be maintained. A pending peer retains
the first baseline without claiming a second selected SIM. The first owner can
restore its own carrier/lease while other holders remain; only the final holder
restores the resource's original policy. Resource RESTORING and per-owner released
markers permit recovery to continue without resampling. Fully restored resources
are atomically archived before a new cycle. Orphaned mutation intent, changed
references, duplicate tokens, foreign flags and untracked inventory are refused.

Exact Android13/16 `DataServiceManager` sources revoke unused service permissions
using only the current `mPhone` transport selection, without a cross-phone scan
in those methods:

| Release | Primary source | Decoded SHA256 |
|---|---|---|
| Android13 | [DataServiceManager](https://android.googlesource.com/platform/frameworks/opt/telephony/+/refs/tags/android-13.0.0_r1/src/java/com/android/internal/telephony/data/DataServiceManager.java) | `96eea9b70b46910016300148083b042e548329e2a5254adf7d610bd9284b4a08` |
| Android16 | [DataServiceManager](https://android.googlesource.com/platform/frameworks/opt/telephony/+/refs/tags/android-16.0.0_r1/src/java/com/android/internal/telephony/data/DataServiceManager.java) | `d9dd7ad8726139c8ea3beb81be9b74d8921a2a25c2d723fc32718fcf223ab22e` |

Both set IPsec/fine-location AppOps allowed on activation and errored on unused
service revocation. The coordinator accepts only baseline or those native modes;
an external actor choosing the exact same allowed/errored value cannot be
distinguished by value alone. It does not claim to detect every external writer.

The [final shared-role batch](../runtime/reports/20261006-shared-roles/README.md)
passed 46 stages per API33/API36 guest, including the resident lifecycle. Its peer
is a synthetic pending journal: no second active SIM or provider was fabricated.
Actual ACTIVE-peer permission repair still needs two-subscription device evidence.

## Actual core preparation interruption

The production snapshot writes/syncs a private staged original bundle, atomically
commits it, then writes PREPARED. Its separately guarded named-QEMU observer pauses
at the staged and committed boundaries. The new fixture actually SIGKILLs each
nonce/start-time-bound process, then opens the owner in a new process and resumes
through ordinary `ModernSelectionTransaction.restore()`. Neither boundary has yet
published a lease, acquired shared mode or applied carrier provider overrides.

The [full second batch](../runtime/reports/20261006-preparing-interruption/README.md)
passed 49 stages per API33/API36 guest concurrently, including the preceding
40-stage regression. It confirms the full original carrier/lease/role restoration,
repeat recovery, missing-bundle refusal and complete outer permission cleanup.
The deliberately withheld bundle is the same saved private original and is put
back only by the fixture; production does not reconstruct it from current config.
The final fixture handles a departed-process NIO read and explicitly verifies
cleanup checkpoints/kill fields; its targeted 18-stage follow-up passed both guests
without repeating the unchanged production restoration regression.
Earlier snapshot writes with missing profile/persistence metadata, incomplete data,
APPLYING-phase kills, removed-SIM recovery, OS reboot, actual Magisk lifecycle and
real two-active-SIM/modern carrier operation still require additional work.

## Recovery while the recorded SIM is absent

The owner controller has a separate recorded-recovery constructor. New trials,
verification, retention, renewal and final restoration confirmation still require
the exact ready23415 live slot/sub tuple. Recovery accepts absence only when a
nonnull, unambiguous active inventory contains neither that slot nor that sub,
and the slot's SIM state is explicitly ABSENT. Unknown/null inventory, a card
still starting or locked, a different card in the slot, or the same subscription
moved to another slot refuses mutation. The original private schema3 record is
required and validated under the same lock used by archival; no new owner or
carrier baseline is sampled.

Absent recovery restores only this owner's recorded original component lease.
It keeps phase RESTORING with `recovery.pending_owner=true`. It does not call the
carrier loader, copy/delete carrier files, release shared role policy or reset
shared IWLAN mode. The supervisor counts it as pending/waiting_owner, keeps the
installation baseline and continues processing other owners. The app returns
`action_completed=false` and explicitly displays that the original SIM must
return. The recorded token and raw identifiers never appear in app results.

This boundary follows the exact cached Android13/16 loader sources cited above:
`clearConfigForPhone` clears carrier/default app bundles, not either override
array. `overrideConfig` captures a phone ID before posting a Handler operation;
file identity is then looked up inside that operation. Neither an absent SIM nor
restored file bytes proves an unchanged, quiescent loader. The controller therefore
does not force a Binder clear through a departed sub or declare file-only recovery.

When the original slot/sub and file identity are live again, the supervisor or
app recovery resumes the saved carrier transaction, reloads the original layer,
compares the entire original bundle and releases the owner's role/mode references.
Only then does it remove the pending flag and enter RESTORED. A changed subscription
ID, a moved card, a changed build or malformed original remains pending; identity
migration is not implemented by this path.

`ModernDetachedOwnerEmulatorTrial` exercises the production restore method with
an isolated, explicitly injected absence observation in a fixed nonce/QEMU profile.
It checks missing-observation refusal, refused activation/retention/renewal/verify,
lease restoration, unchanged carrier/journal/native-role/peer-lease observations,
then a fresh handle's full original recovery with the real fake-SIM identity.
This is not actual SIM removal, a second active SIM, Magisk mounting or carrier
authentication proof. Runtime pass/failure evidence is separate from source tests.

## PermissionController sensitivity metadata

Raw permission flags remain in the original journal. Authorization-policy
comparison excludes only the two PackageManager `USER_SENSITIVE_WHEN_GRANTED`
and `USER_SENSITIVE_WHEN_DENIED` flags (API mask 768). The controller and the
system-UID broker never write either bit. AOSP identifies them as informational
metadata maintained by PermissionController, which can update them asynchronously
after package installation or upgrade. See the [framework flag definitions](https://android.googlesource.com/platform/frameworks/base/+/3fb46661abbb2802602ec6be1620cc01c7b2034c/services/permission/java/com/android/server/permission/access/permission/PermissionFlags.kt)
and [sensitivity update callback](https://android.googlesource.com/platform/frameworks/base/+/0698e320940c3c4ca971e38a16ea341f9fbb6ba5/core/java/android/permission/PermissionControllerService.java).

Actual grants, user/policy/system-fixed decisions, default grants, restricted-SMS
exemptions and every other flag remain checked. A role can restore only its
existing default/system-fixed mask after the original UID/APK checks. This is
authorization-policy restoration with current sensitivity metadata preserved,
not byte-exact restoration of every raw permission flag. Fixture reports explicitly
include the excluded informational mask; raw audit differences remain visible.
