# Android 11 application-side stack experiment

This is a separate experiment, not a replacement for the working SMS companion.
Baseline: `android11-companion-0.2.0-baseline` / commit
`ccd2aa0b54cdad5d7d357a83b594f55402ab944f`. The companion directory and installed
module remain unchanged. Private device/module/config backups are stored outside
this repository and must not be committed.

## Current result (2026-10-05)

Device: raphael, MIUI V12.5.1.0.RFKMIXM, API30, VOXI 23415, slot1.

- Actual telephony bytecode checks `legacy` or Radio HAL <1.4 in
  `TransportManager.isInLegacyMode()`. The device exposes standard Radio HAL1.4;
  its current property is `legacy`. No property was changed.
- QNS, WLAN DataService and WLAN NetworkService package override keys exist.
  Their presence is not proof that switching modes/services works end to end.
- Runtime API inventory confirms EAP-AKA, EAP-only authentication, P-CSCF requests,
  tunnel-mode child sessions and the API30 IMS SMS callback/acknowledgement APIs.
- `IndependentEpdgProbe` established its **own** IKE and child session over the
  physical Wi-Fi network using the system IPsec/IKE library. It obtained two
  P-CSCF servers and one internal IPv4 address and applied both IPsec transforms
  to its own tunnel interface. It then closed the session and resources.
- This does not reuse the vendor IMS network. It also does not register its
  tunnel as an Android IMS network, send SIP REGISTER, send SMS or make calls.
- The companion subsequently still reported a successful IMS registration and
  reception-ready status. The device-default IMS remained `org.codeaurora.ims`,
  the IWLAN mode remained `legacy`, and I/O pressure averages were zero.
- API30 trial QNS Java compilation, DEX generation and unsigned APK packaging
  succeeded. The APK has **not** been installed or selected as a provider.

## Components

`StackApiProbe.java` prints only selected API signatures/constants. It performs
no subscriber reads or network operations.

`IndependentEpdgProbe.java` is an explicit, one-shot connection experiment,
restricted to slot1 / operator23415 / IPv4. It performs real SIM authentication
and network IKE traffic, but never prints subscriber identity or keys. It uses
the already-installed Android11 IKE library rather than bundling a newer APEX.
It uses EAP-only authentication and accepts the peer's IKE identity, matching the
tested operator flow; these settings are not a generic VPN configuration.

The original app_process attempt failed before network authentication because
AMS could not find an application record when IKE registered its receiver.
The root-authorized probe context supplies a directly registered Binder receiver
on a dedicated HandlerThread. This shim is only for the diagnostic process;
a real IWLAN APK should use its normal application context.

`qns/TrialQnsService.java` is a new, gated API30 implementation informed by the
upstream `code/minqns` approach. It only reports IMS/IWLAN for slot1 with VOXI,
the user's WFC setting enabled, a physical Wi-Fi internet-capable network, and
an explicit same-boot trial window of at most120 seconds. It withdraws the report
when conditions stop matching. It is not a complete carrier selection policy.
The two trial setting names are `codex_wfc_stack_trial_until` (elapsed-realtime
milliseconds) and `codex_wfc_stack_trial_boot` (matching Android BOOT_COUNT).
No script sets them automatically. These gates are not a provider rollback mechanism.

## Build and probe

Use the parent directory's documented Java17 / ECJ / D8 / android-all11 toolchain.
The QNS build additionally needs SDK Build Tools `aapt2`, selectable via `AAPT2`.
Generated artifacts remain ignored under `out/`.

```sh
python build-api-probe.py
python build-qns.py
```

The QNS result is unsigned. Do not flash it or switch provider configurations:
the WLAN data/network services and rollback supervisor are not yet implemented.

Read-only runtime inventory (after pushing `out/api-probe.zip` to
`/data/local/tmp/codex-stack-api-probe.zip`):

```sh
su -c 'CLASSPATH=/data/local/tmp/codex-stack-api-probe.zip:/apex/com.android.ipsec/javalib/android.net.ipsec.ike.jar app_process /system/bin StackApiProbe'
```

Explicit independent tunnel test on the validated device:

```sh
su -g 1000 -G 3003 1000 -c 'CLASSPATH=/data/local/tmp/codex-stack-api-probe.zip:/apex/com.android.ipsec/javalib/android.net.ipsec.ike.jar app_process /system/bin IndependentEpdgProbe --connect-once'
```

The negotiation wait is35 seconds, followed by a5-second graceful-close window
and force-close fallback for the owned session. DNS/platform initialization is
outside that negotiation timer. The process releases its own transforms/tunnel;
it never flushes global XFRM state. Do not run repeated concurrent probes.

## Remaining work before selecting replacement providers

1. Adapt an IWLAN `DataService`/`NetworkService` to the actual API30 signatures;
   pass interface, internal addresses, DNS and P-CSCF back to telephony. Verify
   framework-created IMS network routing and cleanup, not merely IKE success.
2. Package/sign the privileged services and minimal required permission allowlists.
   Implement scoped, timed rollback of provider overrides and IWLAN mode before
   any live switch. `legacy` is consulted during framework initialization, so a
   runtime property change alone is not a validated migration procedure.
3. Bind a properly adapted IMS MMTEL provider to the new network. Existing phh
   source exposes `ImsSmsImplBase.onSmsReceived` / `acknowledgeSms`, but binding,
   delivery, sending and voice are not validated by this experiment. Its voice
   path also references `rnnoise_jni`, not supplied by this experiment.
4. Test inbound/outbound SMS acknowledgement through telephony, actual calls,
   audio, switching, expiry, reboot and rollback. Outgoing test SMS/calls require
   an explicitly authorized destination.

The reference Android12 IWLAN uses APIs absent here (including 3GPP IKE extensions
and newer data-call parameters). Reusing its prebuilt APK is not API30 adaptation.

## Provenance

- Architecture and MinQns reference: Suiying6023/VoWiFiPortedRom, retained in this fork.
- IWLAN candidate: https://android.googlesource.com/platform/packages/services/Iwlan/
  branch `android12-release`, commit `75c089942bbfb5cab1a9decc30ee535cf5d70699`;
  inspected, not vendored or installed.
- Runtime IKE: the device's `/apex/com.android.ipsec/javalib/android.net.ipsec.ike.jar`;
  private inspection input, not redistributed.
- IMS source and GPL-2.0 attribution remain in the parent [THIRD_PARTY.md](../THIRD_PARTY.md).
- New experimental sources are GPL-2.0 under the repository license.
