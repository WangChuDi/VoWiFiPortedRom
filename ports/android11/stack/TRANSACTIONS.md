# Subscription ownership and the remaining multi-SIM work

The final target includes selectable replacement components on Android11–17 and
dual-SIM devices. Per-slot diagnosis and per-slot service objects alone do not
complete this target. Controller 0.7.0 manages separate subscription transactions
under a shared coordinator; simultaneous live dual-SIM validation remains pending.

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

## Multiple transaction coordinator (0.7.0)

Each active owner has `transactions/slot-N-sub-S/`, with its own original XML,
provider snapshot, identity record, component mask, lease, transaction token,
watchdog and persistent supervisor. Renewals recheck actual SIM and backup identity;
an unavailable owner waits for recovery without extending a mismatched lease.
One supervisor lock per owner prevents duplicate workers. Background phone reloads
also share a boot-scoped five-minute cooldown; explicit user reload remains available.
The Java helper accepts only the exact state
directory for the explicit slot/sub tuple (or the legacy root during migration),
rejects symbolic/canonical aliases, and still checks the actual SIM fingerprint.
The root `control.sh` routes operations; `subscription-control.sh` performs them
under the shared root lock. A new subscription in an already managed physical
slot is refused until the original owner is explicitly recovered.

Operation mode, phone-process restart, test-package SMS policy and the old SMS
companion are device-wide resources. The coordinator records one original mode.
Ending one owner preserves AP-assisted while another selected IWLAN remains;
without another IWLAN, it restores the original mode. The companion and test
policy resume only when the last transaction is restored. Every phone restart
checks both slots for an active call. Reload rebuilds shared framework bindings
and may briefly reconnect another card, but does not replace its configuration.

Successful restoration atomically archives the entire subscription directory.
An interrupted restore retains its original transaction evidence. Stale workers
are scoped by tuple and token, and cannot expire a later generation. Legacy
migration verifies copied evidence, commits the slot directory, preserves the
old state privately, and can resume after interruption during old-file archival.
Boot recovery, persistence, module removal and the independent recovery hook
route all owner records rather than one root-level transaction.

The tool reads selected subscription status separately from `active_owners`.
Recovery uses an explicitly named SIM selector; empty-slot inspection does not
attribute another card's transaction to that slot or prevent recovery of it.

Android shell fixtures exercise two transactions with isolated carrier/platform
backends: another owner's token, baseline and lease survive restoration; shared
mode, partial masks, stale workers, same-slot conflicts, last-owner companion
restart, rollback-all and interrupted migration are covered. These are controller
tests, not two real SIMs registered to a carrier. The actual connected device has
only one active card; do not infer simultaneous registration from those fixtures.

## Remaining requirements

1. Handle removal, relocation and subscription database resets with explicit
   recovery evidence; current recovery safely refuses owner/identity changes.
2. Verify global mode/reload/companion coordination on two real active cards,
   including service failure while another card is in a call or receiving SMS.
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
6. Resolve intermittent post-boot SMS dispatcher synchronization. On the final
   0.7.0 reboot, its service-up and capability flags were true but registered was
   false, producing RADIO_OFF before invoking replacement IMS. Independent
   registration/capability callbacks do not prove dispatcher readiness. An explicit
   idle reload restored real INFO sending and native multipart delivery/notification.
   In 0.7.1, stale feature callbacks are retired and the actual installed APKs are
   verified before preparation. The final reboot's first native SMS passed without
   another manual reload; the tool also observed a real send's three availability
   flags after its log-reader correction. Multiple boots/long-term observation
   and confirmation of every older failure's cause remain pending.

Working API30 baselines and published source/artifacts remain preserved while
these requirements are implemented. Full completion requires all of them.
