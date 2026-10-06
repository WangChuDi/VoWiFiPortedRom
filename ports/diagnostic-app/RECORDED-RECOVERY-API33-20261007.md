# API33 original-record recovery and final 0.9.5 regression

The retained Android13 emulator had two unfinished installation records and one
old seeded-persistence carrier record. Recovery reused those records and their
saved APK/UID/build, permission policy, XML and outer carrier snapshots. It did
not delete the failed fixtures or create replacement baselines.

## Observed failure and scoped repair

The original XML filename matched the current specific carrier identity, its
package-version string matched the installed CarrierConfig package, the SIM was
ready, and the loader consumed the original test marker. The live bundle exactly
matched the saved outer configuration merged with the saved XML, excluding the
XML serialization metadata. The old serialized snapshot contained 655 keys;
the disk-loaded bundle contained 654. The sole difference was
`__carrier_config_package_version__`; there were no other missing keys, extra
keys, changed values or changed types.

The read-only saved-record observation confirmed that this version value was
also identical to the original XML. The evidence proves this old snapshot's
content; it does not establish precisely how the historical writer captured it.

The new recovery helper is isolated to the existing named API33 test emulator.
It requires the original `RESTORED_FILE` handoff, matching owner and XML backup,
one ready non-VOXI fake SIM, original outer state, and the held global lock.
After the normal reload comparison refuses the legacy snapshot, it accepts only
the proven serialization-version difference. Every actual carrier configuration
value must equal both the live bundle and the original outer-plus-XML bundle.
It then commits the original test record's phase and restores the saved pristine
outer state. Snapshot, backup XML, persistence metadata, profile, owner and outer
record bytes are checked unchanged. The serialized snapshot remains preserved;
raw snapshot equality is explicitly reported as false.

Production `ModernProviderTransaction.same()` and `confirmRestored()` retain
their exact bundle comparison. The signed tool, production helper, service APKs
and module were not rebuilt for this repair. This fixture-specific recovery is
not an alternate production carrier policy.

Both original installation records were then restored with the retained helper
whose SHA-256 is
`9bc8fd733a643cccaba14f5d479e34688d6a1dea05e5afe0783e52fcebfdcf3e`.
Its actual DEX contains both installation transaction and emulator trial classes.
The final audit found zero pending installation and carrier records, with the
same owner and outer record aggregates. Original outer permission policy was
restored, including grants, flags, SMS exemption and IPsec app-op.

## Validation and boundaries

The unchanged final module/helper ran the default API33 suite: 41 stage keys
passed, including detached-owner handling, shared roles, supervisor and cleanup.
The signed 0.9.5 tool's root diagnostic and refusal checks also passed. The owned
emulator processes terminated normally after the batch. The reports preserve
the earlier failed reload and its unchanged-evidence cleanup.

These are named-emulator and fake-SIM tests. They do not prove modern Magisk
mounting, real carrier traffic, two simultaneously active real SIMs, or Android17
completion. Physical Android11 traffic evidence is recorded separately.

Source: [API33 recovery helpers](../android12/runtime/recovery/api33-recorded/).
Evidence: [published reports](reports/20261007-api33-recorded/summary.json).
The existing phhusson/ims attribution and GPL-2.0 licensing remain in place.
