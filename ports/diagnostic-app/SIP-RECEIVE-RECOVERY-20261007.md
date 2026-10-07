# Captured-attempt SIP receive recovery (0.9.16)

This change repairs a source-level receive-lifetime gap in the API30 and modern
IMS generators. Before this change a TCP listener or UDP reader exception only
logged an error and ended the coroutine. The main connection could remain alive
and `imsReady` could remain true even though that inbound channel no longer read
network-initiated messages. The TCP listener also read one accepted peer to EOF
before accepting another peer; a quiet connection could delay a new connection.

This is a conditional defect in the source. It has **not** been established as
the cause of the earlier intermittent SIP202 / unobserved-RP timeout. A successful
business regression cannot establish that every intermittent timeout is fixed.

## Implementation

- `common/SipReceiveLoops.java` owns read-loop lifetime and a bounded TCP peer
  executor. MAIN / UDP EOF or error and TCP listener failure call the existing
  recovery path. Four accepted TCP peers can read independently; ordinary peer
  EOF/error closes that peer while the listener continues accepting.
- `sip_receive_source.py`, called after `sip_reconnect_source.py`, uses the exact
  captured connection and connection attempt. The existing handler rechecks both
  before retiring registration or scheduling recovery. Old readers cannot retire
  a newer connection attempt or another SIM's handler.
- REGISTER provisional responses retain the callback. A non200 final response
  throws a typed SIP failure; this retires the captured attempt even if the
  response arrives on an accepted TCP peer. Failure telemetry preserves the
  appropriate enum rather than replacing every failure with NETWORK_IO.
- `ims/Api30SipTcpServer.kt` tracks accepted descriptors as close-once ownership
  objects. Listener cleanup shuts down/closes all owned clients; an accept racing
  with cleanup either enters that owned set or closes its returned descriptor.
  This unblocks old quiet readers when a connection is retired. The Java helper
  does not itself own or close the Android listener.
- UDP responses are emitted only when there are reply bytes and the captured
  attempt is still current.

The framework SMS callbacks, system inbox/notification path, SMS wire encoding,
120-second submission timeout, APNs, modem and vendor image are unchanged. Pending
submissions are not resent by this repair. Existing phhusson/ims provenance and
GPL-2.0 attribution remain in the preserved source and generated adaptations.

## Verification and limits

`ports/compatibility/test-telemetry.py` runs the actual production Java helper
against local host sockets and deterministic concurrency cases. The 23 receive
checks cover EOF/error completion, a failing error callback, obsolete attempts,
another SIM's gate, listener failure, cancellation during accept, executor
rejection, the hard peer limit, released permits, delivery on a new peer while an
old peer is quiet, and shutdown of owned sockets. These are **host tests**, not
Android socket fault injection or carrier tests.

The isolated release batch builds both API30 and API31 variants, signs all payloads,
checks the diagnostic tool's exact embedded modules and preserves the older
0.9.15 artifacts. Device installation, actual module reboot and native191 / INFO
business reports are recorded separately under `reports/20261007-sip-receive-recovery`.
An unavailable human listening check remains unverified. Modern carrier traffic,
modern positive app-UID replacement and simultaneous real dual-active-SIM traffic
remain unverified.

The actual 0.9.16 boot and 191 media/BYE regression passed. The single following
INFO submission **failed**: SIP202 was observed, no RP acceptance or new inbox row
was observed, and no new Messages notification appeared. The retained log
metadata shows IMS FAILED at 120,079ms after framework submission and the sent
Intent error 155ms later. This supports the existing 120-second RP-wait timeout.
The runner had sampled telemetry immediately before that final callback, so its
last SMS snapshot still says WAITING_NETWORK_ACK and its failure counter is 0.
That earlier snapshot must not be presented as the final 120-second outcome.

Policy cleanup reloaded the Phone client only after business observations. The
separate post-cleanup snapshot is a new feature generation with reset SMS
counters; it cannot substitute for the failed generation's final observation.
No socket-error events were retained in the inspected fixed metadata window, but
that does not prove all readers were healthy throughout or that the network
delivered an RP response. 0.9.16 remains an experimental source repair, **not** a
release with a passing SMS regression.

At most four accepted TCP readers run per handler. Four quiet peers can still fill
that deliberate bound. Synchronous framework receive callbacks and possible late
RP-reference reuse are separate outstanding cases, not claimed fixed here.
