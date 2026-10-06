# Modern selected-subscription carrier transaction

This is the carrier-configuration layer for a future modern controller, not a
complete installer, boot supervisor or enabled diagnostic-app replacement engine.
The Android11 controller/profile and its working installed APKs remain separate.
The modern research APK bundle still does not contain an installer/controller.

## Why modern persistence needs a separate implementation

The exact AOSP Android12,13 and16 release loaders maintain both a temporary RAM
override and a persisted override. `overrideConfig(..., true)` changes both;
passing null clears both and schedules removal of the selected persisted file.
Configuration merging includes these layers. Saving/removal and phone updates
are asynchronous, so a Binder return is not evidence of disk persistence.

These loaders serialize the selected file through `PersistableBundle.writeToStream`
and read it with `readFromStream`, with a package-version entry. The new modern
reader uses the platform stream API; it does not reuse the API30 XML parser.
The file identity remains carrier package + ICCID + specific carrier ID, under
the phone application's device-protected files directory. Raw identities,
filenames, bundles and SMS data are not exported in runtime reports.

Exact primary release sources inspected:

| Android | Loader source | Decoded source SHA256 |
|---|---|---|
| 12 | [android-12.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-12.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `afb1558fdd2786f0602c6ff8aea956f5989d747313b5f79dabacc5e209c4e479` |
| 13 | [android-13.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-13.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `c5eb214ce3a84358ed6b112f44340c0b4d923d10c77bd5faf18f429dfc3b3e70` |
| 16 | [android-16.0.0_r1](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/tags/android-16.0.0_r1/src/com/android/phone/CarrierConfigLoader.java) | `cdfef33ffe9584d3f2da705b781b77dbb8b4107593c49ddbaf6e922b2454c5a0` |

OEM changes and other modern releases require their own integration evidence.
The root guard accepts SDK31–37; that range is not a claim of runtime validation
on every SDK or of Android17 ROM support.

## Implemented transaction

`ModernProviderTransaction` shares the production subscription-target and exact
file snapshot/restore implementation with the API30 controller. Production
ownership requires an active, ready VOXI/Vodafone23415 subscription at the explicit
slot/sub tuple; the separate test profile requires a non-VOXI disposable emulator.
Private paths reject aliases, symlinks and non-files. A controller-wide file lock
serializes these transactions. Recovery refuses a changed SDK/build fingerprint
or selected persistence identity.

Preparation requires an already-loaded carrier bundle, an empty temporary layer
and no untracked replacement provider. It snapshots the entire effective bundle
and the exact selected persisted file (including its prior absence). Unknown dump
formats and nonempty RAM overrides are refused because their prior contents
cannot be reconstructed losslessly from a text dump.

Masks are IWLAN=1, QNS=2, IMS=4. Apply checks the unchanged baseline and original
file, then writes the selected provider keys with persistent=true. Success
requires both live readback and the selected native-stream file to match.

Phases are persisted before mutations: PREPARING, PREPARED, APPLYING, ACTIVE,
CLEARING, FILE_RESTORING, RESTORED_FILE and RESTORED. Restoration clears the loader's
two override layers and waits for deletion of the selected file and an empty
temporary layer before restoring the original XML. FILE_RESTORING is committed
before writing the original file; resumption at this phase never repeats loader
clearing against an already restored file. RESTORED_FILE/RESTORED retries verify
the original file without deleting it. An unused PREPARED transaction can finish
only if the original baseline is still unchanged. PREPARING remains refused and
retained for future supervisor-controlled archival; partial backups are not reused.

If the original XML existed, restoring it also requires an idle phone-process
reload to make the loader consume that original layer again. The future supervisor
must own that reload and call `confirmRestored()` afterward. Confirmation compares
the entire original bundle and requires an empty temporary layer, rather than
masking restoration by applying a full bundle as a new temporary override.

## Actual tests and remaining work

On 2026-10-06, the preparation owner ran the same newly built helper concurrently
on owned Android13/API33 and Android16/API36 Google-APIs emulators. Both used a
fresh absent selected-override baseline and a single non-VOXI test SIM. The QNS-only
persistent trial passed live/disk confirmation, restoration by a reopened
transaction, full original bundle comparison, absent selected file and safe repeat
restoration. Independent read-only agents then checked each version's cleaned
baseline and native cache stream reader. Those final agent checks ran sequentially
because this conversation temporarily reached its agent limit; the mutation
trials were concurrent. The earlier three-service runtime checks used simultaneous
version agents as documented in the runtime README.

The parser's12 host contracts include null/empty/nonempty/array layers, selection
among different slots, CRLF, duplicate/malformed/missing layers and unterminated
input. They caught and fixed a trailing-blank-line split error. The report checker
has21 contracts for incomplete proof, wrong-version proof and stale success after
refusing a physical-device serial. These checks do not operate on a phone.

This trial does not yet test a pre-existing original XML, restoration across an
actual OS reboot or externally killed helper, or the FILE_RESTORING crash window
itself. Ordinary exceptions attempt recovery; a forced timeout/kill is a failed
disposable test and cannot guarantee cleanup. Private phase evidence is retained.
It does not prove real carrier authentication, IMS registration, call/SMS/native
delivery, simultaneous active SIMs or a production installer/lease/watchdog/UI.
All those remain separate work; modern buttons in the diagnostic app stay disabled.

Build and run the parser/report contracts:

```sh
python ../build-runtime.py
python test-baseline.py
python ../runtime/test-persistence-report.py
```

See [runtime commands and reports](../runtime/README.md) for guarded emulator
execution. New sources retain GPL-2.0. The attributed phhusson/ims source and
license remain under the Android11 port and are not replaced by this carrier layer.
