# API37 isolated full lifecycle and actual PREPARING interruption

The same original `CodexVoWiFiApi37` AVD passed all 50 stages of the current
full fake-SIM fixture plus PREPARING interruption checks, after its prior fixture
was recovered and audited. The earlier failed run remains archived as failed.

This run used one device worker. No userdata wipe, snapshot load/save, system APK
replacement, new original baseline or legacy journal migration was requested.
It retained the three original signed APKs and used current compatible helper
`5c5748cdd75377a34a05001c22bb3d5e7a9375de060a265b0c3bbb51202b44c4`.
The isolated module was
`a9aae03e5b6b45477b20c0a5f2de7b880cc9080cc7451bc6b91af63225e1a4ff`.

- `lifecycle.json` records every full fixture stage and two real PREPARING
  process interruptions, at `bundle-staged` and `snapshot-complete`, before
  carrier mutation. PID/start-time ownership checks, SIGKILL and actual child
  termination precede new-process carrier/lease/role restoration and repeat recovery.
- Original-bundle removal after the complete checkpoint was refused without
  resampling; the exact saved original was preserved. Preparing children were
  terminal before preparing/roles/supervisor/selection/outer cleanup.
- `driver.json` verifies all 11 pre-existing properties-file hashes stayed
  identical and original runtime helper `ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d`
  stayed identical. The new nonce fixture adds records: navigation apply counted
  11 records, restore counted 58. No old record was replaced by a new baseline.
- Temporary three-button navigation was restored to the original gestural
  navigation. The swangle/2GiB resource-monitored window ended by explicit finish
  after approximately 433 seconds, with all owned emulator processes terminal;
  1500 seconds was its maximum window, not an observed stability duration.
- The runner checked unchanged Phone PID. It does not independently compare
  Phone start time, so this is narrower than the physical-phone identity checks.

`host-contracts.json` records 19 host contracts, including actual runner branches
for matching/different/missing/unconfirmed audit helper presence with mocked ADB.
`source-pins.json` pins the three changed source files; `module-build.json` pins
the module/helper/APKs and exact module payload verification. APK signature checks
were performed by the actual packaging invocation.

This evidence does not establish API37 resident-loop renewal/disable recovery,
real Magisk mounting, real USIM authentication, carrier registration/voice/SMS,
modern app-own-UID positive controls or simultaneous real dual SIMs. It is not a
new production APK/module release. Android11 phone findings from the same turn
are separately recorded in the diagnostic-app report directory.
