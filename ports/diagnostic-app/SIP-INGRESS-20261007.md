# Registered SIP receive-path metadata, candidate 0.9.17

`SipReceiveObservation` adds a per-IMS-attempt, selected-owner observation of the
registered MAIN/TCP/UDP receive loops. The source transformation attaches it to
the production Kotlin receiver and MESSAGE/RP handler in both API30 and API31
builds. The pure-Java helper is shared. The nested optional `sip_receive` protocol
has a closed key set, typed enums, bounded counters, attempt and monotonic-time
validation. Older producers are accepted without manufacturing receive data.

It records loop states, parsed-message/MESSAGE/datagram counts, accepted/current
TCP peer counts and RP DATA/ACK/ERROR/other/decode-error/unmatched counts. Owner
replacement or SIP-attempt retirement freezes old observations. Peer close is
counted once. FAILED states survive retirement; remaining RUNNING states become
ENDED. No SIP headers, endpoints, payloads, PDU, message reference or SMS text are
exported.

Counting starts **after the final registration response**, when the registered
receive loops and MESSAGE callbacks are installed. Initial authentication and
REGISTER handshake reads are outside this counter's scope. RUNNING means the
task has started; it is not proof of thread liveness, carrier delivery, system
SMS acceptance or inbox/notification delivery. Metadata alone does not fix or
diagnose the retained SIP202/no-RP timeout.

The isolated build passed API30/API31 compilation, signing, exact embedding and
source/frozen-artifact checks. Production host contracts passed39 receive-schema
checks,23 receive-loop checks,37 telemetry checks,31 reconnect checks and42 SMS
submission checks. These are host contracts, not carrier tests.

Candidate versions: tool0.9.17, API30 IMS0.4.11/module0.9.10-trial and modern
IMS0.2.7/module0.2.10. The engines are embedded unchanged in the subsequent0.9.18
UI APK. **The newer engines were not installed on the phone.** Live receive-path,
SMS regression and Android17 validation remain pending. The phone stays on the
previous0.9.16 engines while UI/non-root feasibility work proceeds.

See [the UI build and deployment record](MATERIAL3-SHIZUKU-20261008.md) and the
retained [previous receive-recovery/SMS failure](SIP-RECEIVE-RECOVERY-20261007.md).
