# Original API37 fixture restored with an audited compatible helper

The original `CodexVoWiFiApi37` installation and owner are now RESTORED, shared
IWLAN mode is released, outer authorization policy is restored and the final
audit passed. This completes recovery of the original interrupted fake-SIM
fixture. It does not convert its earlier failed lifecycle into a passing run.

The six actual stages are in `compatible-sequence.json`; per-stage reports retain
the successful child observations. `compatible-inventory-final.json` confirms
the original schema1 installation and schema3 owner are RESTORED and
`mode_owned=false`. No new baseline, journal migration/deletion or old APK/helper
replacement was performed. The connected API30 phone was not modified this turn.

## Diagnosed failures and bounded comparison

Host-GPU and ANGLE windows repeatedly encountered the SurfaceFlinger graphics
`hasReadColorBufferDma` assertion. The host window did not complete boot. ANGLE
occasionally completed boot but Phone/Settings/package observations were unstable
and memory eventually reached the monitored limit. These failures remain separate
from subsequent successful observations; `graphics-crash-observation.json` has
fixed categories only. The graphics workaround was informed by another project's
[first-hand integration report](https://github.com/hajisensai/Fushi/blob/main/docs/agent/integration-testing.md),
then independently exercised on this original AVD. Changing the GPU alone did
not resolve the assertion here.

Temporary three-button navigation produced a stable window. All four keys passed
two direct ADB command paths and three app-context paths, including the unmodified
retained `ModernRootSettings`: 20 observations. The original 11 properties files
had unchanged hashes and Phone/system_server identities stayed stable during the
Settings probe. These are current successful read observations; they do not prove
the exact cause of every historical `ModernRootSettings.command:19` failure.

The v5 restore then failed at the role broker's fixed-classpath guard. V6 ran
the retained entries as independent children with the required single-helper
CLASSPATH but still failed. `roles-readonly.json` checks the original package
owners and grants and separates informational metadata from authorization flags.
Only the two PermissionController sensitivity flag types differed in the IMS
role observations; there was no foreign authorization-policy difference.

## Compatible recovery and provenance

The current controller already compares authorization policy while preserving
`USER_SENSITIVE_WHEN_GRANTED` and `USER_SENSITIVE_WHEN_DENIED` (mask768). Its
schema1/schema3 compatibility was audited before use. `build-compatible-helper.py`
compiled the current source inventory into an isolated helper, recording every
source digest. It did not overwrite the retained helper or any production engine.

The new helper was staged at the existing allowed fixture path
`/data/local/tmp/codex-modern-owner-audit.zip`. An existing different file, wrong
owner/mode, source mismatch, helper/classes mismatch or old APK mismatch refuses
execution. Compatible children retain the original owner, build, schema, package
and grant checks and refuse non-informational flag, grant, AppOp or SMS exemption
differences. The full outer audit reports five raw flag-field differences within
mask768 and zero authorization-policy changes. Thus recovery preserves current
sensitivity metadata; it is not byte-exact restoration of all raw permission flags.
Outer cleanup separately observed those bits exactly unchanged during cleanup.

| Artifact | SHA-256 |
|---|---|
| Retained helper, unchanged | `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d` |
| Compatible helper | `5c5748cdd75377a34a05001c22bb3d5e7a9375de060a265b0c3bbb51202b44c4` |
| Compatible probe | `763e9bd0cf0c0d1d6e576e790c8fdfa3ac78f97ae8d09795d30e0bcbd764bcd1` |
| Compatible probe source | `6b24ac6d9b7dc4d946eb5ba4a0dcd8c9f1e5396289080f1c3da7b8d2730a21fe` |

Build manifests and `source-byte-pins.json` identify the other probe inputs.
`SHA256SUMS` covers all33 JSON reports. Two generated Java probes preserve their
exact tested source bytes through scoped Git attributes; source/digest checks
therefore continue to work after checkout. No private owner names/tokens, package
UIDs, SIM identities, raw command logs, account data or message bodies are exported.
Sources remain GPL-2.0; the underlying IMS source and attribution remain in the
[phhusson notice](../../../../android11/THIRD_PARTY.md).

## Host contracts and final cleanup

Fifteen diagnostic contracts cover process absence/identity, output redaction,
navigation ownership, report preservation, timeouts and concurrent record changes.
Ten v6-runner and fourteen compatible-runner contracts cover failed package
commands/calibration, path mismatches, malformed/duplicate results and compatible
helper staging/ownership. They are host-only tests, never carrier proof.

The first navigation restore attempt occurred after its 900-second window ended;
its report confirms no write. A fresh restore report later confirms the original
gestural navigation, with the 11 record hashes unchanged during that operation.
All four identity-guarded emulator monitors report terminal owned processes. The
compatible window ended by explicit finish after recovery and navigation restoration.

Full API37 lifecycle, modern positive app-own-UID actions, real carrier/MMTEL/native
SMS, actual modern Magisk mounting/reboot and simultaneous dual active SIMs remain
unverified. The next lifecycle must use a fresh nonce and a matched helper/module;
the broad existing runner does not accept an independent helper profile. Do not
overwrite this recovered original state or silently rerun its failed old suite.
