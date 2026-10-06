# Optional persistent components: implementation and device evidence

Tool0.7.0 / module0.9.0. Device: raphael, MIUI V12.5.1.0.RFKMIXM,
Android11/API30, VOXI23415, one active SIM in slot1/sub1. The original
working tool0.6.0/module0.8.0 and earlier companion artifacts remain preserved.
The three replacement service APKs are unchanged from the previous milestone.

## Configuration support

Bits are IWLAN=1, QNS=2, IMS=4. All nonempty masks1–7 can be retained after
an ACTIVE trial. Retention requires matching original provider/selected-file
identity and backup hash, plus live and persisted provider values. Unselected
values must match the original snapshot. It does not certify carrier traffic.

Supervision checks selected processes only. IWLAN mode remains coordinated by
bit1; companion conflict ownership and SMS policy restoration use bit4. Boot
examines all active IMS owners. The UI fills its checkboxes from the recorded
mask and requires the new controller capability for partial retention. Changing
selection requires restoration, so replacement values never become an original.

The isolated Android shell fixture passed masks1–7 retention and boot recovery,
negative retention backend/disk drift and PREPARING checks, another owner's
baseline/token/lease preservation, stale-token and same-slot-conflict rejection,
last-IMS-owner companion coordination, and selected-only process supervision.
These fake carrier/platform fixtures do not establish live dual-SIM behavior.

## Actual mixed selection: mask5

The trial selected replacement IWLAN and IMS, leaving the QNS override at its
original empty value. Initial bounded samples lacked an attributed IMS network
and valid replacement status. A later trial with one explicit idle framework
reload produced child-open IPsec, two P-CSCF addresses and replacement IMS
REGISTER status200, with public WLAN voice/SMS capabilities. Matching slot/sub
and boot lease had about74 seconds remaining. Retention verification then passed.

This successful result rules out a categorical requirement to replace QNS on
this profile. It does not establish why every initial observation failed:
the provider's authorization flag includes transient SIM readiness, and initial
samples did not record every authorization condition. Neither network failure
nor a QNS policy rejection is proved by the initial missing state.

A separate mask5 persistence test then rebooted the phone. The saved selection
and enabled marker remained; early snapshots were unauthorized during boot
renewal, followed by valid current-boot ownership, child-open IPsec, P-CSCF2,
IMS200 and WLAN voice/SMS without another manual post-boot reload.

The first authorized INFO→85075 after that boot passed:

* One send result success/error0; telemetry delta TX1/OK1/failure0.
* Four incoming segments, four native framework ACKs and four corresponding
  accepted RP responses/SIP202; telemetry delta RX4/ACK4/failure0.
* One new native inbox record and a new Messages notification.
* The current phone's latest historical dispatcher observation was
  service-up=true/registered=true/SMS=true.
* Temporary diagnostic short-code policy restored to its original0; the cleanup
  idle reload was issued after the successful traffic test.

An authorized191 call subsequently connected with SIP200. Sampled counters
increased by243 RTP transmit frames and243 decoded audio playback frames.
In-call reload and rollback were refused, preserving phone PID and transaction.
Hangup succeeded with BYE200 and both slots idle. Actual audible speech was not
human-confirmed; frame counts do not prove listening quality.

## Boundaries and final state

The experiment restores and retains the working full mask7 after testing. An
explicit idle reload is used when restoring selections because configuration
equality alone does not prove that the framework has created the replacement
IMS feature. Short-lived subscription/binding transitions were observed.

Only mask5 has additional live mixed-selection boot/traffic evidence here.
Other masks have configuration/fixture coverage, not equivalent carrier tests.
Android12–17 installation engines, two real active SIMs, long-term behavior and
human audio confirmation remain pending. No vendor/CNE/JNI, modem or APN changes
were made. No message body, subscriber identity or authentication data is included.

Sources retain upstream Suiying6023/VoWiFiPortedRom references and the preserved
phhusson/ims attribution/GPL-2.0 license. See [TRANSACTIONS.md](TRANSACTIONS.md),
[TELEMETRY.md](TELEMETRY.md) and [THIRD_PARTY.md](../THIRD_PARTY.md).
