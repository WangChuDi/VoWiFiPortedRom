# Android12+ IWLAN adaptation experiment

This is a separate, unsigned API31 IWLAN build. It is **not** a complete modern
IWLAN/QNS/IMS installation profile and is not included in the Android11 module
or diagnostic app. Android12–17 replacement engines remain disabled. The connected
MIUI Android11 phone continues using the published tool0.5.1/controller0.7.1 APKs;
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

## Remaining modern work

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
