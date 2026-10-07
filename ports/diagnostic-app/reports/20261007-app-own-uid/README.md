# Actual 0.9.11 own-UID UI recovery

The unchanged installed tool0.9.11 and API30 full replacement were exercised
through a separate, same-signature instrumentation APK. Android ran the test
inside the target application's UID. The test pinned the actual installed tool
bytes, launched its existing MainActivity and clicked its enabled client-rebind
button. This closes the own-UID diagnose/rebind gap from the ADB-only worker test;
it does not claim to retest every application action or Android12–17 UI.

The [actual result](own-uid-ui.json) was a live broken-to-ready observation:

| Observation | Before application button | After refreshed check |
| --- | --- | --- |
| WLAN registration / voice / advertised SMS | true | true |
| Native SMS support | false | true |
| Calibrated system SMS dispatcher available | false | true |
| Full ACTIVE selection / persistent ENABLED | true | true |
| Phone/system-server continuity within diagnosis | stable | stable |

The worker reported the action accepted and completed with no Phone reload. The
host independently compared Phone identity/boot count and the IMS PID
across the entire trial, and verified the production tool's bytes unchanged.
Both diagnostic results were complete. The instrumentation returned code -1;
the temporary test package and staged APK were then removed. This run sent no
new carrier call or SMS. The original host result called its PID-only comparison
`ims_process_identity_stable`; the public report explicitly names it
`ims_pid_unchanged`, because a PID comparison alone does not check its start time.
State recovery is distinct from new traffic proof,
notification delivery, automatic long-term recovery and dual active SIM support.

The [first preflight failure](preflight-failed.json) incorrectly treated the
absent test package's nonzero `pm path` exit as an observation failure. The host
check was corrected without touching production code. The [USB install attempt](usb-install-failed.json)
then did not report installation success; its exact installer reason was not
recorded. Both failures are retained. The final run used the already-authorized
root package manager for test installation, with an exact staged-APK check, and
the same built test APK. No new production release was generated.

[Build identity](build.json) records the executed test source and APK digests;
[source and reproducible builder](../../tests/app-uid/README.md) retain GPL-2.0.
The [public builder verification](public-build.json) reproduced the executed
test's classes.dex exactly; signed container identity is recorded separately.
SHA256SUMS covers every fixed report. Raw logs, private records, SIM identities,
signing keys and SMS bodies are excluded.
Reports and checksums use canonical LF bytes, matching Git blobs and source archives.
