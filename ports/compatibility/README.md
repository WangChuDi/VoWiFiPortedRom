# Framework compatibility work

This directory compares pinned AOSP-derived `org.robolectric:android-all`
framework samples without loading or executing their code. It is a source and
class-file compatibility check, **not a device support matrix**. Only the
Android11 raphael/VOXI replacement profile is enabled by the installer.

## Reproduce

Use the existing Java/ECJ toolchain and build the Android11 stack first:

```sh
python fetch-frameworks.py 12 12L 13 14 15 16 17
python read-framework-abi.py /path/to/android-all-11.jar out/frameworks/*.jar
python check-linkage.py --classes ../android11/stack/out/iwlan-classes ../android11/stack/out/qns-classes ../android11/stack/out/ims-java-classes ../android11/stack/out/ims-kotlin-classes --frameworks /path/to/android-all-11.jar out/frameworks/*.jar
python test-contracts.py
```

Expand wildcard arguments with your shell. Downloads use immutable version
coordinates, registry checksums and source-pinned SHA256 values. They are cached
atomically with per-artifact provenance. Generated JARs/reports/classes are
ignored; framework binaries and signing keys are not redistributed.

## Evidence on 2026-10-06

The newly compiled IWLAN/QNS/IMS service classes contain 235 distinct Android or
internal-framework method/field references. All resolve, including inheritance,
in the eight pinned framework samples: Android11, 12, 12L, 13, 14, 15, 16 and 17.
Including the diagnostic app gives 301 distinct references, also with zero
missing references in all eight samples. The two explicitly documented
core-Java inherited methods are handled separately;
the checker does not accept an arbitrary missing `java.*` method.

| Sample | Artifact version |
| --- | --- |
| Android11 | Existing Android11 build-toolchain sample |
| Android12 | 12-robolectric-7732740 |
| Android12L | 12.1-robolectric-8229987 |
| Android13 | 13-robolectric-9030017 |
| Android14 | 14-robolectric-10818077 |
| Android15 | 15-robolectric-13954326 |
| Android16 | 16-robolectric-13921718 |
| Android17 | 17-robolectric-15733970 |

The framework class inventory also records constructors, exception callback
names, DataService setup overloads, subscription-aware ImsService methods and
CarrierConfig Binder interface signatures. References made through reflection
are **not** covered by either reference-count result. Visibility, hidden-API rules,
Binder implementation, permissions, SELinux, modem behavior, IPsec availability
and actual network service integration require separate device validation.

Android11's framework sample lacks IKE/EAP module classes. The connected MIUI
device has a working backport: that was verified on the phone, not inferred from
the Android11 sample. The later framework samples contain those classes. A
Robolectric Android17 artifact does not establish release status or compatibility
with any particular Android17 ROM.

## Implemented adaptation boundaries

* `IkeApiCompat` prefers `addIkeSaProposal` and accepts the older alias only when
  the preferred method is absent. An invocation/permission error never triggers
  a misleading alias fallback. Builder construction prefers the existing
  Context constructor and uses a no-argument constructor only if absent.
* EpdgSession handles both exception-close callback names. Both names and both
  proposal methods coexist in some later framework samples; the older alias was
  **not proven to be the cause** of any device registration failure.
* The IMS adapter exposes subscription-aware virtual signatures while compiling
  against API30. Features, registration objects, configs and alarms are owned
  by slot/subscription, and callbacks retain their original registration object.
* Shared subscription snapshots reject SIM changes before authentication, IKE
  session creation and child publication. The pure-Java gate tests cover boot,
  expiry, legacy authorization and independent per-subscription leases.
* Feature advertisement uses authorized active subscription identity separately
  from transient SIM readiness. Feature setup waits for readiness; a dynamic
  update notifies the framework when authorized slots change. On this MIUI,
  initial STATE_INITIALIZING prevented binding, so the adapter retains its
  validated service-ready state and advertises transport capabilities only
  after registration.

The controller now records an explicit selected slot/sub owner on API30 raphael;
positive device validation still covers slot1/sub1. Simultaneous dual-active
SIM execution and modern installation/controller profiles need appropriate
devices. Global IWLAN operation mode, whole-phone reload and transaction-wide
rollback are not per-SIM operations.

The ABI inventory now includes DataService, QNS/NetworkAvailabilityProvider and
NetworkService/NetworkServiceProvider in every pinned sample. This closes the
earlier inventory gap for those classes; signatures/access flags alone still do
not prove runtime forwarding, abstract-method coverage or handover behavior.

## Sources

* [Maven Central android-all artifacts](https://repo.maven.apache.org/maven2/org/robolectric/android-all/)
* [Android IKE builder](https://developer.android.com/reference/android/net/ipsec/ike/IkeSessionParams.Builder)
* [Android IKE callback](https://developer.android.com/reference/android/net/ipsec/ike/IkeSessionCallback)
* [AOSP Android11 dynamic IMS feature contract](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-30/+/refs/heads/androidx-appcompat-release/android/telephony/ims/ImsService.java)
* [AOSP DataService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/data/DataService.java)
