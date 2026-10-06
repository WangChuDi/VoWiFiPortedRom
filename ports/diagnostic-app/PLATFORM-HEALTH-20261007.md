# Version0.9.3: retain diagnostics during stalled system calls

A stalled subscription, phone-service or CarrierConfig Binder call previously
held up the whole root diagnostic until the outer shell timeout. The application
then lost earlier results. Version0.9.3 adds immutable completed-stage checkpoints
and a32-second process watchdog. It returns one partial JSON result with
`diagnostic_complete=false`, `diagnostic_error=TimeoutException` and the stalled
`diagnostic_stage`. It does not claim the currently unfinished stage was observed.
The outer40-second shell timeout remains as a separate backstop.

`PlatformHealth` compares phone/system-server PID plus `/proc` start-time identities
at the beginning and end of diagnosis. Only `stable`, `changed`, `absent` or
`unknown`, and nullable presence booleans leave root. Multiple PIDs, malformed
stat records, permission failures and sampling timeouts are unknown. Reused PIDs
with a different start time count as changed. Stable identity does not establish
service responsiveness, modem health or carrier acceptance.

Partial diagnostics retain visible observations and recorded-owner recovery.
Trial, retention, reload and module update require a complete result at both
button and execution entry points. Registration observed before an incomplete
check or process change is labelled as a prior callback. Post-action status no
longer presents such a callback as confirmed recovery. Root controllers retain
their independent ownership/SIM/device/call-state checks.

## Final artifact identities

| Artifact | SHA256 |
| --- | --- |
| Signed tool0.9.3 / versionCode15 | `26fafab696ddd9c1cd823422babead38a8837078c252ef40ac5e6686397f8ed8` |
| Embedded API30 module | `c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5` |
| Embedded SDK31–37 module | `de5826326df086f2694aae88a3973f817b1cc0f30e1425c6ace42721cf7f70de` |

Both engines are unchanged from0.9.2. No modem, vendor, APN, IMS/IWLAN/QNS service
APK or module configuration was changed for this diagnostic update. GPL-2.0 and
the existing [phhusson/ims provenance](../android11/THIRD_PARTY.md) remain intact.

## Executed checks

- [Signed build/metadata/asset validation](reports/20261007-platform-health/validation.json):
  source compiled, SDK30 minimum/target and version15 checked, signature verified,
  both embedded ZIPs byte-equal to independently retained engines.
- Production runtime ABI contracts:77 assertions passed as part of the build batch.
- [Host production contracts](reports/20261007-platform-health/host-contracts.json):
  malformed/multiple/absent/unknown/PID-reused observations, completeness and
  registration policy, immutable checkpoint and single terminal winner checks;
  a truly blocked child and a child whose main thread returned early both exited
  after32.14 seconds with one partial result. The watchdog keeps the process alive
  until its winning output is emitted; it cannot lose that output on main return.
- [API32 compiled root diagnosis/refusals](reports/20261007-platform-health/api32-readonly-refusals.json):
  complete stable process observations; non-root diagnosis and fixed invalid,
  non-root and missing-module actions refused; production-state presence unchanged.
- [API32 blocked fixture](reports/20261007-platform-health/api32-blocked-watchdog.json):
  classes loaded from the final APK returned one partial result after approximately32 seconds,
  retained the completed-stage marker and disabled changes. This was a fixture,
  not a carrier registration or an injected fault in the physical phone.
- [Physical API30 two-slot root regression](reports/20261007-platform-health/physical-readonly.json):
  two workers observed complete diagnostics and stable phone/system-server identities.
  Empty slot0 did not claim the other slot's IMS. Active slot1 still reported full
  selection7, WLAN registration, voice/SMS capability, one matching IMS network
  and two P-CSCF addresses. Phone PID remained unchanged. No call/SMS sent.

The [signed app installation and on-device byte identity](reports/20261007-platform-health/installed-identity.json)
were verified. The attempted own-UID screen regression
was blocked by the phone's active lock screen; it is **not verified** by the root
backend results. The user was asked to unlock it. Earlier0.9.2 evidence on other
SDKs remains historical; this0.9.3 regression directly covers only API30/API32.
Native call/SMS/inbox notifications, modern positive application actions and
real simultaneous dual-active-SIM behavior require their respective live checks.
This update does not complete the full Android11–17 replacement objective.

## Android17 host evidence remains partial

[A resource-monitored Lavapipe run](reports/20261007-platform-health/api37-lavapipe-monitor.json)
observed boot completion but QEMU private memory exceeded12GiB at174 seconds.
The exact owned QEMU/launcher were stopped by the resource guard, preserving the
guest's outstanding fixture journals. Both owned host processes were terminal.
Changing from SwiftShader did not resolve the observed growth. No exact GPU,
framework, emulator or carrier root cause has been established. Pending API37
fixture cleanup and the full49-stage lifecycle remain unconfirmed.

The report's3072MiB is the **requested configuration**, not proof of the actual
guest allocation. Official [emulator release notes](https://developer.android.com/studio/releases/emulator)
state that API37 Phone AVDs require at least4GB and smaller configurations are
increased automatically. That rule does not explain the observed12–24GiB host
growth. Further API37 tests must retain host memory/disk guards and must not
rebaseline or delete the original pending transaction merely to obtain a pass.

## Reproduction

Build both engines independently, then use the existing external signing
environment with `python ports/diagnostic-app/validate.py --build`. This performs
one signed build and its host contracts; it does not install or rebuild engines.
Run `check-runtime.py` with the final APK and an owned named root emulator.
`check-watchdog-runtime.py` builds only its isolated test fixture, loads watchdog
classes from that APK, and accepts owned SDK31–37 emulators only. Both report
writers require fresh output paths. No signing material or raw phone data is
included in the public evidence.
