# Android 11 VOXI Wi-Fi SMS companion (0.2.0 trial)

## Scope

Tested on **raphael, MIUI V12.5.1.0.RFKMIXM, Android 11/API30, arm64, second SIM
slot, VOXI/Vodafone UK 23415**. The installer deliberately rejects other devices
and Android versions. This is a source publication of an experimental port, not
a broadly compatible VoWiFi fix.

The vendor IMS/ePDG connection must already work. Native voice IMS is left bound.
This companion creates a separate IMS registration to receive ordinary single-part
SMS and uses the default SMS application for persistence and notification.

## Architecture and changes

1. Run the receiver as UID1000 **with inet supplementary group3003** so restricted
   IMS sockets actually receive the network mark; check the selected SIM network.
2. Complete USIM AKA and establish application-side IMS transport IPsec.
3. A one-shot root broker accepts two socket FDs only from UID1000 and installs
   socket-local inbound transport+tunnel templates matching the native ePDG path.
   It does not flush or rewrite global XFRM policies or vendor files.
4. Decode RP-DATA; reject unsupported TPDU forms. Suppress already-stored messages.
5. Deliver the unmodified 3GPP PDU through a protected explicit `SMS_DELIVER`
   broadcast to the sole default SMS role holder, with the correct subscription.
   Wait for and verify its inbox record before SIP200 and RP-ACK. Track the
   corresponding RP-ACK SIP response separately from SMS submission results.
6. A Magisk service supervises sessions, sends keepalives, conservatively renews
   by deregistering/re-registering, and backs off on failures. Normal stop releases
   sockets and temporary transforms. Current/previous logs and a bounded event log
   contain operational status, not SMS bodies or AKA keys in normal module mode.

The public `SmsManager.injectSmsPdu` path was tested but rejected class-unspecified
messages on this MIUI build (`not class 1`). The final implementation does not
alter the DCS to work around that check; it delivers to the actual default SMS app.

The vendored `vendor/phhusson-ims/app/` changes are **experimental API30 core compatibility and SMS-state
fixes**. Its Gradle APK, voice/media paths, and full provider replacement are not
validated. The Magisk companion is the tested deployment, not the upstream APK.

## Build

Use Python3, Java17 and separately obtained Android SDK Build Tools30.0.3. Paths
are configurable through `JAVA`/`JAVA_HOME` and `IMS_PORT_TOOLCHAIN`. Run from this
directory. `fetch-toolchain.py` explicitly downloads pinned compiler dependencies
from Maven Central; it does not install system software or Android SDK licenses.

```sh
python fetch-toolchain.py
# Copy the SDK's build-tools/30.0.3/lib/d8.jar to toolchain/android-build-tools/d8.jar
python compile-api30.py
python test-sms.py
python build-core-probe.py
python test-digest.py
```

The hidden API compile input is Robolectric `android-all:11-robolectric-6757853`,
not a repackaged vendor framework. Kotlin1.9.24, coroutines1.6.1 and ECJ3.37.0 are
used. A successful compile does not establish compatibility with other ROMs.

Native helper, preferred portable source-build route (NDK compiler driver):

```sh
export ANDROID_NDK_CLANG=/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang
python build-native.py
python build-module.py
```

The original tested build used Zig's Linux headers and linked against **a local
copy of the test phone's Bionic libc**. To reproduce that route on Windows/Linux:

```sh
export IMS_ZIG_DIR=/path/to/zig
export IMS_BIONIC_LIBC=/private/local/path/to/libc.so
python build-native.py
python build-module.py
```

The NDK build route is provided but has not been device-validated. Do not commit
or redistribute pulled device libraries. The resulting module ZIP contains the
built JNI helper and DEX, not the input libc/framework. Generated files are ignored.
On PowerShell set environment variables with `$env:NAME='value'` instead of `export`.

## Install and control

Install `voxi-wifi-sms-android11-trial.zip` using Magisk. No vendor/system overlay,
Zygisk injection, APK replacement, or SELinux rule is included. `service.sh` waits
for boot completion plus60 seconds. **Reboot autostart has not yet been tested.**

When running, a session lasts at most900 seconds and is shortened for the network's
granted registration lifetime. Normal cycles have a short deregistration gap;
failure retries back off from60 seconds to600 seconds. The existing Wi-Fi IMS
network must be available. There is no automatic reboot or modem restart.

Root commands:

```sh
sh /data/adb/modules/codex_vowifi_sms/control.sh status
sh /data/adb/modules/codex_vowifi_sms/control.sh stop
sh /data/adb/modules/codex_vowifi_sms/control.sh start
```

The Magisk Action button, where supported, toggles pause/resume. Pause persists
across boots; `start` clears it. Disabling/removing the module requests a graceful
stop from a running supervisor. Logs are root-private under
`/data/adb/codex_vowifi_sms/`; runtime files use
`/data/local/tmp/codex-vowifi-sms/`. Uninstall does not delete received messages.

Version0.2.0 **does not grant WRITE_SMS**. The original ignore mode remained in
place during the successful notification test. Cleanup retains compatibility for
an old0.1 test's saved AppOp state. UID1000's existing privileges are still needed
for SIM/network access and the protected delivery broadcast.

## Verified and unverified

Verified on the target phone: IMS REGISTER200; deregistration200; RP-DATA decoding;
default-app inbox delivery; Google Messages incoming-message notification;
RP-ACK accepted with SIP202; repeated short registration cycles; manual stop;
resident reception continuing after delivery. User confirmed inbox visibility.
Notification presence was checked in Android's notification service; sound and
heads-up presentation still depend on the user's channel/DND settings.

Offline tests cover SMSC/RP handling (41 assertions), SIP/RP completion ordering,
independent digest vectors, repeated/folded headers, multiple Contacts, binary
framing, partial-read timeouts, and20000 keepalives. No private logs are published.

Not complete: SMS sending (RP28), multipart/UDH/port-addressed messages, replace
messages, status reports, third-party SMS_RECEIVED/OTP autofill, full IMS service
replacement, long-term battery use, complex handovers, reboot autostart, and actual
call/SMS coexistence testing. Native voice capability remained reported, but that
alone does not prove real call coexistence.

The diagnostic Java sources retain explicit opt-in one-shot send/decode modes
used during development. **Normal module mode never enables those modes.** Do not
set SMS test destination variables unless you intend to send that message; decode
mode can expose SMS contents in local output. The upstream full SipHandler also
has verbose diagnostic logging; it is not the module's authentication engine.

## Attribution

Based on phhusson/ims at `c180bdff810880d8f75f5dda70e6bcbee6991e9e`, GPL-2.0.
Porting guidance: Suiying6023/VoWiFiPortedRom PORTING-GUIDE.md.
Platform references: Android Telephony SMS delivery APIs, Linux4.14 XFRM socket
policy implementation, and the official Magisk developer guide. No claim is made
that the referenced port's full functionality has been reproduced.

## Source layout and license

- `module/`: Magisk lifecycle scripts; Java files and `nested-policy.c`: receiver and socket-policy helper.
- `vendor/phhusson-ims/app/src/main/java/`: source snapshot including the Android 11 changes.
- `vendor/phhusson-ims/android11.patch`: exact Java/Kotlin delta against the upstream revision.
- [THIRD_PARTY.md](THIRD_PARTY.md): upstream revisions, attribution and licensing.
- [CHANGES.zh-CN.md](CHANGES.zh-CN.md): detailed Chinese change list and limitations.

This directory builds independently of the repository's Android 17 `code/` module.
It does not include a replacement IWLAN or QNS implementation. A complete application-side
IWLAN/QNS/MMTEL replacement remains future work; do not install the Android 17 APKs on API30.

The separate [stack experiment](stack/README.md) now contains an API30 capability
probe, a successfully tested independent ePDG tunnel probe and an unsigned trial
QNS build. Those artifacts are not part of this working companion module.
