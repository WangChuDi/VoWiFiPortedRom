# AKA synchronization, post-boot SMS and outgoing SIP dialog work

This work keeps the full target: selectable IWLAN/QNS/IMS replacement, actual
calling and native SMS on Android11–17, and two simultaneously active SIMs.
The API30 device has one active VOXI SIM. Host contracts, emulator diagnosis and
one successful call are separate evidence, not completion of that target.

## Source changes

* `stack/ims/AkaResponseCodec.java` validates USIM DB success and DC AUTS framing
  with fixed errors. The source generator handles SIP AKA synchronization failure
  with at most two resynchronization requests, an empty-password Digest and AUTS,
  then a fresh network challenge. SIM keys and authentication responses stay in
  memory and are never exported. The IKE implementation is not replaced by this
  SIP authentication change.
* Reliable provisional SDP is published before PRACK is sent. A PRACK 1xx waits;
  a matching 2xx resumes the pending provisional response; failed PRACK retains
  its own status. Exact ACK method matching no longer also matches PRACK.
* Registration and `ImsSmsImplBase.onReady()` schedule one bounded delayed
  capability republication. The current feature/subscription/gate is revalidated
  and only actually registered capabilities are published. Removed features
  cancel callbacks. This does not fabricate registration or enable SMS policy.
* Tool0.9.9 adds `VoiceDialogState` to route PRACK, UPDATE, ACK and BYE using the
  early/final dialog Contact and reversed Record-Route, including strict routing.
  It owns the dialog's local CSeq, preserves the INVITE sequence for ACK and
  removes an old Via branch before creating a new request transaction.
* `SdpSessionVersion` preserves the SDP origin and advances its version when
  creating a changed offer. Existing codecs and the existing precondition policy
  are retained. Codec interoperability and QoS enforcement are not proved by
  this change. References: [RFC3261 section12](https://www.rfc-editor.org/rfc/rfc3261.html#section-12),
  [RFC3264 section8](https://www.rfc-editor.org/rfc/rfc3264.html#section-8).
* Fixed response metadata attributes SIP errors to CSeq methods without logging
  headers, destinations, SDP addresses or message bodies. The new synthetic batch
  checks20 AKA cases,12 metadata/privacy cases and20 dialog/SDP cases. Modern
  artifact checks also require all new helpers in DEX.
* Module packaging can reuse separately signed APKs only after entry equivalence
  and signature verification. Attribution and GPL notices are retained. Modern
  and API30 builds share the IMS generator while using isolated output trees.

## Actual observations before0.9.9

The temporary AKA/PRACK candidate registered after a real DC synchronization
failure and a renewed challenge. One authorized191 call passed INVITE/PRACK/
UPDATE, reached an active Telecom connection, transmitted183 RTP frames and
played217 decoded frames; hangup received BYE200. An INFO SMS to85075 passed
native send, four receive/ack events, one assembled inbox row and notification.
The original installed IMS APK was restored after that temporary test. Human
confirmation of intelligible speech was not obtained.

Persistent0.9.7 booted with WLAN registration and advertised SMS capability but
the native SMS Binder returned false. A bounded empty capability-change request
republished existing capabilities and changed the native query to true. This
proves a repair at that boundary, not the precise original framework cause.

Persistent0.9.8 then passed an actual reboot: module/installed APK bytes matched,
the IMS UID and original transaction snapshots stayed unchanged, and two
consecutive observations showed native WLAN SMS support without another manual
reload. Real INFO sending, four received/acknowledged segments, native inbox and
notification passed. Its final191 call still failed: PRACK200 was followed by
**UPDATE488**, with no Warning or Q.850 explanation. The earlier successful
candidate therefore does not prove reliable calling for0.9.8.

Tool0.9.8 separately passed concurrent API33/API36 boot and compiled root
read-only/refusal checks. Those runs selected no replacement engine and sent no
carrier traffic. A first smoke startup failed with an ADB invocation exception;
the retry passed after bounded identity/readiness checks. Both owned guests were
terminated. No claim about the first startup's exact cause is made.

An intermediate0.9.7 installer check incorrectly expected `customize.sh` to
remain after Magisk installation; it failed although the runtime payload was
loaded. Magisk removes that install-only script. Later verification checks its
absence and all remaining bytes explicitly. The failed observation is retained.

## Current0.9.9 validation

The [consolidated build and actual traffic results](reports/20261007-aka-sms-dialog/README.md)
are recorded separately. The real boot and191 call passed, including UPDATE200,
INVITE200,213 transmitted frames,218 played frames and BYE200. The first SMS after
that call returned RIL_INVALID_STATE(103), without invoking replacement IMS.
After the test's cleanup phone reload, a separate native-readiness-gated INFO
retry passed send, four receive/ack events, one inbox row and notification. The
first combined traffic result remains **failed**; reliable SMS routing after
calls has not been proved or repaired by this retry.

The new signed tool also passed concurrent API33/API36 compiled root diagnosis
and refusal checks; no engine was selected and the owned guests ended. Do not
infer a carrier pass from a successful build or a passed reboot/readiness result.
API37 original recovery remains
pending; Android12–17 real carrier traffic, current own-app-UID UI validation,
long-term boot/call stability and simultaneous dual-active-SIM validation remain
incomplete. Original root-private owner and provider snapshots are retained and
are never recaptured from the selected replacement.
