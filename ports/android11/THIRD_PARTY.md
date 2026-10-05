# Sources, attribution and licensing

## phhusson/ims

- Original project and authors: [phhusson/ims](https://github.com/phhusson/ims) and its contributors.
- Upstream baseline: [`c180bdff810880d8f75f5dda70e6bcbee6991e9e`](https://github.com/phhusson/ims/commit/c180bdff810880d8f75f5dda70e6bcbee6991e9e).
- Android 11 adapted source: [`WangChuDi/ims@c9b8b273905ef8b62272ffc8cc7841429b94f2df`](https://github.com/WangChuDi/ims/commit/c9b8b273905ef8b62272ffc8cc7841429b94f2df).
- License: GPL-2.0; the original full license is retained in [vendor/phhusson-ims/LICENSE](vendor/phhusson-ims/LICENSE).
- The Java/Kotlin subtree is vendored with original notices intact. `android11.patch` records the changes against the baseline; this is not a complete upstream Gradle checkout.
- Changes include API30 compatibility, SIM/network selection and SMS address, error and acknowledgement handling. See CHANGES.zh-CN.md for details.

## Suiying6023/VoWiFiPortedRom

This repository is a fork of [Suiying6023/VoWiFiPortedRom](https://github.com/Suiying6023/VoWiFiPortedRom).
Its PORTING-GUIDE.md informed the application-side IMS and supervised root-module approach.
The Android 11 companion scripts were written for this port; they do not deploy the original three-APK replacement stack.
Existing upstream files and attribution remain in place.

## Android 11 additions

Android 11 port additions by WangChuDi (2026) are distributed under GPL-2.0, consistent with the repository [LICENSE](../../LICENSE).
Externally acquired build tools retain their own licenses; they are not included here.
Device framework/vendor binaries, signing keys, subscriber identifiers, private captures and SMS logs are not included.
