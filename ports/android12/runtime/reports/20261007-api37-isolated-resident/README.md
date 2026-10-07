# API37 full fixture and isolated resident lifecycle

The original `CodexVoWiFiApi37` userdata passed all 47 stages of the complete
current lifecycle fixture plus resident execution. This run used one device
worker and the explicit `resident` helper slot. It is separate from the prior
50-stage full fixture plus PREPARING interruption batch, whose records remain
unchanged. No userdata wipe, snapshot load/save, legacy journal migration or
replacement original baseline was requested.

Changes: a dedicated fixed resident helper path and a shared Java path policy
allow the role broker and resident supervisor to run the same newly built helper
without replacing either earlier helper. Canonical path, published generation,
digest, named-root-QEMU and owner checks remain enforced. Production resident
entry still requires its exact published recovery helper. The default runtime
slot retains its historical push behavior; use `--helper-slot resident` explicitly
to preserve earlier helpers. The audit/resident CLI combination still refuses.

- `lifecycle.json` records the entire fixture, including real background renewal
  beyond the initial 90-second lease using the production 15-second loop; a
  duplicate waits and refuses without taking ownership; an identity-bound SIGKILL
  terminates the actual resident; the lock releases; a new process resumes the
  existing retained owner; disabling the fixture restores owner and installation
  policy before normal exit. All tracked residents were terminal before cleanup.
  Four exact read-only busy-lock retries are retained in the report.
- `driver.json` verifies all 58 pre-existing properties records stayed byte
  identical, both earlier helper hashes stayed identical, and all three installed
  APKs stayed identical. The new cleaned fixture added 44 records, for 102 total;
  these are retained diagnostic journals. Phone PID **and Linux start time** stayed
  unchanged. Temporary three-button navigation was restored to the original state.
- `monitor-summary.json` records the swangle/2GiB monitored window, explicit finish
  and all owned emulator processes terminal. The last resource sample was at 340
  seconds; the 1500-second configured maximum is not an observed stability duration.
- `module-build.json` pins the signed retained APKs, complete verified module
  payload, helper `163a1d3a6a8f4d9982bb65b66bab75d939058639e9e0953eb3168092902f425b`
  and test module `e29814c04b6d1766644387e243324d1d3de137eeec7fcee2c9da328eb80afbd1`.
  `helper-build.json` pins every compiled Java input. No default output was replaced.
- `host-artifact-contracts.json` records 27 passing host tests with mocked ADB.
  `path-contracts.json` records 33 checks against the actual production Java policy
  and host runner path inventory, with no Android stubs. These host checks are
  distinct from the actual emulator lifecycle evidence.
- `phone-readiness.json` records three consecutive read-only samples on the
  Android11 phone: IWLAN CHILD_OPENED, QNS IWLAN_SELECTED, IMS WLAN REGISTERED,
  native SMS dispatcher ready, service-own-UID native query TRUE/HEALTHY. Owner and
  Phone identity were unchanged, calls idle, automatic requests zero. This batch
  changed no physical-phone package/configuration and made no call or SMS request.

The independent static review found the unchanged legacy runtime-slot overwrite
behavior; the explicit isolated slot used here and the post-run comparisons
address that concern for this batch. It did not perform the emulator test itself.

This is a test helper/module and source change, not a new production APK release.
The evidence does not prove modern carrier registration/voice/SMS, actual Magisk
mount/OS reboot, positive modern application-own-UID controls, simultaneous real
dual active SIMs, Android11 automatic native-SMS rebind, human audible speech or
long-term stability. The full requested goal remains incomplete.
