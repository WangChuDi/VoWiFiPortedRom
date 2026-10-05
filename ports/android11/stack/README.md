# Android 11 IWLAN / QNS / IMS replacement

This experimental stack replaces WLAN data/network services, QNS and MMTEL IMS
on **raphael, MIUI V12.5.1.0.RFKMIXM, API30, VOXI 23415, SIM slot index 1**.
It builds three privileged APKs and a reversible Magisk module. It does not
replace modem firmware, vendor libraries, APNs or the IPsec APEX.

The working SMS companion is preserved at tag
`android11-companion-0.2.0-baseline`, commit
`ccd2aa0b54cdad5d7d357a83b594f55402ab944f`. The controller pauses the companion
while replacements are selected and resumes it after the last owner rolls back. Private snapshots
stay outside this repository.

## Verified on the device, 2026-10-05

| Stage | Actual result |
| --- | --- |
| QNS | Framework binds the new provider and selects IMS over IWLAN |
| IWLAN | New APK performs SIM EAP-AKA, establishes its own ePDG IKE/child session, obtains IPv4 and two P-CSCF addresses |
| Routing | New DataService returns an IPsec interface; telephony creates the IMS NetworkAgent |
| IMS | SIP 200; Android reports transport WLAN and voice/SMS capabilities |
| Voice | System dialer call to authorized 191 creates a normal Telecom connection; INVITE 200, uplink RTP and downlink decoded audio playback observed; hangup returns success, BYE 200, both SIM call states idle |
| SMS sending | System SmsManager sends authorized INFO to 85075 through IMS; SIP 202, RP-ACK, sent result RESULT_OK |
| SMS reception | Four reply segments pass through ImsSmsImplBase; framework delivery result 1, matching RP acknowledgements accepted with SIP 202 |
| System app | Android assembles one inbox message; Google Messages posts its normal notification |
| Persistence | Actual reboot retains providers/mode, automatically reloads idle phone clients and registers; post-boot INFO SMS sending and multipart reception succeed without a manual reload |
| Recovery | Five-minute trial and an actual module-disabled reboot restore original providers/mode and restart companion; temporary short-code policy restores original unknown state |

The first reboot registered but later lost dispatcher SMS capability; the
post-boot idle phone reload now passes an actual reboot and native SMS test.
On the final 0.7.0 reboot on 2026-10-06, the first native SMS instead failed
with RADIO_OFF(2): the dispatcher reported up=true/registered=false/capable=true
although independent callbacks reported WLAN registration and SMS capability.
An explicit idle reload restored dispatch; INFO sending, four reply acknowledgements,
one native inbox row and its notification then passed. Automatic post-boot dispatcher
synchronization remains intermittent; the older success above does not resolve it.
Audible speech still needs user confirmation. Registration alone does not prove audio,
emergency calling, handover, DTMF, supplementary services or every SMS format.

## Implementation

* `iwlan/`: new API30 DataService/NetworkService and EpdgSession. Uses the installed
  Android11 IKE library, normal application context, MANAGE_IPSEC_TUNNELS AppOp,
  SIM EAP-AKA, bounded negotiation and owned-resource cleanup. Never flushes
  global XFRM state. IPv4/one validated SIM/operator only.
* `qns/`: minimal physical-Wi-Fi IMS selection, informed by upstream MinQns.
  Requires VOXI, user WFC enabled and a same-boot elapsed-realtime gate. Root
  renews a 90-second gate; QNS withdraws on expiry. This is not a complete
  carrier selection or VoLTE fallback implementation.
* `ims/` and `prepare-ims-source.py`: reproducible GPL-2.0 overlay on the preserved
  phhusson snapshot. API30 socket compatibility, WLAN registration/capabilities,
  network-bound SIP, reconnection/renewal and voice lifecycle adaptations.
  Unavailable RNNoise JNI is replaced with PCM passthrough.
* SMS uses ImsSmsImplBase. Android handles storage, multipart assembly and
  notifications. RP replies use the saved network RP reference, which differs
  from TP messageRef; rejected delivery produces RP-ERROR.
* `CarrierTrial.java`: platform Binder interface reflection for persistent
  overrides; API30 production `cmd phone cc` is debug-gated on this ROM. Trials
  snapshot the four effective provider values and reject untracked replacement
  values or an inaccessible backup directory. Rollback clears the loader's live
  and persisted override, waits for deletion, restores original XML, reloads
  phone and verifies the effective values before discarding the transaction.
* `module/`: persisted override XML backup, AP-assisted mode, phone reload,
  timed watchdog, persistent supervisor and rollback. Mutations share a kernel
  flock; every background worker carries a unique transaction token, so an old
  watchdog cannot roll back a newer trial. Independent Magisk
  service.d recovery uses a root-private controller copy after disable/removal
  at normal boot. Magisk safe mode can suppress service.d too.

API30 labels the IMS NetworkAgent TRANSPORT_CELLULAR even for WLAN DataService.
IMS requests that framework network and checks IPsec interface/P-CSCF. The
operation mode is read during TransportManager initialization; resetprop alone
is insufficient, so the controller reloads the phone process.

## Build

Use the parent Java17 / ECJ / Kotlin / D8 / android-all11 toolchain. Generated
artifacts are ignored under out/. Signing keys are external inputs.

```sh
python build-api-probe.py
python build-stack-apps.py
export STACK_KEYSTORE=/private/path/stack.jks
export STACK_KEYSTORE_PASSWORD='your-local-password'
python package-stack.py
```

Package only after every build succeeds. Output:
`out/vowifi-stack-api30-services.zip`. Keys, device framework binaries, subscriber
data, SMS bodies and authentication keys are excluded. Distribute GPL sources
and retained licenses with builds.

## Install and reversible trial

Install the ZIP in Magisk and reboot to mount the three privileged APKs.
Installation alone does not select replacement providers. Keep working ADB;
do not run overlapping probes/trials.

```sh
su -c 'sh /data/adb/modules/codex_vowifi_stack_api30/control.sh status 1 1'
su -c 'sh /data/adb/modules/codex_vowifi_stack_api30/control.sh trial 7 1 1'
# Mask: IWLAN=1, QNS=2, IMS=4; combine bits. Slot/sub must be explicit.
# An IMS-only diagnostic trial preserves the original IWLAN/QNS and mode:
# su -c 'sh /data/adb/modules/codex_vowifi_stack_api30/control.sh trial 4 1 1'
# Automatically rolls back after five minutes.
su -c 'sh /data/adb/modules/codex_vowifi_stack_api30/control.sh rollback 1 1'
```

Trial refuses an active call. Its deadline can interrupt test calls; finish
before timeout. Partial masks are compatibility experiments, cannot be retained
by `enable`, and do not imply functioning voice/SMS. The actual app-controlled
IMS-only trial selected only IMS, but did not register WLAN on this MIUI.
After live verification, `control.sh enable SLOT SUB` during a full-mask active
transaction retains replacements and renews gates across boots. Rollback
restores the original providers/mode and companion. Root-private state resides
at `/data/adb/codex_vowifi_stack`; retain it during an active transaction.

Manual recovery with Magisk running, even if the module is disabled:

```sh
su -c 'sh /data/adb/codex_vowifi_stack/recovery/control.sh rollback 1 1'
# Explicit all-owner recovery after module removal:
# su -c 'sh /data/adb/codex_vowifi_stack/recovery/control.sh rollback-all'
```

The boot service waits for boot completion, prepares permissions, and reloads
the phone clients once when idle before supervising. During shutdown, missing
Settings/Binder services stop supervision without discarding persistent state.
Each owner has a token-scoped supervisor. Renewals check the live SIM and private
identity record before extending its gate; unavailable owners are retried without
changing the saved transaction or renewing a mismatched card's lease.
Phone reload invalidates registration listeners. The diagnostic reconnects
callbacks while waiting for WLAN+SMS readiness. Check capability and SMS
permissions in the same phone-process lifetime.

MIUI AutoLockOffClean was observed killing the IWLAN process while telephony
retained its old LinkProperties. The kernel IPsec interface disappeared and SIP
reconnect failed at socket bind. IWLAN now runs with a visible foreground
notification. Persistent supervision detects missing replacement processes and
requests an idle-only phone reload, at most once per five minutes. A controlled
IWLAN process termination recovered automatically to a new IPsec interface and
WLAN voice/SMS registration. Long unattended lock-screen stability still needs
observation. TCP bind failures also close their partially constructed socket.

CarrierTrial rechecks the actual fixed device/SIM/operator before mutations;
provider-apply or gate failures trigger immediate rollback of the transaction.
The companion remains preserved. A separate optional
[diagnostic application](../../diagnostic-app/README.md) exposes read-only
per-SIM status and explicit engine controls.

## Diagnostics and authorized traffic

StackApiProbe only inventories APIs. IndependentEpdgProbe --connect-once is a
real one-shot SIM/network diagnostic. Api30PhhIms, Api30TrialQns, Api30Iwlan and
Api30StackCheck logs contain status/metadata, not bodies or keys.

StackCheckService CHECK observes only. TEST_INFO sends one real INFO SMS to
85075 after WLAN registration and SMS capability, with a two-minute cooldown.
TEST_INFO requires integer extras `slot` and `expectedSubId`; the active identity
and VOXI operator are rechecked before sending. It never selects a network-test
subscription by default. Old callbacks and result broadcasts are scoped to their
run; an additional start during a test is rejected. A CHECK request can use the
same explicit extras. For example (only after authorization):

```sh
su -c 'am startservice -n me.phh.ims/.StackCheckService -a me.phh.ims.TEST_INFO --ei slot 1 --ei expectedSubId 1'
```

Invoke only with authorization for that destination/content. SmsTestPolicy
allow-info is a separate reversible short-code policy override during a trial;
rollback restores the saved policy. It is not a permanent permission grant.

## Scope and provenance

Service features, registrations, alarms and network sessions now retain their
slot/subscription identity; same-boot leases can be independent. This controller
records an explicit active VOXI owner on the validated API30 device; positive
live testing currently covers slot1/sub1. Controller 0.7.0 stores independent
subscription transactions and coordinates the global operation mode, whole-phone
reload, temporary SMS policy and old companion. Two-owner shell fixtures cover
state isolation and shared-resource coordination; simultaneous live dual-SIM
registration is still unverified.
The controller accepts `trial MASK SLOT SUB`, `enable SLOT SUB`,
`reload SLOT SUB` and `rollback SLOT SUB`. Wrong owner requests are refused
before changing settings, configuration or the phone process. Older transactions
without an owner record are interpreted only as the previously fixed `(1,1)`.
See [ownership protocol and remaining requirements](TRANSACTIONS.md).
See [framework samples and contract tests](../../compatibility/README.md).

Other SIM slots/operators, IPv6-only access, Android12–17 and other devices
are not validated or enabled by this fixed-device installer. Android12 IWLAN
uses APIs absent in API30 and cannot simply be installed as a backport.

* Architecture/MinQns and voice reference: retained Suiying6023/VoWiFiPortedRom.
  API30 implementations/build scripts are new.
* phhusson baseline, notices and GPL-2.0: [THIRD_PARTY.md](../THIRD_PARTY.md).
* Android12 IWLAN commit 75c089942bbfb5cab1a9decc30ee535cf5d70699 was inspected,
  not vendored or installed. The device IKE library is not redistributed.
