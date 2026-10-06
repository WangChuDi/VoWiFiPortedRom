# Recorded recovery and PermissionController metadata — tool0.9.5

The app routes recovery through the saved modern owner even when that SIM is
explicitly absent. It withdraws only the recorded component lease and reports
RESTORING/pending until the original slot/sub and carrier-file identity return.
It does not clear an absent subscription's carrier loader or copy its saved file
into another card's state. New activation/retention/renewal still requires a
ready23415 live owner. Current API30 replacement behavior remains separate.

Modern native role callbacks can set default/system-fixed authorization flags.
PermissionController independently maintains USER_SENSITIVE_WHEN_GRANTED/DENIED
metadata (API mask768). Restoration preserves these two metadata bits and checks
the actual original grants and every other authorization flag. The system-UID
broker writes only the existing default/system-fixed role mask after privileged
APK/UID checks. Original journals retain full raw flags. This is restoration of
authorization policy with current sensitivity metadata, not raw flag equality.

Current signatures/artifact hashes are in [signed-artifacts.json](reports/20261007-detached-metadata/signed-artifacts.json).
The API30 engine is unchanged (`c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5`).
The three signed modern service APKs are also unchanged from the bound-IWLAN batch.
GPL-2.0 and attributed [phhusson/ims notices](../android11/THIRD_PARTY.md) remain intact.

## Executed checks and limits

- [Physical API30](reports/20261007-detached-metadata/physical-api30.json): final
  signed tool installed and APK bytes checked; concurrent empty/active-slot root
  diagnosis completed with stable phone/system-server observations. The active
  slot still reports full ACTIVE7 replacement, WLAN registration and advertised
  voice/SMS capabilities. No new call/SMS or engine/APN change occurred.
- [Host contracts](reports/20261007-detached-metadata/host-contracts.json): production
  metadata, inventory, carrier-dump, idle and IWLAN parsers; metadata equivalence
  still refuses changed grants/user/admin/exemption/unknown bits.
- [Saved API36 recovery](reports/20261007-detached-metadata/original-api36-recovery.json):
  reused the failed fixture's original nonce and journals, restored native role
  and installation policy before outer cleanup, then observed no pending
  installation. It did not replace/resample the saved baseline. Metadata bits
  were observed unchanged during outer cleanup. This used the preceding candidate
  helper `8fa0ee85c8eff5b64d7264e903fdae34d1c43a99786cc6846cab88f2448886e4`.
- [Final concurrent lifecycle](reports/20261007-detached-metadata/lifecycle-api32-api36.json)
  and [summary](reports/20261007-detached-metadata/summary.json) contain the actual
  per-version pass/failure and stage count. The absent-owner fixture injects
  absence in a named QEMU/fake-SIM profile; it does not physically remove a SIM.
  Raw role flag/grant deltas remain reported alongside authorization equality.
- [Resource monitor](reports/20261007-detached-metadata/host-resource-monitor.json)
  confirms terminal owned guests after the batch, bounded host memory/disk and no
  physical-phone engine mutation. Earlier failed results are retained in the same
  report directory rather than relabelled as success.

The phone's keyguard still prevented the latest own-app-UID UI regression. Root
backend/installed-byte checks do not prove that UI path. Modern real carrier
calls/SMS/native notifications, actual Magisk lifecycle, real SIM removal and
simultaneous dual-active SIM operation remain unverified. API33 still has older
unresolved test journals and refused an APK mismatch; API37's earlier full suite
failed and cleanup is unconfirmed. Android11–17 compatibility is not complete.
The full requested goal remains active.
