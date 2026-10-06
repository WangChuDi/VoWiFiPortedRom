# Process-local diagnostic status (schema 1)

Tool 0.6.0 / module 0.8.0 adds a read-only status provider to each replacement
APK. It runs in the service's default process, so the registry describes that
process's actual owner rather than inferred log history. It does not start an
IKE session, call, SMS test or registration. A provider query can instantiate an
APK process; the tool requires an existing PID and rejects a changed PID.

## Access and provenance

The authorities are `dev.codex.vowifi.iwlan.status`,
`dev.codex.vowifi.qns.status`, and `me.phh.ims.status`. The only supported call is
`status` with `channel:slot:subscriptionId:16-hex-nonce`. Android privileged-phone
permission guards acquisition; the provider independently requires Binder UID 0.
Shell authorization to `su` is not authorization for an unprivileged Binder call.
All query and mutation methods reject access.

Responses contain a fixed metadata schema: selected slot/subscription, nonce,
process PID, boot count, monotonic sample time, authorization and observation
flags. The registry rechecks selected subscription authorization. Missing owners
are explicitly unobserved. The tool validates schema, exact tuple/nonce/PID/boot,
elapsed timestamps, lifecycle consistency, and a five-second freshness bound.
Three providers are queried concurrently with bounded subprocess waits; timeout
or inconsistency is unknown, never success. The UI samples the public framework's
registration and capabilities independently of these replacement-service states.

## Owner lifetime and evidence

Each channel/slot has one current owner with a monotonically allocated process
generation. Replacement retires its authority immediately; late callbacks cannot
change the new owner's record. Every update and immutable snapshot uses the same
lock. Closing retires permanently and clears active flags/interface/transforms;
failure retains only an enumerated last stage. QNS checks the selected tuple
around eligibility calculation, publishes again on a new tuple, and serializes
close against publication.

IWLAN records DNS answer count, negotiated IKE/child callbacks, successfully
applied transform counts and interface/address/DNS/P-CSCF counts. The diagnostic
also compares the owned interface with the selected subscription's IMS network.
Held local resources do not prove the peer still passes traffic. IMS records the
framework-published registration phase, REGISTER attempts/latest response, SMS
arrival/framework acknowledgements and send outcomes, and RTP transmit/audio
playback frame counts. Counters belong to a feature generation and are historical;
they are not per-message receipts or proof of future delivery/audible speech.

No endpoint addresses, subscriber identifiers, authentication material, SIP
headers, message text or PDU enter this registry/provider. Existing upstream
logging remains subject to the port's redacting logger; status collection uses
the explicit schema and does not export raw phone logs.

## Validation boundaries

`ports/compatibility/test-telemetry.py` executes the production registry and JSON
validator on the JVM, covering ownership, concurrent old callbacks, terminal
retirement, timestamp/provenance/schema validation and SMS arrival versus ACK.
It does not execute Binder permission checks or carrier traffic. Those require
root-versus-shell device queries, real native SMS/inbox/notification checks and
the authorized 191 call. Android12–17 static build/linkage checks do not enable
modern installation profiles or replace actual modern/dual-active-SIM testing.

Sources retain GPL-2.0 attribution; IMS remains derived from the preserved
`phhusson/ims` snapshot documented in the parent port.
