# Subscription ownership and the remaining multi-SIM work

The final target includes selectable replacement components on Android11–17 and
dual-SIM devices. Per-slot diagnosis and per-slot service objects alone do not
complete this target. The current controller is moving from one fixed identity
to explicit ownership; it still manages one global transaction at a time.

## Current identity protocol

* `trial MASK SLOT SUB` records the owner as root-private `owner` containing
  `slot:subscriptionId`, atomically before creating the transaction token.
* `enable SLOT SUB`, `reload SLOT SUB`, and `rollback SLOT SUB` reject a tuple
  different from the recorded owner. Token-scoped background reload remains
  compatible with the previous `reload TOKEN` form.
* The carrier helper receives the same tuple, rechecks the active slot/sub and
  operator before selecting providers, and verifies the transaction owner before
  apply/clear/snapshot/restore checks. Clear requires the original active tuple;
  if that card is unavailable, recovery preserves its snapshots and transaction
  instead of clearing a different card's CarrierConfig.
* Lease keys use the actual owner slot/sub. Only legacy owner `(1,1)` also gets
  the old global lease. Old transactions without an owner file are explicitly
  interpreted as `(1,1)` because that was the only previously enabled identity.
* The tool separates the selected diagnostic subscription from controller owner.
  Reload/enable require selecting that owner; rollback explicitly targets the
  owner even when viewing another slot. Selecting another SIM clears UI results.

## Selected persisted file (0.6.0)

The helper now snapshots/restores only the selected card's exact override XML.
The root-private record binds its directory, filename and SIM fingerprint, and
verifies the original backup with SHA256. Identifiers and paths containing them
are never printed. Restoring an existing original uses a same-directory staging
file, verifies and syncs it, then atomically replaces the selected target. Only
that target is relabeled. An originally absent file is removed only for its
recorded owner; another card's XML is never copied or deleted.

Filename choice requires runtime evidence: the current override or platform
cache must match a unique candidate. AOSP's current loader uses the specific
carrier ID, while this MIUI uses MCC/MNC 23415 despite its carrier ID being 28.
Both candidates are checked, not presumed equivalent. ICCID-only, ambiguous or
unobserved layouts remain refused. Source:
[CarrierConfigLoader](https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/phone/CarrierConfigLoader.java).
The master source is not evidence of every Android release's implementation.

Old active transactions adopt the original selected file from their preserved
legacy baseline, never the current replacement. PREPARING failures archive
partial snapshots without clearing providers; retries create a fresh state.
Copy/restore interruption tests preserve the live file. Real-device migration,
clear, exact-file restoration, provider restoration and a fresh trial passed
on the connected MIUI profile. Tests with two artificial SIM identities verify
file isolation; they are not simultaneous live dual-SIM registration tests.

The transaction directory, IWLAN operation mode, phone-process restart, SMS test
package policy and companion receiver remain global. The controller still does
not allow two independent active transactions or promise dual-active registration.

## Remaining requirements

1. Extend selected-file ownership into separate transaction directories. Handle
   removal, relocation and subscription database resets with explicit recovery
   evidence; the current helper safely refuses owner/identity changes.
2. Coordinate global mode/reload/companion lifetime across a set of selected
   subscriptions; ending one selection must preserve another active selection.
3. Exercise simultaneous feature/registration/tunnel lifecycles on two active
   SIMs and verify each card's real calling/SMS delivery and unselected-card
   behavior. The connected device currently has only one active card.
4. Complete modern installation/controller profiles and validate actual
   Android12–17 framework, root, permission and IKE behavior. The pinned samples
   are static evidence, not device tests. Android12's new DataService overload
   forwards to the old override but drops newer request fields; handover and
   modern field handling require their own implementation and tests.
5. Extend diagnostics beyond available registration/capability metadata with
   bounded, explicitly requested real traffic tests; do not report unseen stages
   as healthy. Keep message bodies, SIM identity and authentication keys private.

Working API30 baselines and published source/artifacts remain preserved while
these requirements are implemented. Full completion requires all of them.
