# Bound IWLAN and selected-role readiness: 2026-10-06

Binding the former modern IWLAN DataService on API34/API35 crashed its process
with `ForegroundServiceStartNotAllowedException` from `onCreate()`. The service
now keeps the lifetime supplied by its framework binding instead of calling
`startForeground()` for every binding. IWLAN versionCode33 / `0.3.1-api31-experiment`
retains target/minSDK31, the shared IKE library, exported service binding permissions
and root-only diagnostic guards. It drops the unused foreground-service permission.
This follows the [bound-service lifetime](https://developer.android.com/develop/background-work/services/bound-services)
and [background foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

The first corrected-service lifecycle batch then failed API34's QNS-only repeat
selection: the journal remained PREPARED with runtime grants and SMS exemption
present, but the unselected IWLAN IPsec AppOp was denied. The previous readiness
gate required that AppOp even for mask2/QNS-only selection. Readiness now checks
runtime grants, SMS exemption and IPsec only for selected roles, while validating
all three payloads, installed APK/UID identities and privileged grants. Installation
preparation and its public `ready()` retain the full mask7 checks. The exact origin
of the observed AppOp change has not been independently attributed.

The repeat fixture explicitly denies the owned test IWLAN's IPsec AppOp: mask7
must refuse without changing its owner record or that AppOp; mask2 must succeed
and preserve denial. It also retains old-token refusal and the previous carrier
history's RESTORED phase check. The original AppOp is restored in `finally`,
including if the setter throws. This adds assertions, not additional stage keys.

## Final artifacts and evidence

| Artifact | SHA-256 |
|---|---|
| Root helper | `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d` |
| Signed modern module | `de5826326df086f2694aae88a3973f817b1cc0f30e1425c6ace42721cf7f70de` |
| Signed IWLAN APK | `5ee8d67d5b9a53d98646c7d64cfdae0261a072dbca05d3c570c3c2d602340528` |
| Retained signed QNS APK | `798ac76899c8dea99b587f1e0b47fb64708fb238b3323d6f21f5a9bc74d11800` |
| Retained signed IMS APK | `4d645baf4c0f7a744e3cefac4190ddc83f977790451cd67c155b15ca0ad1a009` |

The final helper rebuild changed the fixture's cleanup boundary after the signed
service batch. It retained all three signed service APKs; QNS/IMS were also retained
across the earlier IWLAN rebuild. [Artifact identities](artifacts.json) record this
scope. The module's helper equals the local final helper byte for byte. APKs,
private keys/passwords and raw device output are excluded from these source reports.

| Android | SDK | Google APIs x86_64 image | Final lifecycle keys | Runtime/app evidence |
|---|---:|---|---:|---|
| 12 | 31 | revision12 | 49 passed | Bindings, 20 service lookups, QNS platform binding/full restoration; app root diagnosis/refusal |
| 14 | 34 | revision14, extension7 | 49 passed | Same scoped checks |
| 15 | 35 | revision9, extension13 | 49 passed | Same scoped checks |

[`final.json`](final.json) records three concurrent device workers and the same
final helper/module on all versions. Each has 40 base stage keys plus nine
`--preparing` keys, for 147 total. It verifies two actual guarded PREPARING SIGKILL
boundaries, tracked-process termination before cleanup, resumed snapshot recovery,
owner/provider/lease/resource restoration, complete outer fixed-permission cleanup
and unchanged phone PID. These process kills are not OS reboots. The runtime check
and QNS trial are separately archived as `<SDK>-runtime-check.json`,
`<SDK>-framework-trial.json` and `<SDK>-validation.json`.

[`final-cleaned-installation.json`](final-cleaned-installation.json) is an additional
read-only audit after the lifecycle batch. All three guests retain the installed
privileged grants and IMS SMS exemption. It observes IPsec `allow` on API31 and
`deny` on API34/API35; consequently only API31's full permission profile is ready
at that observation. This does not contradict the tested restoration to each
fixture's baseline or establish current full-selection readiness on API34/API35.
Do not silently regrant policy to make a restoration audit green. Only QNS has
an observed phone-process binding in this audit; explicit earlier IWLAN/IMS
connections do not mean those remain selected by the platform.

[`host-contracts.json`](host-contracts.json) separately records 60 controlled
production IWLAN assertions, API30/31 type isolation and actual modern compiled
overload/DEX/minSDK boundaries. Those host contracts do not exercise Android
Binder, carrier authentication or media/SMS delivery.

## Historical attempts

* `<SDK>-current-runtime-check.json` / validation: before the DataService correction,
  runtime package/ABI checks passed but binding failed. The original checker exported
  only `IOException`, not the full crash. The scoped emulator observation identified
  the foreground-service exception; no raw crash dump is published.
* `<SDK>-bound-runtime-check.json` / validation: the corrected DataService bound, but
  immediately after boot the test subscription or CarrierConfig was not yet ready.
  `sys.boot_completed=1` alone does not establish telephony readiness. The later final
  runtime checks separately passed; the early failures are preserved.
* [`before-component-readiness-fix.json`](before-component-readiness-fix.json):
  API34 failed after 16 recorded keys, API35 passed 49; cleanup completed. Its
  helper/module are `2fde9f1daea6d69706a5b49e375ac039eedf6cac7949c63c6cfe206b676bf05b` /
  `baec4ae86454677f19dd55b43c3bf4efed49d2dbb759fad9c351339a66cafcbe`.
* [`before-fixture-finally-fix.json`](before-fixture-finally-fix.json): both API34/35
  passed 49 after selected-role readiness, before moving the fixture setter inside
  `try`. Its helper/module are `b5a81b287e08c16a6df2c78372557580b8a9ccb59de693550a95d7b766610cc7` /
  `52b651844c6ed48cc8ccfea178eb1db8a09818105490e222aa95f3ca7b39ca42`.
* [`api31-prior-helper.json`](api31-prior-helper.json) passed 49 keys using the prior
  `5eb7925a...` helper / `ace364cc...` module. It is not final-helper evidence.

## Reproducible owned-AVD preparation

`../../prepare-emulator.py` takes an explicitly named rooted/writable-system QEMU
guest and the already signed fixed module. It verifies SDK31–37, exact
`CodexVoWiFiApi<SDK>` identity, fixed ZIP inventory/digests/profile and byte-exact
current helper. It stages only the fixed three APKs and privileged permission XML,
checks installed paths/hashes after reboot and requests the fixed runtime
grants/SMS exemption/IPsec AppOp. It never runs module scripts, downloads APKs,
reads signing keys, selects a carrier or accepts a physical-device serial.

From `ports/android12/runtime`, using one guest at a time for preparation:

```sh
python prepare-emulator.py --adb /path/to/adb --sdk 34 --serial emulator-5576 \
  --module /absolute/path/to/modern-services-installation-stage.zip \
  --output /absolute/path/to/fresh-api34-preparation.json
python check-emulator.py --adb /path/to/adb --sdk 34 --serial emulator-5576 \
  --avd-name CodexVoWiFiApi34 --framework-trial \
  --output-dir /absolute/path/to/fresh-api34-runtime
python check-installation-emulator.py --adb /path/to/adb \
  --guest 31:emulator-5570 --guest 34:emulator-5576 --guest 35:emulator-5578 \
  --module /absolute/path/to/modern-services-installation-stage.zip --preparing \
  --output /absolute/path/to/fresh-final-lifecycle.json
```

Boot completion may precede fake-SIM/CarrierConfig readiness; confirm readiness
before launching validators. Preparation reports installation byte identity and
requested policy completion; `permission_profile_independently_verified=false`
requires a separate read-only audit. The committed preparation reports used earlier
helpers/modules: API34/API35 used `2fde9f1d...` / `baec4ae8...`; API31 used
`b5a81b28...` / `52b65184...`. All installed the same final service APK bytes.
The final validators separately staged the `ea54aa8d...` helper. `Api31` privileged
folder names refer to the build profile, not to the guest's Android version.

Temporary staging uses a fixed owned parent and rechecks canonical path, direct
parent and prefix immediately before recursive cleanup. Changed ownership refuses
cleanup and retains evidence. This is not a proof against every hostile filesystem
race. No staging directory or signing material is committed.

## Remaining scope

Modern Magisk mount/boot/disable/remove, real USIM/EAP-AKA and IMS carrier selection,
voice/audio/SMS/notifications, actual dual-active-SIM operation and runtime SDK32/37
remain unverified. SDK33/36 have separate earlier reports, not fresh runs of these
final service bytes. This batch leaves the API30 phone engine unchanged; its
tool0.9.2 current-state check is [separately documented](../../../../diagnostic-app/BOUND-IWLAN-BUNDLE-20261006.md).
IMS keeps the attributed phhusson source and GPL-2.0 license in the module and
[Android11 notices](../../../../android11/THIRD_PARTY.md).
