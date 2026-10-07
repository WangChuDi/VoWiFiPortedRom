# API37 matched stock controls and original context boundary

The existing stock AVD was sampled twice with the same official SDK37 image,
4096 MiB guest RAM, two CPUs, SwiftShader, Vulkan disabled, QuickbootFileBacked
disabled and no snapshot load/save. The first run omitted writable-system; the
second enabled it. Its userdata was retained across both runs. No replacement
package or carrier policy was installed. The original failing AVD and the
physical phone were untouched by these stock trials.

| Actual observation | Stock fixed system | Stock writable system | Original writable system |
| --- | --- | --- | --- |
| File-backed RAM requested | off | off | off |
| Boot observed | yes | yes | yes |
| Settings integer queries | all successful | all successful | both direct commands successful during context probe |
| Post-boot control window | 120 seconds | 360 seconds | memory ceiling reached at host sample302 seconds |
| Host private memory | stabilized | peak5,550,759,936 bytes | last12,943,572,992 bytes |
| Original fixture restored | not applicable | not applicable | no |

[Strict evaluation](summary.json) requires both completed stock windows, actual
boot, calibrated package inventory with platform reference packages present,
absence of all three replacement packages, nonempty successful Settings samples,
Phone/system-server PID-plus-start-time continuity and terminal owned processes.
The [original observer summary](stock-window-observer-summary.json) used `passed`
only to mean its window completed; that label alone is not the strict evaluation
and never proves fixture recovery. The underlying reports are
[fixed](stock-fixed.json) and [writable](stock-writable.json).

Only the stock AVD's rebuildable4GiB RAM backing file was removed, after confirming
no emulator was running. Its allocated size equaled4,295,032,832 bytes. Stock
userdata and all original AVD files/journals were retained. The stock runs did
not recreate that RAM file. Existing configured8/9GiB startup and3GiB runtime
disk floors were retained; resource thresholds were not lowered for a pass.
The original AVD subsequently used the same disabled RAM-backing option and
[still reached the12GiB host-private-memory ceiling](original-host.json). This
does not establish a specific APK as the cause. Original and stock still differ
in userdata, stored overlays, installed components and prior experiment history.

The [initial original inventory](original-inventory-initial.json) verified old
helper/APK/probe digests, then failed with RuntimeException at the context
checkpoint. No selection restoration was entered. A separate
[context-stage probe](original-context-stages.json) then reached Looper,
ActivityThread/system context and Telephony bootstrap successfully. Its direct
ContentResolver Settings read received SecurityException/RemoteException, while
the standalone `settings` and `cmd settings` commands both returned an integer.
The fixed framework frames identify getContentProvider/acquireProvider. Raw
exception messages and stack traces are excluded; this evidence does not identify
the specific server-side authorization rule or prove an IMS service defect.

The [later inventory attempt](original-inventory-after-cap.json) refused the
missing owned guest after the resource monitor had stopped it. The same monitor
handle and a fresh WMI inventory confirmed termination; its absence was not
inferred from an observation timeout. Last known original owner/installation
phases remain ACTIVE/PREPARED, and original cleanup is unconfirmed. No fresh
baseline, selection restore, installation restore or outer cleanup was attempted.

## Recorded source and scope

[The exact matched-control script](source/run-api37-matched-stock-controls-20261007.py)
and [context Java](source/Api37ContextStages.java) /
[builder-runner](source/build-run-api37-context-stages-20261007.py) preserve the
executed code. These are GPL-2.0 lab harnesses, not production installers. They
expect the existing Windows work-directory layout, SDK, named owned AVDs and
toolchain; do not launch them over another device or wipe/rebaseline the pending
original fixture. Their source hashes and all8 fixed-report hashes are archived
in SHA256SUMS using LF bytes. Console tokens, private journals, raw logs, SIM
identities, SMS bodies and signing keys are excluded.

Full API37 installation/selection/recovery lifecycle, modern actual carrier
voice/SMS/notifications, positive modern app-UID actions and two active SIMs remain
unverified. The physical Android11 application's previous own-UID recovery result
and signed production releases were not changed or retested here.
