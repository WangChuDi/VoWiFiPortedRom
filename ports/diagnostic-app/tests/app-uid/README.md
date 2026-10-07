# Exact 0.9.11 application-UID UI trial

This GPL-2.0 instrumentation enters the installed tool's process with Android's
same-signature instrumentation mechanism. It verifies that its actual UID equals
the target application's UID, pins the target APK bytes, selects the calibrated
API30 / raphael slot1 profile, and invokes the existing read-only diagnosis and
real enabled client-rebind button. It adds no automation endpoint to the tool,
does not rebuild the production APK/engines and does not call, send SMS, query
SMS bodies, install a module or request a Phone reload.

Build with the existing toolchain and the **same** external signing key used for
the installed tool. JAVA_HOME, STACK_KEYSTORE and STACK_KEYSTORE_PASSWORD are
required; no key is generated or stored in this directory.

```sh
python build.py --framework /path/to/android-all-11.jar \
  --toolchain /path/to/toolchain --output /path/to/fresh-test-build
```

After checking the exact target tool, idle calls and test APK identity, install
the test APK and invoke its instrumentation component:

```sh
adb -s YOUR_PHONE install /path/to/fresh-test-build/own-uid-trial.apk
adb -s YOUR_PHONE shell am instrument -w --no-window-animation \
  dev.codex.vowifi.tooltest/dev.codex.vowifi.tooltest.OwnUidTrial
adb -s YOUR_PHONE uninstall dev.codex.vowifi.tooltest
```

MIUI may refuse USB installation. The recorded run used its authorized root
package manager after independently checking the staged test APK's digest. The
test itself still ran in the tool's own application UID, not the Shell UID.
Instrumentation completes with code -1 only after its fixed assertions pass.
Do not treat a host observation timeout as guest termination or uninstall a
possibly still-running test merely because the observation deadline expired.

The test exports fixed enums and booleans only. It never exports the complete
diagnostic object, subscription IDs, phone numbers, owner tokens, raw output or
SMS content. A completed UI action is checked against the existing worker's
acceptance/completion protocol and refreshed, separate native-SMS and dispatcher
observations. Instrumentation is a test harness, not another IMS implementation.
Actual voice, SMS delivery/notification, other versions, other profiles and two
active SIMs require separate evidence.

See [the actual live broken-to-ready result and retained failures](../../reports/20261007-app-own-uid/README.md).
