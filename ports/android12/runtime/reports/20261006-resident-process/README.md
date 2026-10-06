# Real resident-process lifecycle, 2026-10-06

The final `fourth.json` passes on owned Android13/API33 and Android16/API36
Google-APIs emulators concurrently. Each guest completes 39 stages: the original
33 installation/selection/supervisor stages plus six actual resident stages.
Every invocation uses a fresh nonce; failed baselines are not reused.

| Report | Scope | Result |
|---|---|---|
| `first.json` | Full suite plus resident | Both completed the original suite and resident preparation, then failed with ValueError; the precise failing operation/reason was not recorded. Cleanup passed. |
| `second.json` | Resident prerequisites only | Both reached an alive resident; the first read-only audit returned `modern-controller-busy`. Tracked processes ended before policy cleanup. |
| `third.json` | Resident prerequisites only, bounded busy retry | API36 passed all 14 stages. API33 refused prepare because the direct legacy-mode field was absent (`ModernSharedIwlan.acquire:59`); original cleanup passed. |
| `fourth.json` | Full suite plus resident, final helper | Both pass all 39 stages, complete original-policy cleanup and retain the same phone PID. |

The missing API33 field was present as `isInLegacy=false` in a subsequent
read-only observation. The original omission's cause is unproven. The correction
rereads fresh dumps at most three times on API31–33, then still refuses unknown
legacy state; it does not substitute a default or ignore contradictory fields.
The host only retries a validated `resident-audit` IOException with the exact
fixed busy reason, within bounded attempts/time. It never retries a mutation or
failed state assertion to manufacture success.

## Actual assertions

- Retained mask7 owner prepared over the fixed permission/lease/configuration baseline.
- Resident shared production loop runs at its real 15-second interval.
- A duplicate process waits then refuses the generation-bound resident lock.
- After 105 real seconds, the original lease has elapsed and the live lease is extended.
- SIGKILL targets only the private journal-matched nonce fixture, checking PID,
  process start time, root UID, boot, build, generation and exact command name.
  The first tracked process ends and the resident lock is released.
- A new resident process resumes the same retained selection.
- Disabling the fixture automatically restores original owner and installation
  policy, then exits normally. All tracked host handles are terminal before outer
  policy cleanup. The full fixed grant/flag/SMS-exemption/AppOp observation restores.

The final host follow-up also found zero nonce-named resident fixture processes
in each guest's device process list. This extra read-only check is separate from
the JSON's tracked-handle assertion.

The final helper/module bytes match both successful version records:

```text
helper cd67b65cd39bbba8893a4fc67c23e3bef5bbff07f8f69b52505a35847aad8ab7
module a5f53710b0f60c80693789cb1ab94d2d5c9f1033e68a6f206648fbe3a2621319
```

Build ECJ/D8 and compiled service/bundle checks pass. The host parser runner
passes 12 full-bundle baseline, 13 phone-idle and 21 IWLAN-observation contracts.
Existing deprecated-API/D8 target warnings remain. Signed service APK bytes were
retained; only the root helper/module changed. No signing keys are included.

## Scope and provenance

Guests have one ready non-VOXI fake SIM and preinstalled privileged packages.
Only temporary nonce-scoped fixture directories are mutated. This does not prove
USIM authentication, ePDG carrier acceptance, real voice/SMS/native delivery,
simultaneous dual SIMs, phone-process/cache reconstruction, actual Magisk mounting
or boot hooks, OS reboot, every interrupted core transaction phase or all API31–37
devices. In particular, a supervisor SIGKILL is not an interrupted PREPARING test.

The API30 phone remains on its existing replacement. A separate read-only current
diagnostic found the three selected providers, IKE/child open, WLAN registration
and voice/SMS capability. No new phone install/configuration change/call/SMS was
performed, and capability is not a fresh end-to-end call/SMS result. Its private
non-identifying audit is kept outside this repository.

GPL-2.0 source and the retained phhusson/ims attribution/license are unchanged.
The broad Android11–17 app/dual-SIM goal remains incomplete.
