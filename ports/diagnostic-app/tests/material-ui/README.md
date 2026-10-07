# Physical own-UID Material interface verification

This is a separately installed, same-signature instrumentation APK targeting the
production diagnostic application. It verifies the target APK digest supplied
by the host, actual application UID, empty/active SIM selection, full owner state,
busy controls, diagnostic scope text, and navigation/content visibility. It never
clicks mutation actions or sends a call/SMS. It saves PNG canvases of the target
application's own decor view to its private files directory, not a screen or
notification-shade capture.

Use the application build's `JAVA`, `IMS_PORT_TOOLCHAIN`, external `STACK_KEYSTORE`
and `STACK_KEYSTORE_PASSWORD` environment. The test key must match the target.

```
python build.py --target-apk /absolute/path/to/vowifi-tool.apk
adb install -r out/material-ui-test.apk
adb shell am instrument -w --no-window-animation -e target_sha256 TARGET_SHA256 dev.codex.vowifi.materialtest/dev.codex.vowifi.materialtest.MaterialUiTrial
adb uninstall dev.codex.vowifi.materialtest
```

Replace TARGET_SHA256 with the hash from `out/build.json`, independently verify
the installed target APK, and inspect the instrumentation result/code rather
than only the shell exit status. The current selected-profile fixture assumes
an empty slot0 and active VOXI slot1 with full persistent selection. It is not a
general two-active-SIM test. Native SMS support false is recorded and is **not**
reclassified as a successful SMS test.

Unlock the phone and keep it awake for the entire check. Logic-only view-tree
assertions are insufficient when the screen is locked; this test also requires
measured page content to be globally visible. Inspect the saved canvases before
claiming visual acceptance. Temporary night/font/USB stay-awake settings must be
restored. Test light and dark modes against unchanged target bytes; these do not
require rebuilding or installing the replacement engines.

The retained production0.9.18 physical results are documented in
[the validation record](../../MATERIAL3-SHIZUKU-20261008.md).
