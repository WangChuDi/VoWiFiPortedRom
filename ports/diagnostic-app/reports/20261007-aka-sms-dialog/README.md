# Consolidated build and actual API30 results, 2026-10-07

The current physical deployment is tool0.9.9 / API30 module0.9.3 / IMS0.4.2.
Original provider/owner snapshots remain retained. The phone is idle after test
cleanup; temporary SMS short-code policy was restored. No APN, vendor or modem
firmware was changed.

| Evidence | Result | Boundary |
| --- | --- | --- |
|0.9.9 build/sign/assets | passed | API30 and modern built separately; embedded ZIPs exact;52 synthetic contracts;7 modern framework samples linked |
|0.9.9 actual boot | passed | New boot, exact module and installed APKs, same IMS UID/original snapshots; two WLAN/native-SMS-ready observations |
|0.9.9 first191 call | passed | PRACK200, UPDATE200, INVITE200; active Telecom;213 transmitted/218 played frames; BYE200/idle |
|0.9.9 first SMS immediately after call | failed | Result103 / RIL_INVALID_STATE; replacement IMS transmit count0, no new inbox row or notification |
|0.9.9 SMS after previous cleanup reload | passed | Native readiness checked before sending; INFO send RESULT_OK;4 received/ack events;1 inbox row and notification |
|0.9.9 API33/API36 simultaneous smoke | passed | Actual boot and compiled root diagnostic/refusal checks; no replacement engine selection or carrier traffic; owned guests ended |

**`tool099-combined-traffic.json` remains failed.** The separate successful retry
does not change that first result or establish reliable automatic SMS routing
after every call. Its cleanup reload is explicitly recorded. The application
cannot display unseen carrier stages as healthy from capabilities alone.
Actual intelligible speech still awaits human confirmation.

The earlier temporary AKA/PRACK candidate had one full traffic pass, followed by
exact restoration of the original APK. Persistent0.9.8 separately passed actual
boot/native SMS but its call failed with UPDATE488. These results are retained
alongside the current release, rather than replaced by later successes.

The old0.9.6 production artifacts remain frozen outside this new isolated build.
The final post-cleanup read-only record confirms the exact installed IMS/tool,
full persistent selection, original evidence unchanged, idle call states and
current WLAN/native SMS support. Metadata-only querying additionally found one
recent native inbox reply from the authorized85075 short code on the tested
subscription. No body column or body filter was used in that executed query.
The shared source now targets0.9.9; use its new build outputs for reproduction.
Current APK/ZIP SHA-256 values are in `tool099-build.json`. Modern ABI linkage is
static sample evidence, not actual Android12–17 carrier support. API37 original
recovery, current own-app-UID UI validation, long-term stability and simultaneous
dual-active-SIM validation remain incomplete.

## Safe records

All included records contain fixed statuses, counters, permitted component
metadata and artifact hashes. SMS bodies, subscriber identifiers, SIP headers,
SDP addresses, authentication material and signing keys are excluded. Original
root-private transaction-file hashes were removed from the published boot record.
