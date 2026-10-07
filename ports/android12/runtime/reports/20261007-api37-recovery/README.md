# API37 original recovery: narrower failure evidence, cleanup still pending

The original `CodexVoWiFiApi37` data and its journals were preserved. Independent
read-only observations confirmed one schema-1 PREPARED installation fixture and
one schema-3 ACTIVE current-owner selection with `mode_owned=true`. The original
helper and all three actual privileged APK paths/hashes matched their retained
artifacts. Neither the connected Android11 phone nor its working releases changed.
The full Android11–17/carrier/application/dual-SIM goal remains incomplete.

## What changed in the evidence

* [Paired getters](getters-paired.json) completed subscription, TelephonyManager,
  SIM-state and operator queries both with and without a pumped main Looper.
  A main-Looper fix is therefore not established by this run.
* [Later paired samples](getters-state-transition.json) observed UNKNOWN and then
  READY while the complete fake-operator prerequisite still failed. A successful
  subscription query alone does not prove full owner readiness.
* A [fresh isolated owner observer](owner-fresh.json) reached
  `sim-ready-refused` and exited 137 in 8.383 host seconds under a 40-second bound.
  Exit 137 does not itself prove a timeout or transaction-lock deadlock. The older
  reports' `process_timeout_observed` field was an exit-code heuristic; retain their
  actual checkpoints/exit codes without treating that label as independent proof.
  [The original observation](owner-original.json) now labels it explicitly as
  `legacy_exit_code_timeout_heuristic`, with `timeout_cause_verified=false`.
* The bounded [inventory](inventory-no-vulkan.json) confirmed two consecutive ready
  samples and the original pending records. [Selection recovery](selection-no-vulkan.json)
  entered the old helper and failed with IOException at
  `ModernRootSettings.command:19`. Inspection of that retained implementation maps
  the line to `settings-command-unavailable`. No complete restoration was observed.
* [Direct shell checks](settings-service.json) also returned exit 20 and the fixed
  `Can't find service: settings` marker. The report's service-list name parser was
  not positively calibrated, so its false entries are **not** proof that every
  listed service was absent. The direct Settings command failure is the stronger
  observation. Later native queries saw Settings/activity name tokens again.
* [Short continuity sampling](framework-short-window.json) observed unchanged Phone,
  system_server and kernel boot identities over four closely spaced samples, no
  kernel OOM markers, and fixed exception classes. This does not establish long-term
  stability or rule out service restarts outside that short window.

## Controlled host runs

All original-AVD trials configured 4096 MiB guest RAM and used exact owned-process
identity checks, no wipe/snapshot load/save and a 12 GiB host-private-memory ceiling.
Their journals and original baselines were kept. [Lavapipe](host-lavapipe.json),
[SwiftShader](host-swiftshader.json) and
[SwiftShader with Vulkan disabled](host-no-vulkan.json) all reached the ceiling.
Changing the software backend or disabling Vulkan did not resolve the observed
growth in this original state. The first monitor lacks a reason field, but its
last memory sample exceeded the same ceiling; later monitors explicitly record it.

An independently created official API37 [stock control](stock-control.json) used
new userdata, SwiftShader, disabled Vulkan and no writable-system option. The
creation script requested 4 GiB for userdata; its later [AVD configuration](stock-config-observation.json)
reported 6 GiB. Guest RAM was separately configured as 4096 MiB. Do not infer
an observed 4 GiB disk partition from the launch script's requested value.
[Calibrated PackageManager sampling](stock-package-calibration.json) confirmed its
system reference packages existed and all three replacement packages were absent.
It completed a 180-second post-boot observation window with successful Settings
queries and stable Phone/system_server identities. Host private memory settled
near 5.2 GiB rather than following the original trials' growth beyond 12 GiB.

This narrows investigation to differences between these profiles. It does not
identify an APK as the cause: original userdata, stored overlays, installed
components **and** the writable-system launch option differ. The following
original-data/fixed-system observation is reported separately, never as a clean
baseline or a completed original-fixture recovery.

The [original-data/fixed-system trial](host-fixed-system.json) omitted the
writable-system option, retained its userdata and journals, and never exposed a
completed boot during its 505-second bounded observation. Its exact owned host
processes were then stopped. This failed boot does not isolate the memory cause.
The [offline runner refusal](runner-offline-refusal.json) verified that the public
runner refused the missing owned guest without targeting the connected phone.
The [altered manifest/payload refusal](runner-profile-refusal.json) also checked
that changing both local digests was rejected before any ADB invocation, using
an intentionally missing ADB executable. This is a host guard test, not an
additional successful guest recovery.

Android's official documentation lists the
[software rendering backends](https://developer.android.com/studio/run/emulator-acceleration)
and recommends [`-feature -Vulkan` for Vulkan-related troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting).
These options do not guarantee reduced memory or prove the cause here.

## Source and remaining work

The isolated [probe source/build/runner](../../recovery-api37/README.md) preserves
the original helper/schema guards and layer ordering, stages no engine and creates
no replacement baseline. Its public build reproduced the actual executed probe.
Fixed metadata is archived with SHA256SUMS; private journals, raw logs, console
tokens, SIM identities and SMS bodies are excluded.
Report checksums cover canonical LF bytes, matching Git blobs and source archives.

Pending original selection/installation/outer cleanup, the complete 49-stage
API37 lifecycle, actual modern Magisk/OS reboot, carrier voice/SMS/notifications,
positive app-own-UID operations and simultaneous real active dual SIMs remain
unverified. Earlier runtime/app passes are not promoted to those broader claims.
