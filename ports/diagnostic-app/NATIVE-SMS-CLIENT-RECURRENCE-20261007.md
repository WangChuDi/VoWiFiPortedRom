# Registered IMS with unavailable native SMS: current recovery and recurrence

The installed tool0.9.12 / IMS0.4.6 / API30 module0.9.5 again showed an actual
framework SMS mismatch. Three read-only observations had IWLAN CHILD_OPENED,
QNS IWLAN_SELECTED, registered WLAN IMS and voice/SMS callbacks true, while
native IMS SMS support and the current SMS dispatcher window were false.

The existing idle/owner/framework-pinned client rebind restored both native
observations without reboot or Phone reload. IMS generation remained the same,
capability mask stayed 9, and SMS ready events increased from 1 to 2. Three
post-rebind observations were ready. Callback count reflection was unavailable;
it is not evidence that zero callbacks were registered.

One already-authorized native `85075 / INFO` test then passed: send RESULT_OK
(-1), network error0, TX/OK1, RX/ACK4, zero SMS/ACK failures, one native inbox row
and a new Messages notification. No new call was placed. The same IMS generation,
Phone identity and owner were preserved through the traffic test. No account
login, SMS body or credentials were exported.

After restoring the original absent short-code policy through the existing idle
Phone reload, three fresh read-only samples again had registered IMS but native
SMS and dispatcher false. A second existing protected rebind restored readiness.
Both failed readiness observations remain recorded; a successful SMS before
cleanup does not prove automatic recovery after every Phone/framework lifecycle.

Reports are under [current recurrence evidence](reports/20261007-native-sms-client-recurrence).
This turn changed no installed APK, module, APN, vendor, modem, CNE or kernel.
It proves a narrow working repair and a reproducible lifecycle boundary. It does
not prove the precise callback race or a durable automatic repair. A follow-up
must verify native framework state after re-registration and repair only a
confirmed mismatch with current owner/registration/idle guards, then validate
real native delivery and notification again. Modern carrier and dual-SIM proofs
remain separate requirements.
