# Owner supervision and independent recovery, API33 / API36

`second.json` passed concurrently on the owned Android13/API33 and Android16/API36
Google-APIs emulators: 33 independent app_process stages per version. The host
runner used two device workers; read-only agents separately reviewed source and
evidence. These are separate processes for successive stages, not real forced
termination, OS reboot or a Magisk mount test.

The batch includes the earlier selection/installation contracts and adds complete
recovery-generation publication, an uncommitted publication handoff, fresh
installation archive/preparation, mask7 trial/renewal, pending helper upgrade
refusal, missing enabled-module APK recovery, a second preparation cycle,
missing/empty owner inventory, expiry/retention, role identity refusal, disable
recovery, repeated recovery, older generation stop and three ordered cleanups.
The fixture's helper-generation and journal changes are explicitly simulated.
No replacement APK was removed from the emulator's system installation.

The native carrier live bundle and persisted file are checked, followed by the
complete original configuration, lease and mode recovery. Selected-role recovery
checks its fixed grant/flag/AppOp baseline; outer cleanup checks all recorded
fixed runtime grants/flags, SMS system exemption and effective IPsec mode against
the original observation. Both phone PIDs stayed unchanged. This is not an
inventory or restoration of unrelated AppOps, files or application policy.

Production guards separately refused nonroot installation/selection, a missing
module, invalid mask and non23415 owner. The supervisor's alive probe did not
create coordination state. Module shell syntax and exact payload/helper/digest
consistency were checked. Production has no fake-SIM bypass.

`first.json` is retained as failure evidence. API33 passed the earlier contracts
and publication handoff, then failed the fresh supervised trial; all three cleanup
stages completed. Its original error was intentionally unmapped and does not
prove an exact root cause. API36 failed the old requirement that its installation
already be permission-ready; no outer snapshot or permission seed was reached,
so attempted outer cleanup also reported failure. The subsequent source accepts
the actual original permission state, saves it before seeding missing grants,
still requires preparation ready/PREPARED, and checks complete original cleanup.
Restored owners are checked without reapplying an earlier prepared permission
baseline. Fixed error reasons and source class/method/line locations replace
arbitrary exception-message exports.

Tested helper SHA256:
`323bebc8d81a39f80c6aa68e5f475fbab7a2431c0e007369100aae582542549d`

Tested signed module SHA256:
`dfd4440dcb12628e7615e63333db8e2c4bd7ae7f15f3687f8ceb8ecf6f9556a3`

The saved fixture directories and immutable helper generations remain isolated
under nonce paths. No claim of complete filesystem cleanup is made. Initial
publication interrupted before its `current` pointer may leave a hook that cannot
start yet, but publication has not returned and permission preparation has not
started; retry publication completes the entry. Existing committed generations
remain usable across an uncommitted staging handoff.

False / unverified: real modern Magisk mount and service.d lifecycle, actual
reboot/forced kill/power-loss durability, real VOXI EAP/tunnel/registration/call/SMS,
legacy-mode property/cache refresh, two active SIMs, and runtime coverage of every
SDK31–37 release. Modern app selectors remain disabled. The working API30 phone
and all three retained signed service APKs were not changed by this batch.
