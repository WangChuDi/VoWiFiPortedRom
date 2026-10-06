# Android12L/API32 continuation: 2026-10-06

The existing tool0.9.2 and final bound-service helper/module were run on the
owned `CodexVoWiFiApi32` Google APIs x86_64 emulator. No service, helper, module
or tool was rebuilt for this continuation. Earlier API31/API34/API35 evidence
remains in its [separate three-worker batch](../20261006-bound-iwlan/README.md).

| Artifact | SHA-256 |
|---|---|
| Signed tool0.9.2 | `2fd68b0d012e9523f935a7c9e91cedea78138b695fcc260c588a57cfcf43a630` |
| Signed modern module | `de5826326df086f2694aae88a3973f817b1cc0f30e1425c6ace42721cf7f70de` |
| Root helper | `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d` |
| Signed IWLAN | `5ee8d67d5b9a53d98646c7d64cfdae0261a072dbca05d3c570c3c2d602340528` |
| Retained QNS | `798ac76899c8dea99b587f1e0b47fb64708fb238b3323d6f21f5a9bc74d11800` |
| Retained IMS | `4d645baf4c0f7a744e3cefac4190ddc83f977790451cd67c155b15ca0ad1a009` |

[Artifact identities](artifacts.json) and the [preparation record](preparation.json)
match the byte-exact installed privileged APKs and module helper. Preparation
used writable-system QEMU, not Magisk mounting. The official package is
`system-images;android-32;google_apis;x86_64`, metadata revision8; the archived
[image metadata](official-image.json) records its official archive SHA-1 and
local metadata hashes. The packaged `source.properties` retains revision4 while
`package.xml` records revision8; these original files were not rewritten.

## Completed observations

* [Runtime validation](validation.json) and [detailed lookup/binding checks](runtime-check.json)
  passed installed privileged permissions, 20 service-library lookups, explicit
  IWLAN data/network and IMS bindings, shell/privileged-nonroot refusal and
  CarrierConfig Binder reading. The root helper's 23 lookups and the service's
  20 lookups are distinct loader scopes.
* [The QNS framework trial](framework-trial.json) observed the phone-process
  binding and restored the entire original bundle without creating a persisted
  override. This is a controlled non-VOXI fake-SIM experiment.
* [Compiled tool checks](app092.json) passed SDK dispatch, root read-only
  diagnostics, nonroot/malformed action/identity and missing-module refusals,
  with production state presence unchanged. IMS transport/capability callbacks
  were unavailable in this fake-SIM run. This does not exercise positive UI
  selection under the tool application's own UID.
* [The full lifecycle run](final.json) passed 49 stage keys: 40 base plus nine
  PREPARING recovery stages. It includes full-mask refusal with denied IPsec,
  QNS-only selection preserving that denial, two actual identity-guarded
  PREPARING SIGKILL cases, separate-process recovery, terminal-before-cleanup
  checks, complete outer permission/configuration/lease cleanup and unchanged
  phone PID. Actual OS reboot is separately false.
* [The post-cleanup audit](postcleanup-readonly.json) is read-only. It observes
  installed permissions and the IMS SMS restriction exemption, IPsec `allow`
  and the narrow full permission profile ready. Only QNS has a current observed
  phone binding; prior explicit IWLAN/IMS bindings are not persistent selection.

This continuation used one device worker. The earlier three-worker batch and
this later one-worker run together provide 196 stage observations across four
SDKs, not four concurrently running agents or guests.

## Corrected Android17 package discovery

The old SDK-list filter matched `android-37;` and omitted decimal package names.
The cached official listing/XML actually includes
`system-images;android-37.0;google_apis;x86_64`, revision6, archive
`x86_64-37.0_r06.zip`. [The corrected metadata](official-sdk-metadata.json)
preserves that discovery. It supersedes the earlier matrix's missing-image
statement; metadata alone proves neither installation nor runtime/carrier support.
Android17 runtime remains pending at the time of this API32 report.

## Remaining scope

No physical phone was changed and no call or SMS was sent for this continuation.
Modern Magisk mount/boot/disable/remove, real USIM/EAP-AKA/ePDG/IMS traffic,
audio/SMS/notifications, positive application-UID controls, removed-SIM recovery
and simultaneous dual-active-SIM behavior remain unverified by these fixtures.
IMS retains [phhusson/ims source and GPL-2.0 attribution](../../../../android11/THIRD_PARTY.md).
The full Android11–17 and dual-SIM goal remains incomplete.
