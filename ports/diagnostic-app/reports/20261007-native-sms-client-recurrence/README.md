# Current native SMS recovery and recurrence

See [behavior and limits](../../NATIVE-SMS-CLIENT-RECURRENCE-20261007.md).

- `before-failed-readiness.json`: three fresh observations of registered WLAN
  IMS with native SMS support and dispatcher availability false.
- `first-recovery.json`: existing protected client rebind accepted/completed,
  followed by three ready observations with stable Phone identity and IMS generation.
- `native-info.json`: one native INFO SMS; delivery, RP acknowledgements,
  merged inbox row and new notification passed. No new call was placed.
- `after-cleanup-failed-readiness.json`: the mismatch recurred after the
  existing idle Phone reload used to restore the absent short-code policy.
- `final-recovery.json`: a second existing protected client rebind restored
  three ready observations; the full replacement owner remains active.
- `summary.json`: manual repair and native traffic passed, automatic recovery
  remains unverified. No installed APK/module or APN/vendor/modem/kernel changes.

Only fixed metadata is archived. Callback count reflection was unavailable,
which does not imply zero framework callbacks. SMS text, private process identities,
SIM identifiers, private journals, raw logs and account credentials are excluded.
