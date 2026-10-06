# Diagnostic tool0.9.0: modern routing and shared-role backend

This version embeds both independently built engine bundles and exposes
experimental SDK31–37 IWLAN/QNS/IMS trial, retention, lease check/renewal and
selected-owner recovery. The modern worker uses the existing production shell
coordinator, including publication, permission preparation and supervisor
readiness before selection. Its lease tokens remain private root-worker data.
Every modern screen marks this experimental; engine-profile availability is not
a claim of successful installation, carrier registration or complete version support.

The existing API30/raphael/ready23415 controller route stays separate. The updated
signed tool was installed on the current phone. Read-only backend verification
observed mask7 ACTIVE/persistent ENABLED, replacement IWLAN CHILD_OPENED, QNS
IWLAN_SELECTED, IMS REGISTERED, one attributable IMS network/two P-CSCF addresses,
WLAN registration and advertised voice/SMS capability. See
[`reports/20261006-api30-regression.json`](reports/20261006-api30-regression.json).
No new call/SMS was sent; this is not fresh audio/delivery/notification proof or
a new activity-UI interaction test.

## Implemented boundaries

* `RootDiagnostics` dispatches modern inspection to `ModernControllerObservation`.
  It reads only fixed module/private owner records, actual package bytes and
  CarrierConfig. It constructs no controller locks or state directories.
* Build-generated engine identities pin the embedded module ZIPs, modern control
  script/helper and all three independently signed APKs. Modern component actions
  require the installed privileged APKs and module bytes to match those identities.
  A module-provided manifest by itself is insufficient.
* Modern inventory returns only the selected phase/mask/retention and owner tuples;
  it never exports private recovery properties or worker tokens. Journal ACTIVE
  is not labelled as verified live/disk/lease or carrier readiness.
* `ModernAppActions` validates root/SDK/action/slot/sub/mask and rechecks the active
  ready23415 tuple before the production CLI rechecks it. Retain/renew read the
  current private token; concurrent token changes are refused by the transaction.
  Child JSON/text is drained and consumed privately, including select's token,
  and only fixed action-result metadata reaches the activity.
* Modern reload is labelled **check and renew**. It does not kill the phone
  process or claim that a fresh IKE/SIP registration has been triggered.
* Modern module update refuses an incomplete status read, pending installation
  phase or pending-owner inventory observed by the activity. Missing module code
  does not hide existing recoverable owner/installation records.
  It is not an atomic installer/controller interlock against an external concurrent
  root writer. Carrier actions/recovery retain their independent runtime guards.
* Source/asset compilation, signature checks and root backend tests are separate
  from activity UI, true Magisk lifecycle and positive modern carrier tests.

Timeouts do not establish rollback; inspect the private transaction through the
tool afterward. Root worker argv temporarily contains the existing CLI lease
token, as before; it is not returned to the UI or public reports. This token is
not SIM authentication material, and process-list visibility to other privileged
actors is not claimed to be eliminated.

## Actual evidence

The final signed APK is
`8dedf3352309eedf3576c858e14c1300d4137a738f0a912f1aa7242d87c12966`.
Its manifest is versionCode12 / `0.9.0-diagnostic`, min/targetSDK30.
Signature verification passed with the retained external application identity;
keys/passwords remain outside the repository and are never embedded.
Both module assets were verified byte-for-byte against their independently built
bundles. The modern module is
`4d675ae57d4cba462e30f2c4d5c655f23848ad7621662d13bbd40c9dfa7ce279`;
the API30 engine is
`c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5`.

The actual compiled root diagnostics and refusal worker ran concurrently on named
API33/API36 root emulators:

* `reports/20261006-modern-app-first.json`: earlier unsigned draft root check.
* `reports/20261006-modern-app-final.json`: first signed version0.9.0 draft, both passed.
* `reports/20261006-modern-app-third.json`: final signed version0.9.0 with missing-
  module recovery inventory and update refusal gates, both passed. The new inventory
  branches were statically reviewed; the run still uses absent production state,
  not a positive recovery/update transaction fixture.

Those checks exercise SDK dispatch, SIM/policy/network/ABI observations, nonroot
diagnostic refusal, nonroot/invalid action/mask/slot/sub refusals and missing-module
action refusal. Production installation/module state is absent before/after.
The fake-SIM run did not obtain IMS registration/capability callbacks, and the
report records those fields absent rather than claiming failure/success.

Actual modern module mounting/installation, positive app-driven VOXI selection,
ePDG/USIM authentication, modern voice/SMS/native notifications and simultaneous
dual active SIMs remain **unverified**. SDK31/32/34/35/37 root runtime/device
coverage is also pending; the Android17 framework sample is not ROM evidence.
The separate [46-stage helper batch](../android12/runtime/reports/20261006-shared-roles/README.md)
includes shared pending-peer policy and real resident renewal/SIGKILL/disable;
it does not fill those positive carrier/device gaps.

## Reproduce

```sh
python ports/diagnostic-app/validate.py --build --unsigned
# Or supply the documented external keystore environment and build/sign normally.
python ports/diagnostic-app/validate.py --artifacts-only
python ports/diagnostic-app/check-runtime.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --apk ports/diagnostic-app/out/vowifi-tool.apk \
  --output /path/to/fresh-modern-app-report.json
```

Unsigned validation reports signature proof false. Artifact-only validation does
not claim a build/contracts run in that invocation; the consolidated unsigned
source build ran the existing 77 ABI lookup assertions. Controller host checks
also passed 12 baseline, 13 idle and 21 IWLAN observation contracts.

Both embedded engines retain their upstream/GPL notices, including the original
[phhusson/ims source attribution](../android11/THIRD_PARTY.md). No APN/vendor/modem
replacement or external-account automation was introduced by this app integration.
