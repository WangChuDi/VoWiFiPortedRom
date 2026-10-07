# IMS application UID SMS observation, tool 0.9.14

This release adds an independent, read-only native SMS support query. Root requests
the observation from the installed IMS provider; a bounded worker in the IMS
application calls `ISms.isImsSmsSupportedForSubscriber` as that application's UID.
It can observe the selected active SIM without requiring a replacement lease,
registered MMTEL feature, or an outgoing SMS. This makes the diagnostic useful on
modern Android even when the carrier registration chain has not started.

The separate `ims_native_sms_status` result preserves the existing root query,
MMTEL capability snapshot, and calibrated phone-dispatcher window. A TRUE result
can include the legacy radio implementation. It is not proof that the replacement
software SMS dispatcher is ready, that an IPsec tunnel works, or that an SMS was
delivered. The UI states that distinction and treats stale or unavailable results
as unknown.

## Scope and limits

Read-only queries accept API31–37. API30 retains the exact raphael/framework SHA
calibration. Reflection, hidden-API access, app identity, permissions and available
Binder interfaces still determine whether a particular ROM can return a result.
The existing API30 automatic recovery gate remains separately calibrated; this
new endpoint does not enable automatic client rebinding on API31–37.

The provider still requires privileged-phone permission and Binder UID0. The
single worker verifies one ready, matching slot/subscription both before and after
the Binder query. Requests time out after 2.5 seconds. An outstanding Binder call
continues to occupy that worker; other requests return BUSY, and its late result
is discarded. Timeout and permission/interface failures return UNKNOWN.

The consumer requires the requested slot/subscription, nonce, PID, boot and
monotonic query window. It verifies subscription and IMS process identity before
and after the request. Only a current OBSERVED result with `self_uid=true` is a
known TRUE/FALSE. The returned public metadata excludes private nonce/process
identifiers. The selected slot/subscription is retained for ownership attribution.
There is no message text, PDU, authentication or SIM identity in this protocol.

## Build and verification

The isolated consolidated build produced tool0.9.14/versionCode26, API30
IMS0.4.8/versionCode42/module0.9.7, and modern IMS0.2.4/versionCode40/module0.2.7.
Both service variants compile, signatures verify, and the APK embeds the exact
two engine ZIPs. Previous releases and original build outputs were preserved.

The production worker/reader/gate tests executed 164 assertions using controlled
Android and Binder stubs, including API31–37, two independently selected
subscriptions, changed/duplicate ownership, permission failures, real worker
timeouts, BUSY and late-result rejection. The production consumer validators
passed 63 existing IMS-client assertions and 46 new query assertions. These host
tests do not prove real device permissions, carrier service or simultaneous
dual-SIM operation.

On the physical API30 phone the initial hot update returned an own-UID FALSE
while IMS was DOWN/NETWORK_IO. That installation verification remains failed.
One existing, idle-only Phone reload restored registration and three consecutive
own-UID TRUE/native-dispatcher-ready samples. The matching module was then staged.
An actual reboot loaded byte-exact IWLAN/QNS/IMS mounts and returned three
consecutive current own-UID TRUE/native-ready samples without another reload or
manual client rebind. Owner and global hidden-API policies were preserved.
No new call or SMS was sent in this query regression. Earlier real191/native
INFO/inbox/notification results remain separate in
[the watch evidence](NATIVE-SMS-WATCH-20261007.md).

A same-signature, exact-artifact instrumentation test also ran inside the real
diagnostic application's UID. Its first run passed query attribution but failed
the combined readiness assertion; that result remains failed. The second run
added independent observation fields while retaining the same strict assertion.
It passed actual empty-slot isolation, active IMS own-UID query, WLAN voice/SMS,
native dispatcher readiness, persistent full selection and the rendered UI scope
disclaimer. Phone/IMS process identity and production bytes were unchanged, and
the temporary test APK was removed. A separate three-sample root observation
between the two UI runs also passed. These observations do not establish the
cause of the first readiness failure or long-term reliability.

Modern device query attempts and their restore/permission checks are retained in
[this batch's closed reports](reports/20261007-native-sms-query/README.md).
On the original API37 emulator, three actual own-UID queries returned OBSERVED
FALSE for the fake SIM, and the provider refused a nonroot request. Exact original
IMS APK/UID/permissions/navigation and retained records/helpers were observed
restored. The driver's final state read and later independent rechecks failed;
the4GiB host monitor reached its private-memory limit and terminated the owned
emulator. This is a verified query substep with incomplete final-state checks,
not a passing full modern deployment or real-carrier test.
They must be interpreted separately from fake-SIM carrier registration, diagnostic
activity own-UID actions, actual SMS delivery and real dual active SIMs. Those
requirements, automatic recovery on modern ROMs and the full goal remain pending.

IMS remains derived from the preserved `phhusson/ims` snapshot. Existing GPL-2.0
source attribution and [third-party notices](../android11/THIRD_PARTY.md) remain
part of this port; the observation code does not replace or relicense upstream.
