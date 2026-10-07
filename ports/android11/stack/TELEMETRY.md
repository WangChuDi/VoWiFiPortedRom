# Process-local diagnostic status (schema 1)

Tool 0.6.0 / module 0.8.0 adds a read-only status provider to each replacement
APK. It runs in the service's default process, so the registry describes that
process's actual owner rather than inferred log history. It does not start an
IKE session, call, SMS test or registration. A provider query can instantiate an
APK process; the tool requires an existing PID and rejects a changed PID.

## Access and provenance

The authorities are `dev.codex.vowifi.iwlan.status`,
`dev.codex.vowifi.qns.status`, and `me.phh.ims.status`. The service-status call is
`status` with `channel:slot:subscriptionId:16-hex-nonce`. Android privileged-phone
permission guards acquisition; the provider independently requires Binder UID 0.
Shell authorization to `su` is not authorization for an unprivileged Binder call.
CRUD methods reject access. The explicit IMS client method below is separate.

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

## IMS client metadata and explicit rebinding, tool0.9.11 / IMS0.4.5

`me.phh.ims.status` additionally accepts `capabilities` and `client-rebind`, with
`ims:slot:subscriptionId:16-hex-nonce`. Both require root. They use a separate
closed schema, preserving the old `status` response for older tools. The new
response includes PID/boot/nonce/sample time and feature generation; the tool
validates them and process start identity before exporting only safe counters.

Metadata separates enabled capability bits from the last notified bits, counts
SMS-ready/capability-enable/disable events, and records pending/completed rebinds.
Unregistered features notify zero. Explicit IWLAN disables are respected during
replay. `notification_returns` counts returned notification calls, not remote
delivery. OEM callback reflection failure remains unknown, not zero.
In IMS0.4.5/0.4.6 the historical `sms_session_idle` field only described the
feature's voice call state. From IMS0.4.7 it additionally requires empty TX/RX
pending maps, no active SMS construction/completion/ACK write, and a five-second
quiet window. It remains a boolean idle observation, not a transaction counter.

Rebinding is supported only on the exact calibrated API30 raphael framework.
The controller owner, live23415 subscription, registered feature and all active
subscriptions' call states are checked again in root and in the service. The
feature publishes INITIALIZING then READY after750ms, rechecks its ownership and
registration, and replays its actual enabled capabilities. Removed features never
return to READY. Requests have a30-second cooldown. Acceptance only means queued;
the root worker separately observes completion and stable phone/system processes.
No REGISTER, phone reload, SIM toggle, call, SMS or APN change is performed by
this action. A subsequent native support/dispatcher query checks recovery without
claiming actual traffic success. See [current evidence](../../diagnostic-app/IMS-CLIENT-REBIND-20261007.md).

## Service-side native SMS recovery, tool0.9.13 / IMS0.4.7

The optional, all-or-nothing `native_watch_*` group extends the capabilities
protocol. Update the diagnostic consumer before the new IMS producer: older
closed-schema consumers reject the added keys. The status includes a fixed enum,
TRUE/FALSE/UNKNOWN native state, query/request counts, monotonic observation and
request timestamps, and whether the query ran as the IMS application's own UID.
It contains no subscriber identifiers or message content. A fresh provider
snapshot does not make an old native observation fresh; the UI shows its age.

The IMS manifests declare `android:usesNonSdkApi="true"` because the verified
API30 ROM denies the ISms method to the app despite granted phone permissions.
Android honors this declaration for system/updated-system apps, not ordinary
user apps. It changes non-SDK access for this installed IMS application; Binder
permissions and all profile/owner/registration/idle guards still apply. No global
hidden-API setting is changed. See the [AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/ca6f81d39525174e926c2fcc824fe9531ffb3563%5E%21/).

Only the already calibrated API30 raphael framework SHA enables recovery. The
service reads `ISms.isImsSmsSupportedForSubscriber` on one background worker,
as its own application UID. Missing Binder, denied permission, reflection errors
and timed-out results are unknown, never false. A 2.5-second result deadline
invalidates late callbacks. A stuck Binder remains bounded to that single worker;
the watch stays unknown/stale until the call returns or the service is recreated,
rather than accumulating blocked threads or fabricating a negative sample.

The selected owner/subscription and current feature must still match. Registration,
READY state, enabled SMS capability, all active calls idle and all feature SMS
sessions idle are required. Eligibility must last20 seconds, followed by three
negative observations spaced at least3 seconds. Registration withdrawal/removal
invalidates old eligibility and negative samples. An accepted request uses the
existing INITIALIZING/READY client rebind, retains the SIP registration/tunnel,
and is counted as a request rather than delivery proof. Automatic requests are
limited to three per feature lifetime, with120 seconds between requests; the
existing explicit rebind keeps its separate30-second cooldown.

`check-native-sms-recovery.py` compiles the actual Java gate, activity tracker and
reader against controlled host Android/Binder stubs. Its permission/context and
failure tests are host contracts, not device UID/permission proof. Actual service
queries, native software dispatcher state, and real SMS delivery/notification
must be checked independently. Modern IMS builds include an unsupported watch
snapshot; the API30 calibrated recovery is not advertised as an API31–37 fix.

## Independent native SMS query, tool0.9.14 / IMS0.4.8 and modern0.2.4

The IMS provider accepts `native-sms` with the same root-only request tuple
`ims:slot:subscriptionId:16-hex-nonce`. Its separate response channel is
`ims-native-sms`. This does not require an active replacement lease or registered
feature. API31–37 allow read-only observation; API30 retains the exact calibrated
profile. No registration, SIM authentication, traffic or rebinding is requested.

The fixed fourteen-field schema contains schema/channel, slot/sub, nonce/PID/boot,
sample/query monotonic timestamps, result, status, self_uid, read_only and scope.
Result is TRUE/FALSE/UNKNOWN. Status is OBSERVED/UNAVAILABLE/TIMEOUT/BUSY/UNSUPPORTED;
only OBSERVED can carry a known result, and it requires the IMS own UID. Scope is
always IMS_OR_RADIO because the native interface can include legacy radio support.
The tool must not interpret it as replacement software-dispatcher readiness.

One daemon worker checks the unique ready slot/sub before and after the ISms call.
The 2.5-second deadline discards late results; a stuck call keeps the worker BUSY
instead of spawning more threads. The consumer checks current ownership, nonce,
boot, sample window and stable IMS process identity. Its read-only result remains
independent of the older `native_watch_*` recovery group and dispatcher window.
See [build, device evidence and limits](../../diagnostic-app/NATIVE-SMS-QUERY-20261007.md).
