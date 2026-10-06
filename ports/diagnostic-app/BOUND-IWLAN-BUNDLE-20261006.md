# Tool0.9.2: bound IWLAN and selected-role readiness

VersionCode14 / `0.9.2-diagnostic`, min/targetSDK30, embeds the modern IWLAN
bound-service correction and selected-role readiness helper. Application action
semantics remain those introduced in [tool0.9.0](MODERN-INTEGRATION-20261006.md).
The modern service fix avoids the API34/API35 binding crash; the helper permits
QNS-only selection without requiring an unselected IWLAN IPsec AppOp, while
full preparation and all payload/privileged identities remain guarded.

| Final artifact | SHA-256 |
|---|---|
| Signed tool APK | `2fd68b0d012e9523f935a7c9e91cedea78138b695fcc260c588a57cfcf43a630` |
| Embedded modern module | `de5826326df086f2694aae88a3973f817b1cc0f30e1425c6ace42721cf7f70de` |
| Modern helper | `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d` |
| Unchanged embedded API30 module | `c22f6987d30ba2eaeffa00c1f7cf530b5bf8c11241ded8bc9fe69bd8cc878ce5` |

The initial 0.9.2 unsigned build passed 77 unchanged ABI lookup contracts and
source/DEX/metadata/asset checks, recorded in
[`initial unsigned contracts`](reports/20261006-app092-initial-unsigned-contracts.json).
That initial draft embedded the earlier `baec4ae8...` module, so it is not final
artifact identity. After the helper and fixture corrections, the final app was
rebuilt with exact `de582632...` module bytes; unchanged lookup contracts were not
repeated. The final unsigned source build is
[`separately recorded`](reports/20261006-app092-final-unsigned-artifacts.json).
The retained external application key signed it; final
[`artifact-only verification`](reports/20261006-app092-final-signed-artifacts.json)
confirms signature, compiled version/SDKs and both byte-exact embedded engines.
That invocation records build/source/contracts false because it verifies retained
artifacts rather than repeating the preceding build. Signing material is external.

[`Compiled application checks`](reports/20261006-modern-app092-final.json) ran
concurrently on named API31/API34/API35 guests with the same final signed tool.
They verify root diagnostics/SDK dispatch, nonroot and malformed action/identity
refusals, missing-module refusal and unchanged production state presence. Their
fake-SIM IMS callbacks are absent. These checks invoke the installed compiled root
backend; they do not prove positive application-UID UI selection or carrier behavior.

The embedded modern helper separately passed
[49 lifecycle stages on each of those three SDKs](../android12/runtime/reports/20261006-bound-iwlan/README.md),
including actual guarded PREPARING SIGKILL/recovery, selected-role refusal/preservation
and complete outer cleanup. OS reboot, modern Magisk hooks and real dual-active-SIM
or carrier traffic remain outside those fixtures.

ADB update on the connected API30 MIUI phone succeeded. The
[`sanitized current regression`](reports/20261006-api30-regression092-final.json)
matches installed APK bytes to the final signed tool and invokes its root diagnostics:
full mask7 ACTIVE, persistent ENABLED, AP-assisted, IWLAN CHILD_OPENED/IKE+child,
QNS IWLAN_SELECTED, IMS REGISTERED, one attributable IMS network/two P-CSCF,
WLAN transport and advertised voice/SMS capability. No engine/settings/reboot,
new call or SMS was performed in this regression. It does not repeat earlier
audio/native-SMS/notification proof or establish own-app-UID UI behavior.

The preserved first 0.9.2 signed draft is `b79dd1efe21671a1023dac76ce25c8f3d4e0ceeb348e65f266c8ab795e2adf39`
with module `baec4ae8...`; it is historical, not the current installed tool.
Tool0.9.1's [recovery record](RECOVERY-BUNDLE-20261006.md) retains its historical
hashes and then-pending coverage. Runtime API31/API34/API35 has the new scoped
evidence; the [later API32 continuation](../android12/runtime/reports/20261006-api32/README.md)
uses the identical signed artifacts and adds runtime/app checks and 49 stages.
The [API37 continuation](../android12/runtime/reports/20261007-api37/README.md)
adds runtime/app checks with identical artifacts, but its full lifecycle failed
and cleanup is unconfirmed. SDK37 selection/recovery, modern carrier/application trials, modern Magisk and
simultaneous active dual SIMs still need work. The full Android11–17 goal remains
active. [phhusson/ims source and GPL-2.0 notices](../android11/THIRD_PARTY.md) remain intact.
