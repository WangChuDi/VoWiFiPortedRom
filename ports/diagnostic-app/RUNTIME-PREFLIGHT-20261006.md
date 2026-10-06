# Tool0.8.0 runtime preflight and consolidated validation

This batch adds read-only runtime signature lookup and release verification.
It does not install the modern research services or change the running API30
engine. All attribution and licensing remain GPL-2.0 with the existing phhusson
IMS source notices.

## Changed behavior

`RuntimeAbiProbe` checks23 fixed core signatures for EAP/AKA, IKE construction,
network/identity/auth/proposal configuration, child/P-CSCF results, IPsec tunnel
creation/transforms and CarrierConfig read/override lookup. Constructor/proposal/
read aliases use the production order and only fall back for missing classes or
members. Access denial and linkage errors stop fallback. Lookup uses
`Class.forName(..., false, loader)` and never constructs an object, calls the
looked-up method or authenticates the SIM. The rest of RootDiagnostics retains
its pre-existing telephony bootstrap and normal read-only service observations.

The scope is the root app_process class loader, not a claim of all compiled
service contracts or their runtime permissions. Modern candidate means only
SDK31–37. Actual engine eligibility remains the previously tested API30/raphael/
VOXI/SIM_READY profile. Installation, binding, permissions and carrier validation
flags stay false in the probe.

`validate.py --build` provides one batch for one signed tool build, production
contracts and real APK metadata/signature/embedded-engine verification. A
separately built batch can use `--artifacts-only` without repeating contracts.
The report records whether build/contracts ran in that invocation; an artifact
check alone does not establish source freshness or device validation.

## Evidence

* Production lookup fixture:77 assertions passed, including exact signature,
  absent aliases, no initialization/invocation, denied/broken alias termination,
  version boundaries and explicit unverified integration flags.
* One new tool build/sign succeeded with existing deprecation warnings only.
  Compiled APK version0.8.0/code11, min/targetSDK30, signature verification passed.
* Embedded engine is byte-identical to the existing signed controller0.9.0 ZIP:
  SHA256 `c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5`.
  APK SHA256 `6c6a4ef2180c43707029166b2bc515cc302669aa25ada45e76d14f9438ead318`.
* Read-only independent source review found no gate/lookup regression. It
  highlighted artifact-versus-source freshness; the batch report now records it
  explicitly. This is source review, not modern-device integration proof.
* ADB installed the signed APK update successfully on9222ae0e. Its own UI root
  invocation completed on active SIM2, requiring no new grant or reboot.
* That same single UI diagnostic showed:SIM_READY/23415, physical Wi-Fi,
  ePDG DNS3addresses, CHILD_OPENED/IKE+child true/transforms1+1, one matched IMS
  interface with2P-CSCF, WLAN registration, voice+SMS true and SIP REGISTER200.
  The existing full IWLAN+QNS+IMS transaction remains ENABLED.
* The root-loader probe showed5visible/18missing/0inaccessible/0linkage errors.
  This is **not evidence of18missing system APIs**: independently loaded IKE/EAP
  library types were unavailable to this root loader while the declared-library
  service retained a working tunnel. Do not replace libraries based on this count.

This batch used one fixture run, one tool build, one artifact check/install and
one actual app diagnostic. UI scrolling inspected that same result and did not
rerun diagnostics. No module/vendor/APN/modem writes, telephony reload, reboot,
call or SMS was performed. Previously validated call/SMS delivery results remain
separate; the fresh capabilities above are not a new delivery test.

## Next integration work

Need library-aware service-process preflight, modern root/controller/package and
framework binding validation, then real SIM/IKE/call/SMS tests on newer systems
and concurrent dual-SIM evidence. Only the API30 phone was connected. Bounded
standard SDK/PATH/workspace/program installation discovery found no usable
existing emulator; this is not an exhaustive machine-wide absence claim.
The unsigned modern three-service bundle remains research output, never embedded
or installed by this release.
