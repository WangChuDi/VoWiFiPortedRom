# VoWiFi diagnostic tool

Standalone Android application, package `dev.codex.vowifi.tool`. Select a SIM,
run a read-only check, and optionally control the separately built replacement
engine. The source and embedded engine retain GPL-2.0 licensing; see
[Android11 third-party notices](../android11/THIRD_PARTY.md).

## Checks

The root helper reports only selected-subscription metadata:

* Active SIM slot, subscription ID, actual SIM state and operator, without phone
  number/IMSI/ICCID. An active subscription is not assumed to mean SIM_READY.
* Physical Wi-Fi and ePDG DNS resolution bound to that Wi-Fi network.
* Selected internet APN/type, user WFC and roaming switches, carrier policy and
  WLAN voice provisioning where the ROM exposes those interfaces.
* IMS networks matched by `TelephonyNetworkSpecifier` subscription ID, interface
  existence and P-CSCF count; a stale framework interface is shown explicitly.
  Newer/extended frameworks also use the subscription-ID set where accessible;
  absent/redacted attribution is reported separately, never assigned by order.
* IMS registration transport and MMTEL voice/SMS capabilities through callbacks.
* Bounded parallel process-local snapshots from the selected replacement IWLAN,
  QNS and IMS, with exact tuple/PID/boot/nonce/freshness validation. IWLAN reports
  locally held IKE/child/transforms and counts; IMS reports registration and
  per-generation REGISTER/SMS/ACK/media counters. Older services retain a clearly
  historical log fallback. See [status protocol](../android11/stack/TELEMETRY.md).
* Latest SMS-dispatch capability observation from the current phone process,
  separated from advertised IMS capability. The check does not send an SMS.
* Version0.9.3 compares phone/system-server process identities at the beginning
  and end of the check. Unknown observations remain unknown; identical samples
  do not prove that Binder, IMS or the carrier is healthy. A32-second watchdog
  returns completed-stage snapshots and the stalled stage if a system call blocks.
  Partial observations disable new changes while recorded-owner recovery remains
  available. PIDs, start times and raw process output are not returned to the UI.
* Version0.8.0 adds23 core IKE/EAP/IPsec/CarrierConfig signature lookups in the
  root app_process loader. Each lookup reports visible, absent, inaccessible or
  linkage error; explicit constructor/read/proposal aliases are checked in
  production order. No class initialization, method invocation or SIM auth occurs.
  These23 checks are a scoped pre-install aid, not the entire service ABI.
  Missing IKE classes in this loader may still be available to a service through
  its declared shared library. Visibility does not prove Binder permissions,
  package installation, service binding or carrier acceptance. Version0.9.0 now
  contains experimental modern replacement controls; the Android11 engine remains separate.
  See [runtime preflight evidence and loader limitation](RUNTIME-PREFLIGHT-20261006.md).

DNS success does not prove UDP500/4500 reachability. The tool does not initiate
an IKE probe, SIM authentication, phone call or SMS. Unknown/inaccessible stages
are shown as unknown, not failed. Provisioning configuration is not proof that
the operator accepted registration. Registration and capabilities do not prove
successful audio or SMS delivery. The app requests no SMS/contact permissions
and exports no SMS bodies, authentication material or raw device dumps.

Root is requested by the tool's own UID: authorizing ADB Shell is insufficient.
MIUI's app_process needs an explicit telephony bootstrap and active-list slot
lookup. Root commands have bounded waits and IPC output is captured through a
pipe. Where advertised by `su --help`, the tool uses the implementation's
global mount namespace (`-t 0` on the tested Magisk alpha, or `--mount-master`).
MIUI's app-data isolation otherwise hides the phone's CarrierConfig directory
even after acquiring root. Without either option, the default root path remains
available for diagnostics; the controller refuses a hidden backup directory
before mutation. A failed action clears old button state; inspect again before proceeding.
Optional telephony-bootstrap, controller and per-stage failures are isolated.
An inaccessible stage leaves the other results visible. Empty or inaccessible
subscriptions still permit physical-Wi-Fi/controller checks, while replacement
actions require a freshly observed SIM_READY in the tested profile.

## Optional replacement

The APK embeds the independently built API30 and SDK31–37 engine ZIPs, with
compiled artifact digests. The API30 asset is `vowifi-stack-api30-services.zip`. Install it
with the app's explicit button, reboot, inspect, then start a timed trial. The
three components can be selected independently for a timed experiment. With
controller0.9.0, any nonempty selection can be retained after original/selected
provider and persistence verification. The full combination and IWLAN+IMS with
original QNS configuration have live voice/SMS evidence on this profile;
other mixed combinations still require real interoperability tests. Restore
the current transaction before changing its selection. Current profile:

`API30 / raphael / VOXI 23415 / slot index1 / subId1`.

Version 0.3.0 passes the selected slot and freshly observed subscription ID to
the controller. New transactions record that owner explicitly. Enable/reload
require selecting the owner; recovery always names and targets the recorded
owner, even while viewing another slot. The API30 device profile can accept an
active VOXI in another slot, but positive live validation currently covers only
slot1/sub1. Version 0.5.0 uses selected status plus an active-owner list, allowing
separate transactions through controller 0.7.0. Simultaneous live dual-card
registration remains untested. The app keeps mutation controls disabled when paired
with an older controller that lacks the explicit-identity protocol.

Version 0.4.0 embeds controller 0.6.0, which backs up and restores only the
selected subscription's confirmed persisted override file. A private identity
record and original-file hash prevent another card's file from being restored
as this one. Interrupted preparation archives evidence without clearing live
providers; interrupted staged restoration preserves the live target. Unknown
filename layouts remain refused. Controller 0.7.0 adds separate owner directories,
shared mode/companion coordination, token-scoped workers, atomic directory archival
and boot recovery for all owners. A recovery SIM selector explicitly names the
transaction being restored, including when inspecting an empty slot. Isolated
two-owner controller tests passed; there is still only one actual active card for
live testing. See [ownership and remaining work](../android11/stack/TRANSACTIONS.md).

Root revalidates the device, live SIM, subscription and operator immediately
before apply/enable/reload; UI results alone do not authorize a changed SIM.
Trials last at most five minutes and may roll back earlier on failure. Enable
retains an existing trial. Reload refuses active calls. Rollback restores the
original provider overrides/mode and resumes the preserved SMS companion when
no replacement-IMS owner remains.
Controller recovery remains visible when selecting an empty/unsupported SIM.

Partial combinations preserve unselected effective provider values, and change
global operation mode only when IWLAN is selected. They are compatibility
experiments whose interoperability must be tested individually. The actual
IMS-only UI trial did not register WLAN. IWLAN+IMS retaining original QNS
configuration later passed reboot recovery, native SMS/inbox/notification and
191 calling, following an explicit idle reload when switching selections.
See [optional persistence evidence](../android11/stack/OPTIONAL-PERSISTENCE-20261006.md).
Version0.9.0 adds experimental Android12–17 component controls and the modern
root worker, with read-only modern diagnostics and fixed mutation guards verified
on API33/API36. Modern device/provider/routing/carrier compatibility still needs
validation; installing the diagnostic APK does not establish it. No modem/vendor/APN replacement is
performed. See [engine build, validation and recovery](../android11/stack/README.md).

## Android versions and dual SIM

| Version | API | Read-only diagnostic | Bundled replacement |
| --- | --- | --- | --- |
| Android11 | 30 | Backend verified on the connected MIUI device, including empty SIM1 and active SIM2 | Live-tested on the fixed profile above |
| Android12 | 31 | Tool0.9.2 compiled root diagnosis/refusal verified on owned emulator; fake-SIM IMS callbacks absent | Experimental controls; bindings, QNS restoration and 49 helper lifecycle stages passed; positive app/carrier trial pending |
| Android12L | 32 | Tool0.9.2 compiled root diagnosis/refusal verified on owned emulator; fake-SIM IMS callbacks absent | Experimental controls; bindings, QNS restoration and 49 helper lifecycle stages passed; positive app/carrier trial pending |
| Android13 | 33 | Root checks/refusal paths verified on named emulator; carrier callbacks unavailable in that fake-SIM run | Experimental controls; lifecycle helper verified, positive application/carrier trial pending |
| Android14 | 34 | Tool0.9.2 compiled root diagnosis/refusal verified on owned emulator; fake-SIM IMS callbacks absent | Experimental controls; bindings, QNS restoration and 49 helper lifecycle stages passed; positive app/carrier trial pending |
| Android15 | 35 | Tool0.9.2 compiled root diagnosis/refusal verified on owned emulator; fake-SIM IMS callbacks absent | Experimental controls; bindings, QNS restoration and 49 helper lifecycle stages passed; positive app/carrier trial pending |
| Android16 | 36 | Root checks/refusal paths verified on named emulator; carrier callbacks unavailable in that fake-SIM run | Experimental controls; lifecycle helper verified, positive application/carrier trial pending |
| Android17 | 37 | Tool0.9.2 compiled root read-only/refusal checks passed on owned official emulator; positive app-UID UI actions pending | Runtime bindings/QNS restoration passed; full helper lifecycle failed at selection-reopen and cleanup unconfirmed; no carrier proof |

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
`NetworkCapabilities.getSubscriptionIds()` is public from API35 / U extension12
and permission-filtered. The root helper attempts reflection on API31+ for older
hidden implementations and treats absent/empty sets as attribution unavailable:
[official API contract](https://developer.android.com/reference/android/net/NetworkCapabilities#getSubscriptionIds()).

## Build

Build/sign both engine bundles first using their documented Java17/ECJ/Kotlin/D8 toolchains.
Then set `JAVA`, `IMS_PORT_TOOLCHAIN`, `STACK_KEYSTORE` and
`STACK_KEYSTORE_PASSWORD`, and run `python build.py` here. The external keystore
must contain alias `stack`. No key is generated or committed by this builder.
Output is `out/vowifi-tool.apk`; generated files and private signing material
are ignored. The builder signs and verifies the APK, generates engine identities
and copies the two module ZIPs into assets. It never connects to a phone or installs anything.
`python validate.py --build --unsigned` permits source/DEX/metadata/assets checks
without signing material and explicitly reports signature verification false.

For one consolidated release batch, run `python validate.py --build` with the
same environment. It builds/signs once, executes the production lookup contracts
and verifies actual APK SDK/version/signature plus both byte-exact embedded engines.
`--artifacts-only` permits checking an already-built batch without repeating
passed contracts. Validation never rebuilds the engine or installs on a device.
Inspect the resulting `out/validation.json`; its device flag remains false until
separate live evidence is recorded. This keeps compile, fixture and live evidence
distinct while avoiding repeated build/check cycles after every small edit.

On 2026-10-06 the APK was installed on the connected device and separately
authorized through Magisk's superuser UI. SIM1 empty-slot and SIM2 active-slot
checks were verified in the actual application. Switching SIM clears old
results. The reload button was exercised: it disables mutation buttons, waits
up to 45 seconds for registration/capability recovery and displays the refreshed
WLAN voice/SMS result. A bottom refresh button avoids scrolling back to the top.
These UI checks are separate from compilation and backend diagnostics.

The 0.1.1 update was rebuilt/signed, installed with the same application identity,
and checked through the application's own root invocation. SIM2 reports actual
state READY, one matching IMS network on an existing IPsec interface, two P-CSCF
addresses and WLAN voice/SMS capabilities. Empty SIM1 retains physical Wi-Fi and
controller status without claiming SIM2's IMS network. Android12–17 behavior and
inaccessible-interface branches still need device validation; this source/API
review does not enable the replacement engine on those versions.

The 0.2.0 UI was separately exercised through its own root invocation: native
rollback, IMS-only selection, a second native rollback, full selection and
persistence, and installation of the embedded 0.4.0 module update. Polling no
longer launches a full diagnostic with a shortened final timeout; an accepted
controller action and an incomplete observation are reported separately. The
root error panel contains only whitelisted status metadata. The eight pinned
framework samples resolve all 301 Android/internal references from the services
and tool; reflection, hidden APIs, permissions and hardware are separate checks.

The 0.3.0 tool was tested on the actual empty SIM1 and active SIM2. Empty-slot
inspection leaves reload/persistence disabled, names SIM2 as the controller
owner and offers explicit SIM2 recovery. That recovery restored original values;
selecting SIM2 then created a new explicit owner transaction and restored WLAN
voice/SMS registration. Real wrong-slot/wrong-subscription requests to controller
and carrier helper were refused with transaction/baseline/persistence and phone
PID unchanged. IKE close reasons are now inspected even without an IMS network,
using only bounded safe error identifiers; logs remain historical observations.

## Modern application integration, version0.9.0

The app now routes SDK31–37 to a fixed modern root worker for component trial,
retention, lease check/renewal and selected-owner recovery. Its modern reload button
does not kill/reload the phone process; it is labelled as a check/renew operation.
Only a freshly observed ready23415 SIM is eligible. `engine_supported` now means
the bundled guarded profile is available; it does not mean that device/carrier
validation is complete. Every modern screen labels the engine experimental.
Component actions additionally require the module helper/script and installed
privileged APK bytes to match this tool's compiled engine identities. Update
refuses incomplete status, pending installation or pending-owner inventory; core preparation/recovery
still revalidates ownership and uses its shared transaction lock.

Read-only modern inventory parses only fixed private records and CarrierConfig,
without constructing controller locks or creating state directories. Private
worker tokens are consumed inside root, not returned to the activity. Existing
Android11 controller routing and component behavior remain separate. Full build,
scope and per-version evidence are in
[the integration record](MODERN-INTEGRATION-20261006.md).

## Version0.9.1 recovery bundle

VersionCode13 / `0.9.1-diagnostic` packages the newer modern PREPARING recovery
helper without changing the API30 engine bytes or application action semantics.
Its modern helper passed the two actual snapshot SIGKILL boundaries as part of a
49-stage API33/API36 batch. The signed app passed the modern read-only/refusal
checks, was updated on the current MIUI phone, and still observes the full ACTIVE
API30 selection, WLAN IMS registration and advertised voice/SMS capability.
No new call or SMS was sent in that regression. See the
[version0.9.1 evidence](RECOVERY-BUNDLE-20261006.md). Positive modern application
selection, removed-SIM recovery, true Magisk boot lifecycle and dual active SIMs
remain pending; version0.9.0's integration report retains its historical hashes.

## Version0.9.2 bound-service bundle

VersionCode14 packages the corrected modern IWLAN bound DataService and selected-role
readiness checks. The same final helper/module passed 49 stages on each owned
API31/API34/API35 guest. The actual compiled app root backend passed concurrent
read-only diagnosis and mutation refusals on those three versions. The current
MIUI phone was updated to the signed app and still reports full API30 selection,
WLAN registration and voice/SMS capabilities; that regression sent no new traffic.
See [version0.9.2 identities, build and limits](BOUND-IWLAN-BUNDLE-20261006.md).
The identical signed tool/helper/module subsequently passed the scoped checks on
[API32](../android12/runtime/reports/20261006-api32/README.md) in a separate run.
The [API37 run](../android12/runtime/reports/20261007-api37/README.md) subsequently
passed runtime and compiled tool checks with identical artifacts; its full
selection/recovery lifecycle remains failed and pending.

## Version0.9.3 bounded diagnostic results

VersionCode15 preserves both embedded engine ZIPs byte for byte. The production
watchdog was exercised with a truly blocked child on the host and on the owned
API32 guest; each returned one partial JSON result after approximately32 seconds.
Completed diagnostic results and action refusals passed on API32. Concurrent
empty/active-slot root checks passed on the physical API30 phone and still observed
full replacement, WLAN registration and voice/SMS capabilities on the active card.
The signed app update was installed. The app's own-UID UI regression was blocked
by the phone's lock screen and is not counted as passed. No new call or SMS was
sent in this regression. See [source, artifact identities and scoped evidence](PLATFORM-HEALTH-20261007.md).

## Version0.9.5 recorded recovery and sensitivity metadata

VersionCode17 embeds modern module0.2.1 while retaining the API30 engine bytes.
Recorded modern recovery can withdraw an absent owner's component lease and
explicitly remain pending until the original SIM/file identity returns. The app
shows this waiting state without reporting completed rollback. Activation,
retention and renewal continue to require the live ready23415 owner. The
controller keeps the original carrier, role and mode journals throughout.

Authorization-policy recovery now preserves PermissionController's two
user-sensitivity metadata bits instead of treating their asynchronous update as
foreign authorization. Grants, user/admin decisions, restricted-SMS exemptions,
APK/UID ownership and other permission flags remain checked. Original raw flags
are retained, and test reports distinguish raw metadata from authorization state.
See [the controller semantics](../android12/controller/README.md#permissioncontroller-sensitivity-metadata)
and [current artifacts, scoped checks and retained failures](RECORDED-RECOVERY-20261007.md).

## Version0.9.6 native SMS support observation

VersionCode18 adds a separate selected-subscription query to the system SMS
Binder, independently of MMTEL capabilities. The active slot/sub is rechecked
after the query, and missing interfaces or failed queries remain unavailable.
The watchdog covers this stage. A reported capability does not prove actual
traffic, inbox delivery or notification.

The signed update passed concurrent physical empty/active-slot root checks and
API33/API36 read-only/refusal smoke. The existing API30 full stack passed one
native INFO SMS round trip, merged inbox delivery and notification. The latest
191 test reached SIP responses but no connected/media proof; CSeq attribution
and the488 rejection remain unresolved. See [changes, identities and explicit
limits](NATIVE-SMS-20261007.md) and [original API33 recovery](RECORDED-RECOVERY-API33-20261007.md).

## Version0.9.9 AKA, SMS readiness and dialog handling

VersionCode21 embeds newly built API30/modern IMS services. Bounded AKA
resynchronization, reliable-PRACK ordering, registered-capability replay and
outgoing early/final dialog routing are shared by both builds. Changed SDP offers
advance their origin version. The synthetic contract batch covers52 cases.

The temporary AKA/PRACK candidate passed one actual191 call and native SMS. The
persistent0.9.8 release passed reboot/native SMS/inbox/notification, but its final
call received UPDATE488. These distinct observations remain preserved. The
0.9.9 actual reboot and191 call passed. Its first SMS immediately after the call
failed before replacement IMS transmission; a separate retry after the cleanup
phone reload passed native delivery/notification. The first combined result
remains failed. See [exact changes and evidence limits](AKA-SMS-DIALOG-20261007.md).
