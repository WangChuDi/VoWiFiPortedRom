# Android12+ service adaptation experiment

This directory builds separate unsigned API31 IWLAN, QNS and IMS applications.
The unsigned research bundle is separate from the signed experimental
[installation and supervision module](module/README.md). Modern diagnostic-app
replacement buttons remain disabled. The connected MIUI Android11 phone uses
tool0.8.0/controller0.9.0 APKs;
no experimental modern APK has been installed on it.

## Boundary and implementation

Android12 added a DataService setup overload containing PDU session ID, slice,
traffic descriptor and match-all policy. The framework default forwards to the
old overload and discards those fields. The modern provider overrides both forms
and copies the request's source LinkProperties before posting to its handler.
The API30 provider and its build remain separate: no NetworkSliceInfo or
TrafficDescriptor types enter its compiled classes.

The implemented backend is still the VOXI/Vodafone23415 EPC IMS backend:

* Only IWLAN access and the IMS APN are accepted. Normal versus roaming protocol
  and the caller's allow-roaming flag are honored.
* Normal IPv4, IPv6 and dual address protocols request matching internal addresses,
  DNS and P-CSCF families through IKE. The outer ePDG transport still requires
  IPv4 Wi-Fi, as in the Android11 backend. This does not establish IPv6 end-to-end
  IMS/SIP/media support or carrier acceptance on a new device.
* An incoming handover carries its existing IPv4/IPv6 addresses into the IKE
  configuration requests. A delegated IPv6 prefix of the same length and bits can
  retain the source IID before configuring the interface, following AOSP's
  approach. An unpreserved source address fails the handover; it is not reported
  as a successful new connection. Multiple source addresses of one family are
  refused rather than silently reduced to one address.
* Source start/cancel handover callbacks retain the existing tunnel. Normal
  deactivation while transferring is refused; framework handover completion or
  shutdown permits its release. Each new session gets a distinct process-local
  CID, so a late release for the old CID cannot close a replacement session.
* Callback publication and provider retirement share a lifetime lock. Slot/sub
  identity is checked before session creation, opening and active call-list or
  source-handover observation. Old generations cannot publish into a replacement.
  These properties do not prove simultaneous registration of two real SIMs.

The backend has no N1/5G or URSP implementation. It accepts PDU ID0 and no slice
or traffic descriptor. Nonzero PDU IDs and traffic descriptors return a failed
DataCallResponse with SERVICE_OPTION_NOT_SUPPORTED. Slice requests return
SLICE_REJECTED. Because no non-match-all rule database exists, match-all=false
returns MATCH_ALL_RULE_NOT_ALLOWED. Out-of-range PDU IDs and malformed source
addresses return invalid argument. An existing active tunnel is preserved when
a conflicting request is refused. Failed target handovers use DO_FALLBACK,
leaving recovery to the source transport rather than forcing another target retry.

## Build and verification

Use the Android11 Java17/ECJ/aapt2/D8 toolchain environment variables. Fetch the
pinned Android12 framework first:

```sh
python ../compatibility/fetch-frameworks.py 12
python build-iwlan.py
python ../compatibility/test-modern-iwlan.py
python ../compatibility/check-iwlan-variants.py
```

`build-iwlan.py` checks the pinned framework SHA256, compiles against API31 and
sets min/target SDK31. Its output is `out/iwlan-unsigned.apk`; it never signs,
deploys, rebuilds or overwrites an Android11 APK. `check-iwlan-variants.py` builds
the current API30 IWLAN sources into a separate compatibility output directory,
checks type isolation, the full modern public overload, modern DEX contents and
the compiled APK SDK boundary.

The production-contract test executes the actual modern provider and address
request class with controlled Android/SIM/session boundaries. On 2026-10-06,
60 assertions passed: request rejection, exact address forwarding, IPv6 prefix
and IID preservation, response families, source cancellation/completion, stale
CID release, SIM removal/change, provider separation, null callbacks, old
callback rejection and retirement concurrent with publication. The real API31
build and pinned-framework checks are separate from this test; the test does not
execute Android Binder, permissions, IKE negotiation or carrier traffic.

The compiled modern variant's Android method/field references and superclass
contracts resolve in the seven pinned Android12,12L,13,14,15,16,17 samples.
Reports stay under `../compatibility/out/modern-iwlan-*`. A Robolectric Android17
artifact is not evidence of release status or support for any Android17 ROM.
Missing runtime hidden API, root permission, SELinux or system integration
requirements can still prevent binding/connection on those versions.

## Independent three-service build

Using the same toolchain, run:

```sh
python build-services.py
python ../compatibility/check-modern-services.py
python ../compatibility/test-carrier-config-read.py
```

The build checks the pinned API31 framework, reuses the modern IWLAN provider,
compiles the shared QNS and separately generates the attributed phhusson IMS
port with `prepare-ims-source.py --variant api31`. All modern source/classes/APKs
stay under this directory's `out`. The source generator rejects output aliases;
the default variant still writes to the legacy location. The full API30 output
and preserved upstream source are hashed before and after, including on failure.
A failed rebuild removes the prior research manifest/bundle rather than leaving
an old success advertised as current.

QNS and IMS use dedicated min/targetSDK31 manifests, explicit service exports,
binding permissions and root-only status providers. Compiled artifacts retain
the same package identities for framework integration. They are not installed
over the API30 applications. The existing IMS subscription-aware entry points
are verified in the actual compiled class, alongside APK role classes and SDKs.

Output `out/modern-services-unsigned.zip` includes three unsigned APKs, exact
payload hashes/framework provenance, upstream license and third-party notices.
It contains no installer, boot scripts or controller. `installer_included=false`
and `device_validated=false` are deliberate evidence boundaries. The artifact
checker validates the exact archive, compiled manifest service/action/binding
permissions/exports, status-provider guard, required IKE library and DEX roles; it does
not prove permission grants, binding or carrier behavior.

The original three-service stack had251 Android/internal-framework references
and21 Android-derived classes. After adding the service-runtime validation
provider, the current stack has271 references and22 Android-derived classes.
Independent checks passed the pinned12L–17 samples without missing references,
findings or unresolved Android ancestors. The earlier251-reference reports under
`../compatibility/out/modern-services-*` describe the earlier build. Reflective
calls and Binder/permission/SELinux/network behavior are excluded. Runtime
results and their version-specific scope are documented in `runtime/README.md`.

CarrierConfig read resolution now recognizes the framework's
`getConfigForSubIdWithFeature(int,String,String)` name when the two-argument
method is absent, retaining the prior OEM spelling as a final absent-method
fallback. Invocation errors and null results are never retried through another
method. Fifteen controlled contract assertions passed; an isolated new helper
also read the existing API30 configuration without changing the module. This
does not remove the controller's API30 device/profile gates or establish modern
Binder permissions. The source change has not replaced the installed API30 helper.

## Remaining modern work

The IWLAN experiment includes a root-only service-loader runtime probe and a
separate root integration helper. Build all related pieces in one batch with
`python build-runtime.py --services`; see [runtime checks](runtime/README.md).
This checks library lookup in the declared-library process and distinguishes root
explicit Binder connection from actual telephony provider selection. The research
bundle remains unsigned and has no production installer or controller.

The [modern carrier transaction layer](controller/README.md) now adds selected-SIM
snapshot, native-stream persistence readback and resumable restoration. Its
QNS-only persistence/reopened-transaction trial passed concurrently on API33 and
API36. The experimental module now includes installation and supervision; app UI,
real device lifecycle and shared dual-SIM permission ownership still need work;
it does not enable the modern replacement buttons or prove carrier call/SMS.

* N1 PDU/3GPP extension negotiation, returned slice handling and actual URSP
  matching, before accepting their corresponding requests.
* Modern controller/Binder/provider selection, permission/install profiles,
  foreground-service behavior and IKE runtime checks on real systems.
* Integration with QNS and IMS; real call/SMS/notification tests, including
  cellular/WLAN handovers, Wi-Fi changes and two active subscriptions.

The upstream phhusson/ims source and its GPL-2.0 attribution remain in the
Android11 IMS port and THIRD_PARTY documentation. This directory adds an IWLAN
adapter; it does not replace or remove that source/attribution. The new code
uses GPL-2.0 SPDX tags; AOSP sources below explain contracts and address behavior.

## Primary references

* [AOSP DataService request and source handover contracts](https://android.googlesource.com/platform/frameworks/base/+/master/telephony/java/android/telephony/data/DataService.java)
* [AOSP IWLAN request/response handling](https://android.googlesource.com/platform/packages/services/Iwlan/+/5629b21/src/com/google/android/iwlan/IwlanDataService.java)
* [AOSP tunnel requests and IPv6 IID preservation](https://android.googlesource.com/platform/packages/services/Iwlan/+/5629b21/src/com/google/android/iwlan/epdg/EpdgTunnelManager.java)
* [Pinned framework sample limitations](../compatibility/README.md)
