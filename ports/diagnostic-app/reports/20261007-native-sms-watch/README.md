# Native SMS service-side watch, 2026-10-07

Closed metadata only; no SMS bodies, credentials, subscriber identities, raw
logs, owner tokens or signing material. Original failures remain failures.

Initial hot update left IWLAN/QNS unavailable and IMS DOWN; the bootstrap reload
restored WLAN registration, but the new own-UID ISms read stayed UNKNOWN.
unknown-context.json records granted phone permissions and the exact hidden-API
denial target. The corrected IMS declares usesNonSdkApi as a system app.
application-info.json reads actual installed ApplicationInfo as root; it is
package metadata, not service-UID Binder proof. Separate capabilities snapshots
in installation-state/idle-reload/post-cleanup/reboot contain that service proof.

manifest-host-field-failure.json retains the host parser failure after successful
installation/staging. Its guessed dumpsys flag field is not evidence the flag
was disabled. Fresh direct ApplicationInfo and installation-state supersede that
guess without rewriting the original failed report.

idle-reload.json is ready-without-reproduced-mismatch, not an automatic-rebind
pass. Startup briefly had root native support false before the own-UID sample;
later samples were ready and automatic requests stayed zero. The 82 Java
contracts use real gate/activity/reader code but stub Android/Binder. The 63
consumer contracts exercise the production metadata validator. Neither proves
the three-negative automatic branch actually ran on this phone.

native-business.json records one explicit-account 191 call and one native INFO
SMS, media metadata, network send success, four received/acknowledged fragments,
one merged native inbox row and notification. Human audible speech is unconfirmed.
Short-code policy restoration reloaded Phone only after traffic observations.
The later read-only samples and actual module reboot verify native readiness,
exact installed/mounted APK bytes and the original full replacement owner.

Modern service compilation and exact module embedding/signing passed. This batch
does not prove new modern carrier behavior, modern app-own-UID actions, real dual
active SIMs, sustained automatic-rebind behavior or completion of the full goal.
SHA256SUMS covers the JSON reports; source-checksums pins the actual built inputs.
