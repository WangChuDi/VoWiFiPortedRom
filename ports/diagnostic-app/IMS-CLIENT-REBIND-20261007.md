# IMS client rebind: source, measured recovery and remaining work

The calibrated MIUI dispatcher can remain service-up/registered but SMS-capable
false after a network/phone lifecycle transition. Independent MMTEL SMS=true and
a new REGISTER200 did not repair that earlier failure. A controlled framework
feature readiness transition now offers a separate recovery mechanism.

## Changes

* Move the Java capability bridge into an auditable attributed source file.
  Preserve upstream VOICE|SMS implementation support, honor explicit IWLAN
  enable/disable changes, and publish zero when unregistered. Advertised support
  never guarantees operator acceptance of any particular SMS.
* Expose actual feature state, bind/capability counters, generation and pending/
  completed rebinding through a separate root-only provider method. Callback
  count reflection on tested MIUI remains unavailable. Count returned notification
  calls independently of client delivery. Volatile lifecycle fields cover Binder
  reads versus main/SIP-thread writes.
* Replace the fixed READY getter with the base feature state while retaining
  bootstrap READY. Rebinding changes only the live feature's state, then replays
  its actual registered capabilities. Ownership/removal/registration/call guards
  run before and after the delay; cooldown is30seconds.
* Tool0.9.11 adds a distinct button, bounded root worker, independent metadata
  validator and parallel read-only client snapshot. Queued requests and completed
  state transitions are separate. PID/boot/nonce/selected tuple/freshness are
  checked; unknown, refused or timed-out observations never claim recovery.

## Measured physical results

All precursor results below use the exact IMS0.4.4 SHA shown below, not the new
IMS0.4.5 provenance build.

| Test | Result |
| --- | --- |
| Live registered dispatcher failure | Before: native support=false, software up/reg=true and SMS=false |
| Rebind without install or Phone reload | Two fresh samples: native support=true and software up/reg/SMS=true; phone/IMS identities and feature generation unchanged |
| Native INFO SMS after that recovery | RESULT_OK, network error0; TX1/OK1, RX4/ACK4, no failure; one native inbox message and Messages notification |
| Short-code test policy cleanup | Original policy restored; Phone reload happened only after all preceding traffic/dispatcher observations |
| Explicit selected VOXI PhoneAccount191 call | Connected;320 uplink frames,293 frames handed to audio playback; active call, BYE200 and final idle; dispatcher stayed ready before/during/after |
| Earlier unscoped CALL Intent attempts | Failed, no connected/media proof; no subsequent SMS sent by those combined runners |

Default outgoing account mapped to the VOXI subscription, while default voice
subscription did not. Selecting the actual account through Telecom preceded the
successful call. This does not prove that mismatch alone caused the earlier
failed intents. PhoneAccount identifiers were neither retained nor exported.
Audible speech is still not confirmed.

The new0.4.5 hot install and tool0.9.11 signature/installed hashes were verified.
Its client snapshots correctly showed unregistered/last-notified-mask0, while
IWLAN was negotiating and REGISTER transmission remained0. The100-second startup
readiness run failed and must remain failed; it did not complete a positive root
worker test. The matching module was separately backed up and staged. The actual
subsequent reboot loaded all three matching APKs, opened the child tunnel and
automatically restored WLAN registration and native dispatcher readiness.

The final exact tool0.9.11/module0.9.4/IMS0.4.5 consolidated run **passed**:
the root worker accepted and observed a completed rebind with stable phone/system
processes; an explicit VOXI-account191 call connected with324 uplink frames and
316 frames handed to audio playback; hangup received BYE200. The following native
INFO sent successfully with network error0, TX1/OK1 and RX4/ACK4, one assembled
inbox message and a Messages notification. The feature generation and phone/owner
identity stayed unchanged between call and SMS. Dispatcher queries before,
during and after both remained ready. Policy-cleanup Phone reload happened only
after all business observations, and the original policy was restored.
See [final business report](reports/20261007-ims-client-rebind/final-call-sms.json)
and [reboot report](reports/20261007-ims-client-rebind/reboot.json).
The separate final check after policy cleanup still observed WLAN voice/SMS,
native SMS support and the calibrated dispatcher ready, with all calls idle.
No additional repair was requested by that check; see
[final state](reports/20261007-ims-client-rebind/final-state.json).

## Artifact identities

| Artifact | SHA-256 |
| --- | --- |
| Precursor IMS0.4.4 | `0a3145ced65bafca50175531243f1ef36c56e8c056f3671a393840ac12232271` |
| IMS0.4.5 | `3f971f5ff82c9942fa2e07a3f34f913315e922582bfbaab0913b499d6998ca9d` |
| Tool0.9.11 | `de22787f736555d5efe9260fab3559f04d5f03ffaec3728c0b06564cc0205c3c` |
| API30 module0.9.4 | `07084b8d05ee174319369858160d3feb7cc17e4822e8cf9ae659aa03e61a7d7d` |
| Retained modern module | `ffb9551e50a8ae11c0da4ad562646e2e181aa95ff0ec6c79cfcb4933d854705a` |

The isolated source build compiled all API30 APKs/controller, signed the complete
module/tool, checked embedded engine bytes and passed19 bridge plus39 provenance
contracts and the existing diagnostic/dispatcher checks. Retained releases and
the repository's frozen out artifacts were not overwritten.

Initial writable-system modern smoke was refused before launch by its disk
preflight. A temporary read-only-AVD batch then passed API33 diagnosis/refusals;
API36 returned no active subscription and exposed an overly strict test assumption
requiring a subscription-specific native-SMS result. That failure is retained.
The harness now accepts an explicitly empty slot without manufacturing an SMS
result, and runs the same APK in a separate batch. This is diagnostic/refusal
coverage, not real modern carrier voice/SMS or dual-SIM validation. The corrected
separate batch passed on both API33 and API36, including root/nonroot rejection
of the API30-only client action, and both owned guests terminated.
See [modern runtime report](reports/20261007-ims-client-rebind/modern-runtime.json).

Source remains GPL-2.0, with the preserved `phhusson/ims` baseline and the original
Suiying6023 architecture provenance; see [third-party notices](../android11/THIRD_PARTY.md).
No vendor/modem/APN/CNE/IPsec-APEX replacement is part of this change. Long-term
automatic dispatcher stability, modern real-carrier adaptation, simultaneous
dual active SIMs, positive app-own-UID UI regression and the retained API37
fixture recovery remain incomplete.
