# Version 0.9.12: scoped SIP reconnect and underlying Wi-Fi loss

The replacement stack now withdraws a session when its selected physical Wi-Fi
network disappears, and starts SIP connections with a generation-scoped handshake.
The diagnostic tool separates a fresh connection attempt from a historical SIP
response. A previous SIP 200 cannot establish current IMS registration.

## Changes

- `SipReconnectGate` serializes handshakes, retains the latest eligible deferred
  network, rejects canceled results and prevents restarted work after shutdown.
  Each IMS feature owns its own gate; no shared selected network is introduced.
- `EpdgSession` watches exactly the Wi-Fi network used for DNS/IKE/IPsec. Losing
  that network closes its session and reports an empty data-call list through the
  existing provider callback. Close/cleanup unregister the watcher; initialization
  rechecks the selected network and existing generation/subscription guards remain.
- Generated API30/API31 IMS resets REGISTER headers, digest, Call-ID and CSeq for
  a new handshake, scopes readers and callbacks to their attempt, and sets bounded
  TCP handshake response waits. This is not a UDP response-timeout implementation.
- The optional IMS telemetry extension records attempt, transport, fixed stage,
  failure class/stage, response time and scheduled retry time. New attempts clear
  old response/failure fields. The parser accepts legacy snapshots without this
  extension, requires complete validated fields when present, and exports no
  endpoints, credentials, SIM authentication material or SMS bodies.
- The tool adds a SIP connection/retry card. It labels scheduled retries as plans,
  and retains the independent current IMS/native-SMS/dispatcher observations.
  The older tool's strict parser rejects new extension fields, so update the tool
  before the new IMS producer.

Versions: diagnostic `0.9.12`/24, API30 module `0.9.5-trial`/16,
API30 IMS `0.4.6`/40, API30 IWLAN `0.3-network-loss`/3,
modern module `0.2.5`/7, API31 IMS `0.2.2`/38 and IWLAN `0.3.2`/34.
The modern bundle includes rebuilt services and the current controller, with
its existing pending-owner and recovery guards. Retained API37 fixture/helper
bytes and unresolved recovery records were not changed or replaced.

## Actual validation

Both API30 and API31 service variants and controllers compiled in an isolated
consolidated build. APK signatures, versions and both embedded engine ZIPs were
checked against the independently built bytes. The registry/schema contract
passed 37 checks; actual gate/attempt/parser/UI-format contracts passed 31 checks.
Original release artifacts were preserved. These checks are not carrier tests.

On the connected API30 raphael/VOXI profile, two real idle Wi-Fi off/on cycles
passed. Each observed registration withdrawal, a new connection attempt and
automatic WLAN voice/SMS/native-dispatcher recovery. Both kept the same IMS
process/owner and Phone process, preserved the selection transaction and restored
Wi-Fi. No reboot, Phone reload, client rebind or SIM reinsertion occurred during
these cycles. The second cycle briefly reported IMS registered while native SMS
was unavailable; the later sample recovered native SMS too. Registration alone
was not used as the success condition.

Afterward, one explicit-account 191 call connected with 303 transmitted and 288
played media frames, an active-call observation and SIP BYE 200. One native INFO
SMS to 85075 returned RESULT_OK/network error 0, with TX/OK 1, RX/ACK 4, no SMS
failure, one new merged system inbox row and a new messages notification. No
client rebind or Phone reload occurred before or between those traffic checks.
Human audible speech was not independently confirmed. Temporary short-code
policy was restored afterward with the existing idle Phone reload.

One later reboot verified active module version, installed APK bytes and actual
mounted APK bytes against the final module. WLAN IMS, voice/SMS capability and
native dispatcher recovered automatically. Three final read-only observations
remained ready with stable Phone identity and full persistent component selection.
This is bounded startup/reconnect evidence, not long-term reliability proof.

## Retained failures and limits

The pre-update phone had IMS/WLAN/SMS capability but native SMS/dispatcher false.
The first IMS/tool update required the existing registered-client rebind to
recover native SMS. It did not establish an automatic client-repair mechanism.

An initial sampler refused before changing Wi-Fi because MIUI stored its enabled
Wi-Fi state as 2 instead of the sampler's assumed 1. The calibrated short-loss
test then failed: native SMS was unavailable while IMS still reported REGISTERED.
After Wi-Fi restoration, the first IMS build eventually reconnected without a
Phone reload. This earlier outcome is retained separately from the new watcher.

Hot replacement of IWLAN/QNS/IMS initially left IWLAN/QNS status unavailable and
SIP attempts failing with NETWORK_IO at PLAIN_CONNECT, before REGISTER or a SIP
response. An existing same-owner idle Phone reload restored the new stack. This
observed recovery does not prove the precise stale-binding mechanism. The failed
install/startup report remains failed; staging and later tests have separate reports.

Real Android12–17 carrier/app action compatibility, original API37 recovery,
simultaneous dual active SIMs and broader mixed-provider interoperability remain
incomplete. A shared API31 build is not proof of successful traffic on every newer
Android version. The complete requested goal remains active.

See the [checksummed fixed-metadata reports](reports/20261007-sip-reconnect/README.md).
The implementation continues to use the preserved attributed `phhusson/ims`
source under GPL-2.0; [third-party attribution](../android11/THIRD_PARTY.md) and
upstream licenses remain included. No APN, modem/vendor, CNE or kernel changes
were made in this work.
