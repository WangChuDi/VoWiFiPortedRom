# VoWiFi diagnostic tool

Standalone Android application, package `dev.codex.vowifi.tool`. Select a SIM,
run a read-only check, and optionally control the separately built replacement
engine. The source and embedded engine retain GPL-2.0 licensing; see
[Android11 third-party notices](../android11/THIRD_PARTY.md).

## Checks

The root helper reports only selected-subscription metadata:

* Active SIM slot, subscription ID and operator, without phone number/IMSI/ICCID.
* Physical Wi-Fi and ePDG DNS resolution bound to that Wi-Fi network.
* Selected internet APN/type, user WFC and roaming switches, carrier policy and
  WLAN voice provisioning where the ROM exposes those interfaces.
* IMS networks matched by `TelephonyNetworkSpecifier` subscription ID, interface
  existence and P-CSCF count; a stale framework interface is shown explicitly.
* IMS registration transport and MMTEL voice/SMS capabilities through callbacks.
* Current replacement IWLAN process and its own child-opened metadata; this is
  not a universal IKE monitor for native or other replacement stacks.
* Latest SMS-dispatch capability observation from the current phone process,
  separated from advertised IMS capability. The check does not send an SMS.

DNS success does not prove UDP500/4500 reachability. The tool does not initiate
an IKE probe, SIM authentication, phone call or SMS. Unknown/inaccessible stages
are shown as unknown, not failed. Provisioning configuration is not proof that
the operator accepted registration. Registration and capabilities do not prove
successful audio or SMS delivery. The app requests no SMS/contact permissions
and exports no SMS bodies, authentication material or raw device dumps.

Root is requested by the tool's own UID: authorizing ADB Shell is insufficient.
MIUI's app_process needs an explicit telephony bootstrap and active-list slot
lookup. Root commands have bounded waits and IPC output is captured through a
pipe. A failed action clears old button state; inspect again before proceeding.

## Optional replacement

The APK embeds the already-built `vowifi-stack-api30-services.zip`. Install it
with the app's explicit button, reboot, inspect, then start a timed trial. The
tested engine's three components are offered together. Current profile:

`API30 / raphael / VOXI 23415 / slot index1 / subId1`.

Root revalidates the device, live SIM, subscription and operator immediately
before apply/enable/reload; UI results alone do not authorize a changed SIM.
Trials last at most five minutes and may roll back earlier on failure. Enable
retains an existing trial. Reload refuses active calls. Rollback restores the
original provider overrides/mode and resumes the preserved SMS companion.
Controller recovery remains visible when selecting an empty/unsupported SIM.

Individual component combinations and Android12–17 replacement engines are
not enabled. They require provider/routing compatibility validation; installing
the diagnostic APK does not establish it. No modem/vendor/APN replacement is
performed. See [engine build, validation and recovery](../android11/stack/README.md).

## Android versions and dual SIM

| Version | API | Read-only diagnostic | Bundled replacement |
| --- | --- | --- | --- |
| Android11 | 30 | Backend verified on the connected MIUI device, including empty SIM1 and active SIM2 | Live-tested on the fixed profile above |
| Android12 | 31 | Built on API30 methods; device validation pending | Disabled |
| Android12L | 32 | Device validation pending | Disabled |
| Android13 | 33 | Device validation pending; provisioning queried reflectively | Disabled |
| Android14 | 34 | Device validation pending | Disabled |
| Android15 | 35 | Device validation pending | Disabled |
| Android16 | 36 | Device validation pending | Disabled |
| Android17 | 37 | Device validation pending | Disabled |

Minimum SDK30 means the APK can be considered for Android11 and later; it is
not a claim of tested compatibility. OEM hidden-API/service/permission changes
can prevent root diagnostics; errors remain visible. Two slots are selectable,
and network matching never attributes by enumeration order. Only one active SIM
was available for live testing; simultaneous dual-SIM registration is untested.

AOSP retains default forwarding from the newer DataService setup overload to
the old override, and from subscription-aware ImsService methods to slot-only
methods. These are useful adaptation paths, not proof of end-to-end support:
[DataService source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/data/DataService.java),
[ImsService source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/ims/ImsService.java).
Main is a development branch, not a release-version test.

## Build

Build/sign the engine first using its documented Java17/ECJ/Kotlin/D8 toolchain.
Then set `JAVA`, `IMS_PORT_TOOLCHAIN`, `STACK_KEYSTORE` and
`STACK_KEYSTORE_PASSWORD`, and run `python build.py` here. The external keystore
must contain alias `stack`. No key is generated or committed by this builder.
Output is `out/vowifi-tool.apk`; generated files and private signing material
are ignored. The builder signs and verifies the APK and copies only the module
ZIP into assets. It never connects to a phone or installs anything.

On 2026-10-06 the APK was installed on the connected device and separately
authorized through Magisk's superuser UI. SIM1 empty-slot and SIM2 active-slot
checks were verified in the actual application. Switching SIM clears old
results. The reload button was exercised: it disables mutation buttons, waits
up to 45 seconds for registration/capability recovery and displays the refreshed
WLAN voice/SMS result. A bottom refresh button avoids scrolling back to the top.
These UI checks are separate from compilation and backend diagnostics.
