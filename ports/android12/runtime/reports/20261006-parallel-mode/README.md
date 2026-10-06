# Parallel API33 / API36 mode and coordination checks

These reports record actual two-guest concurrent checks on 2026-10-06. New agent
dispatch was refused with `agent thread limit reached`; there were **no concurrent
agents in this batch**. Host device workers ran concurrently with one explicit
owned emulator per version. The physical API30 phone was not changed.

`matrix.json` is the initial installation/binding/mode observation with helper
SHA256 `3046e489f48e82374e6d081b0eb0c57f82dd76fd54e9c1cb4ab7460462a4a792`.
Both installation-permission profiles were ready. Both showed a received QNS
binding from the phone process; neither showed an active IWLAN or IMS phone
binding. Property presence was false on both. API33 exposed `isInLegacy=false`;
API36 exposed an AccessNetworksManager without that legacy field. Unknown cached
transports are reported as null, not inferred as WLAN or as a successful tunnel.

`integration.json` is the first nine-stage concurrency/recovery run. `final.json`
adds explicit closed-handle refusal assertions and is the authoritative final
helper/module test. Every stage uses a separate app_process. The coordination
stage checks that closing borrowed carrier and installation transactions keeps
the shared global file lock held, using **separate child processes** as lock
contenders, and rejects wrong-thread lock use. The carrier baseline is only
snapshotted and restored without selecting a replacement. The default AP-assisted
mode record completes a no-reset snapshot/release/archive cycle and preserves
the original property's presence as well as value. The other-owner Boolean is
a synthetic contract, not a second active SIM.

The remaining stages prepare/retain/restore the fixed permissions/AppOp/SMS
exemption and their original flags; refuse a foreign IPsec policy change; and
resume/repeat a simulated persisted RESTORING phase. Outer cleanup restores the
full pre-test fixed-policy observation. Phone process IDs remain unchanged.
The shared-mode **legacy property mutation / phone-cache refresh branches have
not been exercised**. The mode class is an internal coordinator foundation and
is not exposed by the installation-stage module or diagnostic-app UI.

The host parser has 21 contracts: nested Android12 TransportManager/ANM, distinct
SIM slots, absent modern legacy fields, indented logs, duplicate/contradictory
managers and malformed transport lists. It avoids Java11 String.stripLeading.

This is configuration/permission/lock evidence on Android13 and Android16.
Actual Magisk mounting/disable/remove/boot, successful modern component selection,
lease/retention supervisor, genuine carrier registration, voice/SMS and two active
SIMs remain unverified. Other Android releases were not newly runtime tested.
Existing Android11 results and attributed phhusson/ims GPL-2.0 source are retained.
