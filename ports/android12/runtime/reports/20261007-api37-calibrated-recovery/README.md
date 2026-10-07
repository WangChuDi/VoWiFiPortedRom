# Calibrated package readiness and original API37 recovery

This is original-fixture emulator evidence. It does not establish modern carrier
traffic, dual active SIM support or completed recovery.

The previous v4 attempt stopped before invoking the guest probe and reported
`retained-privileged-path-mismatch`. Its runner had ignored the return code of
`pm path`; the saved report cannot distinguish command failure from a different
installed path. Its [inventory](previous-v4-inventory.json),
[sequence](previous-v4-sequence.json) and [host window](previous-v4-host-window.json)
are retained as failed observations.

The v5 attempt checked actual original APK file hashes first and waited for two
successful package inventories calibrated with known Phone and Settings packages.
The [sequence](calibrated-sequence.json) observed package command return codes
0, 224 and 20, followed by two successful inventories. All three original file
hashes were correct. The [guest inventory](calibrated-inventory.json) then passed:
the retained helper, all three exact privileged paths, APK hashes and two ready
fake-SIM samples were verified. The existing installation was PREPARED and its
current schema-3 owner was already RESTORING with `mode_owned=true`.

The [actual selection recovery](calibrated-selection.json) reached
`selection-restore` and failed with IOException at `ModernRootSettings.command:19`.
That retained helper reports a Settings child-command failure there; this run did
not record which Settings operation or child error caused it. It does not prove
a modem, SIP or carrier defect. Installation, outer restoration and audit were
not entered. No new baseline, journal migration/deletion or physical-phone
change occurred. The [summary](summary.json) keeps cleanup unconfirmed.

The [host monitor](calibrated-host-window.json) observed boot and stopped its
identity-verified owned emulator after the driver had terminated. It preserves
the original writable userdata/system overlays, uses 4-GiB guest RAM, SwiftShader,
Vulkan disabled and Quickboot file backing disabled. This short recovery window
does not establish long-term memory stability. `calibrated-driver.py` and
`owned-window.py` are exact source snapshots of the local experiment, rather than
general-purpose portable launchers. Paths resolve relative to the original work
directory and cannot be reused over unrelated fixtures.

The [isolated probe](../../recovery-api37/README.md) now runs outer/audit in a bounded
separate child: the retained entry prepares its own main Looper and cannot be
called safely in the already-initialized parent. Output overflow or incomplete
reads are rejected, and one matching success result is required. The real run
did not reach those paths, so their successful runtime behavior remains unproven.

[Build identity](v5-build.json) corresponds to the current independently pinned
source and executed probe. [Ten host-only adversarial cases](host-runner-contracts.json)
verify distinct command/calibration/path failures and reject failed, duplicated
or wrong-schema final results. They invoke no ADB and prove runner behavior only.
All report and experiment-source bytes are covered by [SHA256SUMS](SHA256SUMS).
Reports contain fixed metadata; private journals, IDs/tokens, raw logs, unexpected
package paths, SIM identities, credentials and SMS bodies are excluded.
