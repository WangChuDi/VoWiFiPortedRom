# Android 17 / API37: runtime passed, lifecycle incomplete

The official `system-images;android-37.0;google_apis;x86_64` revision6 image was
installed and booted as `CodexVoWiFiApi37`. The earlier missing-image claim came
from a filter that omitted decimal package names; it is superseded by the
[corrected SDK metadata](../20261006-api32/official-sdk-metadata.json) and this
actual run. Emulator37.2.12 and cmdline-tools12.0 were used. This guest had a
6 GiB data partition and 3072 MiB configured RAM.

The signed service APKs, module, helper and tool0.9.2 were retained byte-for-byte:

- Helper: `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d`
- Module: `de5826326df086f2694aae88a3973f817b1cc0f30e1425c6ace42721cf7f70de`
- Tool: `2fd68b0d012e9523f935a7c9e91cedea78138b695fcc260c588a57cfcf43a630`

[Artifact identities](artifacts.json) include all three signed APK digests.
The module's embedded helper was also compared with the current helper bytes.
No APK/module was rebuilt and the connected Android11 phone was not modified.

## Proven scope

[Runtime validation](validation.json) passed 20 service-library lookups, explicit
IWLAN DataService/NetworkService and IMS binding, root-only provider refusals,
carrier Binder reads, QNS platform selection and full original configuration
restoration. [Framework results](framework-trial.json) retain that bounded
nonpersistent selection and restoration; they do not prove carrier registration.

[Compiled tool checks](app092.json) passed root read-only diagnostics and action
refusals while production state presence stayed unchanged. They do not prove
positive UI actions under the installed application's own UID. Fake-SIM IMS
transport/capability callbacks remained unavailable.

## Preparation and failures retained

[Default preparation](preparation-reboot-failed.json) staged the fixed privileged
payloads but failed at reboot observation with `TimeoutExpired`. Subsequent
[read-only checks](boot-readiness-failed.json) timed out even for guest `getprop`.
The QEMU host process remained live. At observation, host disk and RAM had free
headroom; no specific emulator, hypervisor or ROM defect was established.

After stopping only the identity-checked owned guest and cold-starting it,
[`prepare-emulator.py --verify-existing`](../../prepare-emulator.py) verified all
three expected privileged package paths, each APK digest and the fixed permission
XML digest before requesting permission preparation. Its
[separate report](preparation-existing.json) explicitly says no system payload
was staged and no guest reboot was requested in that run. This is existing-payload
verification and policy preparation, not successful default reboot installation
or a Magisk lifecycle test. Mismatched paths/APK/XML bytes are refused before
permission mutations; the helper is still required to match the module exactly.

The [full lifecycle attempt](lifecycle-failed.json) passed coordination,
installation prepare/resume and initial full-mask selection, then failed at
`selection-reopen` with `IOException` / `unclassified`. It did **not** pass the
49-stage suite. Cleanup was deferred after a prerequisite failure and is not
confirmed; the private test journals are retained. The
[earlier attempt](prior-lifecycle-failed.json) failed even earlier at the
single-fake-subscription prerequisite.

The isolated [subscription sampler](subscription-observation.json) observed
0 active subscriptions initially, after a package query and after one second,
then 1 after six seconds. This observation does not establish an asynchronous
client cache, the root cause of reopen failure, or a safe fixed delay for production.

Two read-only owner probe runs exited 137 under a 35-second process bound.
[Their checkpoints](owner-checkpoints2.json) reached context initialization and
subscription-query completion but not fixture inventory or owner-operation
completion. They did not restore the outstanding owner; raw outputs and identities
were not published. Further telephony/service and transaction diagnosis is needed
before repeating mutation tests on this guest. Their timeout label was inferred
from the exit code, rather than independently verified. The
[later recovery investigation](../20261007-api37-recovery/README.md) reached a
READY-guard refusal and the Settings-command boundary, retained original journals,
and compared the original profile with a separate calibrated stock guest.

## Remaining scope

The [summary](summary.json) deliberately remains `partial`. Full API37 lifecycle,
pending-fixture cleanup, actual guest reboot, production Magisk hooks, real
USIM/ePDG/IMS traffic, voice/audio/SMS/native notifications, positive app-UID controls
and simultaneous active dual SIMs remain unverified. The complete Android11–17
goal remains active. New code retains GPL-2.0 and the attributed
[phhusson/ims source and license](../../../../android11/THIRD_PARTY.md).
