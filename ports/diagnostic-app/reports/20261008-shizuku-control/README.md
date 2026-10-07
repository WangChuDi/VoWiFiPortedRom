# Shizuku control-path experiment, 2026-10-08

These reports cover actual installed Shizuku/rish execution on the existing
rooted Android11 phone. They do not validate installation on a clean non-rooted
phone, root-started Shizuku, an application SDK/UserService integration, live
CarrierConfig writes, module installation or transaction restoration.

- `bridge-probe.json`: installed manager/starter, actual ADB bridge identity.
- `rish-permissions.json`: accepted explicit-remote-status filesystem and root
  entry-path checks; isolated test file was removed.
- `rish-capabilities.json`: existing read-only capability probe launched through
  rish; its legacy no-SDK transport field is unchanged.
- `rejected-runs.json`: the initial misleading outer-status interpretation and
  one missing-marker run are retained as rejected evidence.

No SMS content, OTP, subscriber identity, APN content, private app data or raw
device log is included. IMS/provider/module state was not changed. The Shizuku
ADB server and isolated public CLI assets were left available for future tests.

See [the scope and implementation proposal](../../SHIZUKU-CONTROL-20261008.md).
