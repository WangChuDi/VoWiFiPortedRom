# Tool0.9.1: completed-snapshot recovery bundle

The application is versionCode13 / `0.9.1-diagnostic`, min/targetSDK30. Its action
semantics remain those of [tool0.9.0](MODERN-INTEGRATION-20261006.md); this update
packages the new modern helper and regenerates the exact compiled asset identities.
It does not install that SDK31–37 engine on the working SDK30 phone.

Final signed APK: `8080b7d4e8ce15cda7b57493a9fa15582f69b102036e188a3debe6eab94ec5b0`.
Final modern module: `ace364cc8727c5f386cea57a40de67c57b20be21c8e2ae22ad18eec8aeec3eaf`.
API30 module (unchanged): `c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5`.

The initial consolidated unsigned source build passed the existing 77 ABI lookup
assertions, metadata and byte-exact asset checks. The retained external application
key then signed the APK; artifact-only validation verified signature, version and
both embedded engines. That latter invocation separately records build/contracts
false, because those ran during the prior build invocation. Keys and passwords
remain external and are not copied into source, APKs or reports.
After the fixture-only readiness correction, the app was rebuilt with the new
module bytes, re-signed, and its version/signature/asset equality checked again.
The unchanged 77 ABI lookup contracts were not repeated. The final signed checks
are in [the artifact report](reports/20261006-app091-final-signed-artifacts.json).

The compiled app root backend ran concurrently on named API33/API36 guests;
[`20261006-modern-app091-final.json`](reports/20261006-modern-app091-final.json) confirms
read-only diagnosis, SDK dispatch, nonroot/invalid-action refusals and missing-module
refusal without creating production module/owner state. Fake-SIM IMS callbacks
were absent; positive application-driven modern provider selection is not proved.
The bundled modern helper separately passed the
[49-stage preparation-interruption batch and final 18-stage follow-up](../android12/runtime/reports/20261006-preparing-interruption/README.md).

ADB update of the signed app on the current phone returned Success. A separate
root check matched the installed APK bytes to the signed local APK and invoked
its compiled diagnostics. The sanitized
[`API30 regression`](reports/20261006-api30-regression091-final.json) observes mask7
ACTIVE/persistent ENABLED/AP-assisted, IWLAN CHILD_OPENED with IKE/child established,
QNS IWLAN_SELECTED, replacement IMS REGISTERED, one attributable IMS network/two
P-CSCF addresses and WLAN voice/SMS capability. This is a current registration and
capability check, not new call/SMS/audio/delivery/notification or app-owned UI proof.

The earlier reports without `-final` retain the first signed0.9.1 draft's APK
`ece245ca942cd0bfbdbd074cfad3a546c600ff5c122eaf1522fb65e4894fc45a`
and module `eaf914040506725a5ef3d375cf45ffc5924219a0ab4c26d70cdd0f3a61b89c6f`.
They are historical evidence, not the final installed artifact identity.

Modern Magisk lifecycle, positive modern-device carrier selection and traffic,
true simultaneous active dual SIMs, removed-SIM recovery, and SDK31/32/34/35/37
runtime validation remain pending. The goal remains Android11–17 with diagnostic
and selectable IWLAN/QNS/IMS replacement; the two tested guests do not replace it.
The [phhusson/ims source and license notices](../android11/THIRD_PARTY.md) are retained.
