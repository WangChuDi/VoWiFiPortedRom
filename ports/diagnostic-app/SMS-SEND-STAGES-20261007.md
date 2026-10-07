# SMS submission stages and actual native business tests,2026-10-07

Tool0.9.15/versionCode27 adds selected-IMS SMS submission diagnostics. API30
IMS0.4.9/versionCode43 and module0.9.8/versionCode19 are built with the modern
IMS0.2.5/versionCode41 and module0.2.8/versionCode10 in one isolated batch.
The application embeds both exact signed engine bundles. Earlier releases remain
preserved. Sources and phhusson attribution retain GPL-2.0 licensing.

## The observed problem

The exact installed0.9.14 release had three fresh WLAN/native-dispatcher-ready
samples before an authorized191 call. The call connected, transmitted276 audio
frames and played262 decoded frames, then received BYE200. The immediately
following native INFO request reached replacement IMS and received SIP202, but
no RP response was observed. Its framework failure arrived approximately120
seconds after the request. There was no native inbox row or notification.
The same Phone identity, IMS process and feature generation remained through
that failed call/SMS test. Its short-code-policy cleanup reload occurred afterward.

A separate no-preceding-call INFO control, after that cleanup reload, passed:
one accepted submission, four received/acknowledged reply segments, one merged
native inbox message and a Google Messages notification. This does not isolate
the call as the cause: the intervening reload and the network's state differ.
The earlier failure remains failed; SIP202 is not a sufficient delivery result.
Absence of an observed RP response does not prove that the network never sent one.

## Implementation

`common/SmsSendObservation.java` records fixed enums and numeric metadata for one
real sender invocation. The existing generator attaches it to `PhhImsSms`, its
pending transaction, SIP/RP responses, timeout and socket-write exception paths.
Existing `SmsSubmission` completion rules, wire requests,120-second deadline,
framework callbacks and receipt acknowledgements remain unchanged by this batch.

`StackTelemetry.Owner` exports only the latest invocation for that feature.
Old concurrent completions, the other slot and a retired feature cannot replace
that observation. The app validates an optional complete nine-field extension
against its existing current-process/owner provenance checks. Older producers
without this group remain supported by the new consumer. Update the tool first
because an old closed-schema consumer refuses added fields.

The new card distinguishes SIP acceptance, RP acceptance/rejection and typed
failure, including SIP_TIMEOUT versus RP_TIMEOUT. It describes a historical
network submission observation, not a new traffic test, recipient-delivery result
or proof of inbox/notification behavior. No body, PDU, number, SMSC/PSI, SIP header,
authentication material, message reference or caller token enters the new schema.

## Evidence

The consolidated API30/API31 builds, signing, exact embedded engines and retained
artifact checks passed. The validator executes42 new production Java/JSON
assertions plus the existing telemetry, reconnect, ABI, dispatcher and client
contracts. New cases include reversed SIP/RP arrival,202 without RP, RP without
final SIP, rejection cause, terminal late results, simulated independent owners,
retirement, malformed extensions and older-producer compatibility. These are
controlled host contracts, not physical dual-active-SIM or carrier tests.

The current phone's previous four installed APKs were backed up before one full
update. One idle client reload was part of installation. The module was then
actually rebooted; all three mounted and installed APKs, module metadata and tool
bytes matched the build. Three native-ready samples passed with the new initial
UNOBSERVED SMS schema, without another manual post-boot client reload or rebind.

The new post-boot191/INFO batch passed:321 transmitted and309 played audio
frames, normal connected call and BYE200; one native send, SIP202 plus RP
acceptance; four received segments and successful acknowledgements; one native
inbox row and a new Google Messages notification. Phone identity and IMS feature
generation stayed unchanged between call and SMS. The temporary short-code
policy was restored, using the existing cleanup reload only after all traffic
observations. The user was unavailable for an audible-speech check.

Closed reports, artifact identities and test-source pins are under
[`reports/20261007-sms-send-stages`](reports/20261007-sms-send-stages/summary.json).
Same-signature read-only instrumentation also passed in the actual diagnostic
application UID: empty-slot attribution, active native readiness, the new status
schema, full persistent replacement and the rendered SMS stage/scope card. It
made no call/SMS or engine mutation, preserved Phone/IMS identities and production
APK bytes, and removed its test APK. That UI check is recorded separately there. A passing
new run does not prove that the earlier intermittent RP timeout was repaired.

## Remaining goal requirements

Modern real-carrier registration/voice/SMS, positive modern app-UID replacement
actions, simultaneous real dual-active-SIM operation, audible-speech confirmation
and sustained unattended recovery remain unverified. This batch adds diagnostic
coverage and current-phone business evidence; it does not mark the full
Android11–17/application/dual-SIM objective complete.
