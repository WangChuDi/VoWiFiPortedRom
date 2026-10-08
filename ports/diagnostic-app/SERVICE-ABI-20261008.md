# Actual service-process interface observation — tool0.9.21

The previous5/23 result described the independent root app_process loader.
It could not establish which interfaces the current IWLAN/QNS/IMS service
loaders could resolve. Tool0.9.21 queries each selected active service instead,
and keeps that old result in a collapsed auxiliary diagnostic group.

| Active service | Limited dependency catalogue | Connected API30 observation |
| --- | --- | --- |
| IWLAN | DataService/NetworkService2, EAP3, IKE parameters9, lifecycle3, negotiated configuration3, IPsec2 |22/22 visible |
| QNS | QualifiedNetworksService/provider creation, provider class/update/close5 |5/5 visible |
| IMS | service/MMTEL/capabilities4, registration2, call session3, SMS6 |15/15 visible |

These are purpose-specific dependencies, not competing adaptation schemes or
an exhaustive framework ABI. `ServiceAbiCatalog.java` contains the exact
signatures and explicit constructor/proposal aliases; the UI lists them by
category. Only class, constructor and method lookup is performed. No object is
constructed and no method invoked. A visible protected method is not evidence
that an arbitrary caller can invoke it.

## Current-process evidence

Each root-only `runtime-abi` reply is bound to role, selected SIM slot/subId,
nonce, PID, boot and current active owner/generation. The consumer validates
the closed schema, exact role key set, derived counts, source flags and sample
age. Current CarrierConfig and SIM selection are checked before/after, as is
PID plus process start time. Correlation with status rejects another generation.
Missing, unselected, inactive or old producers remain unknown, never a count
copied from the root loader. The query requires an existing process; a provider
start following a death race is rejected when its identity changes.

IWLAN declares `android.net.ipsec.ike` as a required shared library. Android
adds a declared library to that package's class loader; it does not add it to
every unrelated app_process loader. See the official
[uses-library contract](https://developer.android.com/guide/topics/manifest/uses-library-element).

## Diagnostic-only artifact provenance

The connected device was running the verified0916 API30 engine, IMS0.4.10,
from baseline source commit `bda69909c8aefc20f9907d71dbfe8654134bc0b1`.
The repository also contains newer SIP ingress source that had not been
installed. This batch deliberately builds a **baseline class delta**, using
`build-service-abi-delta.py`, rather than recompiling that undeployed SIP code.

Original baseline class jars are replayed through D8 and must match both the
original unsigned DEX and the pinned live signed APK DEX. Only these compiled
class entries may change or be added:

* `StackTelemetryProvider.class`
* `ServiceAbiCatalog.class`
* `ServiceAbiCatalog$Entry.class`

All other compiled classes remain byte-identical: IWLAN30, QNS20, IMS153.
No SIP/Kotlin, routing or controller code was recompiled. APK metadata versions
and signatures are checked; each service retains its existing signer.
The module is0.9.9-service-abi/code22; IMS is0.4.10-service-abi/code46.
`SERVICE-ABI-PROFILE.json` inside the module records this composite provenance.
Reproduction requires the pinned baseline artifacts plus the current diagnostic
sources; a normal full-current-source build is a different artifact profile.

The modern engine ZIP is retained byte-for-byte. Its older providers do not
implement this method, so the new UI reports unknown there. No Android17 or
other modern real-device validation was performed for this feature.

## Validation and deployment

One consolidated frozen build passed219 new ABI contracts plus the existing
carrier/network/production contracts, signing and embedded-engine checks.
No old release artifact was overwritten. Device APKs and the original module
were backed up in a root-private directory before updating the tool and three
services; the new module was staged for the next normal boot.

The initial installation observation failed while the framework was rebinding;
this failed run is retained. One idle phone-service reload was requested.
Subsequent observation found the selected services running with22/22,5/5 and
15/15 visible, child tunnel open, SIP200 and WLAN voice registration restored.
No new call, SMS, authentication test or independent UDP probe was requested.
The on-screen layout has not received visible acceptance in this batch.
Registration and interface counts do not establish audio, native inbox,
notifications or end-to-end SMS delivery; existing intermittent SMS work remains
separate. Reports preserve only closed diagnostic metadata, never SMS bodies,
APN credentials, SIM identities or raw phone logs.
