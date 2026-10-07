# 0.9.16 captured-attempt receive repair — experimental

The source repair, host receive contracts, API30/API31 build/sign/embed batch,
API30 installation and actual whole-module reboot passed. Native191 voice media
and BYE200 passed. **The following native INFO SMS regression failed**; this
evidence must not be treated as a passing SMS release.

`native-business.json` records one call and one SMS, SIP202, unobserved RP, zero
new inbox rows and no new Messages notification. `failure-timeline.json` records
the matching token's IMS failure 120,079ms after framework submission and the
framework error155ms later. Together with the production120-second timer, this
supports an RP-wait timeout. The runner's last telemetry sample predates that
final callback and still says WAITING_NETWORK_ACK; it is not a final state sample.

Policy cleanup occurred after traffic observations. `post-cleanup-observation.json`
is a healthy, new feature generation whose SMS counters have reset. It does not
prove the previous pending request accepted or rejected RP. No socket-error events
were retained in the inspected fixed metadata window; this is not proof of reader
liveness or network delivery throughout that window.

`receive-loop-contracts.json` covers the actual production Java helper with host
sockets, not Android socket fault injection. `module-reboot.json` includes initial
boot transients followed by three stable ready observations and exact mounted and
installed payload hashes. No manual postboot Phone reload was needed before traffic.

All diagnostic app Java inputs are byte-identical to0.9.15; its version/embedded
engine payloads changed. The prior own-UID UI trial was not repeated, and no new
own-UID UI pass is claimed. Eleven changed built source inputs are pinned in
`source-inputs.json`. The older0.9.15 artifacts and on-device APK backups remain.

Human audible speech, modern real-carrier voice/SMS, modern positive app-UID
replacement and simultaneous real dual-active-SIM business remain unverified.
See [source and limits](../../SIP-RECEIVE-RECOVERY-20261007.md).
