# Own-UID network/APN UI check

`build.py --target-apk <exact-tool-apk>` requires the same external signing key
as the production tool. It builds a separate instrumentation APK without
rebuilding the target. Pass the target SHA256 to `am instrument` as
`-e target_sha256 <sha>`.

The test requires an unlocked/interactive phone with the known emptySIM1 and
activeSIM2 profile. It validates the actual target UID/APK, empty-slot isolation,
busy guards, actual network button/root IPC, detailed APN fields, password
reveal/remask, owner preservation and explanatory text. It sends bounded
SA_INIT requests through the network button but no authentication/call/SMS.
It does not click any install/replacement/recovery action.
For tool0.9.20 it also browses all nine public carrier/product references,
checks the six interface groups and real-session evidence, and captures those
own-app views. Reference selection changes no APN, carrier target or engine.

Only fixed status/boolean fields are returned. Own-window PNGs are stored in
the target's private files directory, with APN password remasked first. These
images may contain the user-requested non-password APN fields; they never capture
another app or the notification shade. Remove the test APK after completion.
