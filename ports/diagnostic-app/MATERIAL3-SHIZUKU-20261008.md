# Material 3 interface and non-root feasibility, tool 0.9.18

The diagnostic application now has persistent Overview, Diagnostics and Actions
navigation. Overview separates WLAN registration, advertised voice/SMS capability,
and the independently sampled system SMS dispatcher. Diagnostics groups device,
network/IWLAN, carrier/APN, IMS/SMS and transaction observations in expandable
sections. Actions contains component selection, timed trials, retention, connection
recovery, recorded-owner restoration and module updates.

`MaterialTheme.java` implements Material 3 color roles, tonal surfaces, typography,
24dp containers, pill buttons, ripple feedback and 48–52dp minimum touch targets
with Android platform widgets. It is a Material 3 styled implementation, **not**
the AndroidX Material Components library. Colors follow the device night mode;
text uses scalable sp units. The existing selected-SIM and transaction guards
are retained. Checks disable SIM selection and mutation controls and show progress.
An error clears the previous overview and diagnostic results.

## Build and installation boundaries

Tool versionCode30 / `0.9.18-diagnostic`:

```
APK SHA-256:
d452897e3d58cd93fb33d7b33f8574bd57bf1a52a015e9fa446ec92ffedc6f7f

Embedded API30 engine SHA-256:
6f659b54968f0b064086394fb9162a836d32b06c0ecf569d26ca58a3f5dd1dee

Embedded API31–37 engine SHA-256:
c934711841da6f7213e3fca95e4ea321f5c8c0d77658abfcb13d850e8e84b147
```

Both engines are reused exactly from the independent 0.9.17 ingress-diagnostic
build. The UI build did not rebuild or deploy either engine. Only the tool APK
was installed. The phone retains API30 module0.9.9 and IMS0.4.10, with all three
installed replacement APK hashes unchanged. The previous0.9.16 tool APK is retained
on-device. Installing the tool does not automatically install its embedded engines.
The newer ingress counters therefore remain unavailable on that older live IMS.
No reboot, phone reload, call or SMS was requested for this UI work.

The consolidated tool build passed signature, manifest, DEX and exact embedded
asset checks, plus production telemetry/reconnect/SMS/receive contracts. The build
validator's `device_validated:false` is retained: separate live evidence describes
what actually ran, rather than rewriting compilation evidence.

## Physical-device UI checks

Same-signature instrumentation ran under the actual tool UID and verified the
target APK hash, empty SIM1 isolation, active SIM2 observation, persistent full
owner transaction, busy guards, original diagnostic scope text and three-page
visibility. It did not click replacement, recovery, rebind or test-message actions.
Phone identity and IMS PID remained unchanged. The test APK was removed afterwards.

The first logic-only run did not require visible measured content and produced
incomplete previews while locked. It is not visual acceptance evidence. A stronger
visible-content assertion retained a failed locked-screen run. After the user
unlocked the phone, the unchanged production APK passed all three pages in light
mode. A dark/1.3-font run was again interrupted by screen locking; its failure
remains retained. A final run with temporary USB stay-awake passed. Night mode,
font scale and USB stay-awake values were restored exactly. Own-view canvases of
the three pages were visually inspected; these captures contain no SMS app or
notification-shade content. This does not validate every OEM or screen size.

The current WLAN voice registration and advertised SMS capability remain present,
but the independently queried native SMS support and dispatcher are false. The
existing automatic recovery reports LIMIT. The original installation checker
retains `status:failed` because its stronger native-SMS-ready condition was unmet,
despite successful tool installation and unchanged service processes/APKs. UI
acceptance does not repair this existing SMS failure or establish its cause.

## Shizuku: actual scope of the experiment

`ShellCapabilityProbe` is a separate, read-only UID2000 `app_process` entry point.
It emits only capability booleans, state values and exception class names. It
does not output phone numbers, subscription IDs, APN values, messages or module
contents, and does not write settings, authenticate a SIM, call or send an SMS.
The brief IMS callback is unregistered on completion.

**The Shizuku SDK is not integrated.** This tests the ADB/Shell identity that a
non-root Shizuku UserService would use, not its transport or authorization flow.
The tested phone already has root and privileged replacement services installed;
it is not a clean unrooted-device installation test.

| Operation on this Android11 device | Observed UID2000 result | Interpretation |
| --- | --- | --- |
| Network list API | Returned a non-null list | Does not establish network-capability visibility |
| Wi-Fi network capabilities | SecurityException | This package-context path is restricted; another attribution/API path may differ |
| Active SIM subscription query | Succeeded, selected slot found | Read-only diagnostic candidate |
| CarrierConfig read | Succeeded, non-null bundle | Read-only diagnostic candidate; no override was attempted |
| WFC setting / roaming setting | Calls succeeded; true / false | Configuration values, not registration or modification proof |
| WLAN voice provisioning | Call succeeded; true | Configuration observation only |
| IMS registration callback | Received one callback within4s | Callback can be subscribed; this boolean does not say which registration state arrived |
| System IMS-SMS support query | Call succeeded; false | A support value, not a permission failure or an actual SMS test |
| Preferred APN query | SecurityException | This query path was refused |
| Existing Magisk module file | Failed to open | Module is root-visible, but this shell path could not read it |
| System/module directory write-access hints | Both false | No write or mount operation was attempted |

Permission checks reported READ_PRIVILEGED_PHONE_STATE and MODIFY_PHONE_STATE
granted for UID2000. WRITE_APN_SETTINGS, RECEIVE_SMS, BIND_IMS_SERVICE and
MANAGE_IPSEC_TUNNELS were not granted. A permission check is not a Binder operation
test; in particular BIND_IMS_SERVICE is a service binding contract, and this one
boolean does not independently establish whether an IMS service can be implemented.

The current Magisk installation and restoration workflows write root-owned
`/data/adb` state and deploy privileged system components. Shizuku in ADB mode
does not supply those capabilities. If a ROM already contains a working IMS/IWLAN
stack and only a carrier setting needs changing, its MODIFY_PHONE_STATE access
may be useful, but this experiment did not test configuration changes. A future
hybrid diagnostic backend should use the ordinary app context for network APIs
and verify telephony calls through an actual Shizuku UserService, preserving
unavailable states and disabling unsupported mutation controls.

[Shizuku's official API guide](https://github.com/RikkaApps/Shizuku-API) documents
the distinction between root UID0 and ADB UID2000, version-dependent shell
permissions and inaccessible private app files. It also warns that UserService
is not a normal Android application process and some Context APIs do not work.
Consequently, the present app-process results cannot promise identical Context,
AppOps or SELinux behavior inside Shizuku. Root-started Shizuku remains root;
it would not be a root-free solution.

Android17 runtime work is deferred at the user's request. Modern carrier voice,
SMS, positive app-UID controls and simultaneous real dual SIMs remain unverified.
The original phhusson/ims attribution and GPL-2.0 notices are unchanged.

Closed metadata, build identities and retained failed runs are in
[the evidence directory](reports/20261008-material3-shizuku/README.md).
