# Scoped API30 reconnect evidence

`summary.json` distinguishes the scoped passing results from the incomplete full
goal. `automatic-reconnect.json` contains both real Wi-Fi loss/recovery cycles;
`native-business.json` records one191 and one native INFO round trip, inbox and
notification metadata. `module-reboot.json` verifies installed/mounted payloads
and automatic startup readiness. Final read-only states are separate reports.

The before-watch failure, Wi-Fi-state sampler refusal, initial hot-install failure
and explicit service reload remain separate and have not been relabeled as passes.
`final-build.json`, `contracts.json` and `source-checksums.json` describe isolated
build and fixed-schema contracts. `first-build.json` identifies the earlier
candidate; its bytes differ from the final network-watcher build.

All JSON files are pinned by `SHA256SUMS`. No SMS/OTP bodies, phone numbers, raw
logs, endpoints, device serial, private owner journals, signing material or account
credentials are included. Internal process/elapsed-time metadata in some scoped
reports is not a claim of modern or dual-SIM verification.

See [the implementation and evidence limits](../../SIP-RECONNECT-20261007.md).
