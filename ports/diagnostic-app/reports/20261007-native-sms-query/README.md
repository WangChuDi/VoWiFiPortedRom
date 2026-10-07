# Independent IMS UID SMS query evidence, 2026-10-07

Closed metadata only. No credentials, raw logs, subscriber identifiers, owner
tokens, message bodies, PDU or signing material. Original failures remain failed.

Build/signing and byte-exact engine embedding passed. 164 production Java worker,
gate/activity/reader host assertions use Android/Binder stubs. 63 existing consumer
and46 new query assertions passed. These are not device or real dual-SIM proofs.
source-checksums pins the production build inputs plus the separately built final
instrumentation sources. ui-first-tested-source preserves the first test's exact
compiled Java bytes; ui-build-first records its digest.

API30 initial hot update failed with IMS DOWN/NETWORK_IO and own-UID FALSE. One
existing idle Phone reload restored three ready samples and staged the exact
module. Actual reboot passed all three mounts/current APKs, owner retention and
three own-UID TRUE/native-dispatcher-ready samples, without additional reload.
First UI test failed its readiness assertion. Separate readonly root observations
passed. Final same-signature instrumentation retained the strict assertion and
passed real application UID, empty-slot isolation, active IMS own-UID query,
WLAN voice/SMS/native readiness, full persistent selection and rendered scope
disclaimer. Phone/IMS identities and production bytes stayed unchanged; test APK
was removed. The first UI failure's cause is not established by the later pass.

API37 readonly-system startup was not observed and was explicitly finished.
Writable attempts retain startup/ADB-root/package-service/LOADED-state/path-check
failures. APK update paths can include tilde characters. PackageManager Binder
returned Broken pipe; a later read-only crash observation timed out. These do
not establish that the new IMS code caused a system crash. Original userdata was
never wiped or replaced by a new baseline. Original journals/helpers were retained.

With4GiB guest RAM, three real IMS application-UID queries returned OBSERVED FALSE
for the emulator's fake SIM, and a nonroot provider request was refused. This
proves the observation protocol/permission context, not real carrier service.
The driver restored IMS/APK UID/permission inventory/navigation and102 records,
but its final state read failed; that report remains failed. Subsequent fresh
restored-state checks also failed. The4GiB resource monitor hit its private-memory
limit and confirmed owned-process termination; later ADB reported the device
missing. Complete final re-verification of all3original APKs and old-producer
unavailable reporting is unproved. Earlier exact IMS/UID/permissions/navigation/
102-record/helper restore observations remain separate. Phone identity across
the earlier update is unproved. Resource monitor caps are limits, not measured
stability; each monitor records actual samples and owned-process termination.

No new call/SMS was sent. Earlier191/native INFO/inbox/notification evidence is
separate. Modern real-carrier voice/SMS, modern diagnostic activity own-UID
positive controls, sustained automatic rebinding and simultaneous real dual
active SIMs remain unverified. The full user goal remains active.
