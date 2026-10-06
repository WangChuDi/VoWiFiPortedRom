# Concurrent owner-selection lifecycle: Android13 / Android16

The latest [final.json](final.json) passed on both owned root emulators on
2026-10-06. Two host device workers ran concurrently, with one serial worker
per SDK. A fresh read-only scout dispatch returned `agent thread limit reached`;
no new agents ran in this batch. Existing terminal agents were not reused.

| Guest | SDK | Final stages | Cleanup | Phone PID |
|---|---:|---:|---|---|
| CodexVoWiFiApi33 / emulator-5574 | 33 | 18 passed | original fixed outer policy restored | unchanged |
| CodexVoWiFiApi36 / emulator-5580 | 36 | 18 passed | original fixed outer policy restored | unchanged |

The final helper SHA256 is
`47a15767802eb0e6be481e017920779629054c8c4098573ba4d32ff5fd4f18cb`.
The installation-stage module SHA256 is
`a2681c4fdacc029c574b3cbf209b68454b0f2024fb8bc1d7fdd45c28f994fb24`.
The three signed service APKs were retained and matched against the existing
unsigned build; they were not rebuilt or installed over the API30 phone.

## Tested behavior

The same batch verifies installation preparation/recovery and shared-lock
contracts plus full mask7 (IWLAN/QNS/IMS) live/native-file selection. Reopened
helpers retain and renew the owner lease, reject old tokens, resume a partially
published lease and refuse foreign lease values without modifying them. It
restores the entire original carrier bundle, selected file, slot lease and
shared-mode property, then repeats restoration. A fresh QNS-only cycle archives
the prior carrier baseline, rejects its token and restores an expired trial.

A fixture removes post-apply role observations to model the private journal
handoff before they were saved. The apply-intent journal still permits original
role-policy recovery. Lease handoff, trial expiry and installation restore
handoff are synthetic state changes, not actual helper kills or OS reboots.
Finally selection cleanup precedes installation cleanup. Original fixed grants,
permission flags, SMS system exemption and effective IPsec AppOp are compared;
the selected-role restore separately verifies its saved effective IPsec/fine
location AppOps and requires a stable full-role observation interval.

## Failed attempts retained

- [first.json](first.json): both failed before selection because root app_process
  could not use the Settings ContentResolver as an unregistered application
  thread. Recovery completed. Fixed by a constrained settings CLI wrapper.
- [second.json](second.json): both selected/restored mask7, then failed a new cycle
  after native data-role permission changes. Outer cleanup initially failed;
  the original fixed policy was explicitly recovered before another trial.
- [third.json](third.json): API33 passed; API36 failed the new cycle. Both outer
  cleanups passed. Fixed system-UID delegation had recovered SYSTEM_FIXED flags,
  but the AppOp side effect was not yet tracked.
- [fourth.json](fourth.json): API33 passed; API36 again failed the new cycle. The
  safe pre-cleanup audit identified IPsec AppOp original0/current2, with no other
  outer fixed-policy difference. Both cleanups passed. Stability checks alone
  did not fix the missing AppOp snapshot; the final helper adds both data-role
  AppOps to ownership and restoration.

[AOSP DataServiceManager](https://android.googlesource.com/platform/frameworks/opt/telephony/+/ee88fa09b5e59a3960ba0c096164c2e803b90c2f/src/java/com/android/internal/telephony/data/DataServiceManager.java)
sets IPsec/fine-location AppOps to allowed when granting the data-service role
and errored when revoking unused services. The runtime audit above identifies
the actual tested AppOp change; this source snapshot is explanatory and is not
claimed as the exact OEM implementation.

## Scope

Each emulator has one ready non-VOXI fake SIM. Configuration selection is not
USIM/EAP authentication, an ePDG tunnel, IMS/MMTEL registration or accepted voice
and SMS. Those proofs remain false, as do real dual-SIM, Magisk mounting,
resetprop/legacy cache reconstruction and actual kill/reboot recovery. Missing
ANM fields do not prove WLAN availability. Modern production selection commands,
watchdog and app buttons remain unavailable. No real phone calls, SMS, reboots,
vendor/APN/CNE/modem changes or credential/SMS-body export occurred in this batch.
