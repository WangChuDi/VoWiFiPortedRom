# Recorded API37 fixture investigation and recovery

This isolated GPL-2.0 probe operates only on the original root-owned
`CodexVoWiFiApi37` emulator and its existing test journals. It is separate from
the production carrier selector and the signed IWLAN/QNS/IMS engines. It never
creates a carrier or installation baseline, migrates a journal, wipes an AVD,
authenticates a SIM, calls, or sends/reads SMS. Installation/recovery implementation
and attribution remain in the [controller](../../controller/README.md) and the
[phhusson source/license notice](../../../android11/THIRD_PARTY.md).

The runtime helper is pinned to
`ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d`,
from `modern-api32-api37-runtime-partial-20261007`. All three old installed
privileged APK paths and hashes are checked before invoking the probe. Do not
rebuild `build-runtime.py` merely to inspect the saved fixture: that replaces
the current helper outputs. A different helper/journal generation requires a
separate compatibility audit rather than changing this hash to bypass the guard.

The probe was compiled against headers with SHA-256
`df715b19bbecf835337294391f87976e2d25c75d59f05590f5b79f68a00740da`.
Its referenced constructor/restore/status signatures were checked against the
retained tag. Both the earlier probe and the current calibrated execution reached
the original selection restore method and failed at `ModernRootSettings.command:19`.
Installation and outer recovery remain unverified. The earlier probe digest
`2cb0989abaeea05dbb78cef3279596627061f3081b85e23407419ce4d7a1f913`
belongs to source retained in commit `4c49d8c`, not the current source.
The current independently pinned Java source digest is
`5ae0d75ae69ce182ffed33b4d5f289d29dc67eb98163ea54a8b62504ded0f833`;
its built and executed probe digest is
`9439d1f4c95c1d6109e0665a812b7ea3412c28c0aa10df62abd5173ac10afae6`.
No signed APK/module/helper was rebuilt for this investigation.
The runner pins both tested source and probe digests independently of the local
build manifest. Editing the manifest and payload together cannot select a new probe.
Before checking a privileged path, the runner now requires a successful package
inventory containing known Phone and Settings packages. Command failure, missing
retained package, malformed path output and a different valid path have separate
failure reasons. No raw command output or unexpected path is exported.

With the existing project toolchain and `JAVA_HOME` configured:

```sh
python build-probe.py --framework /path/to/android-all-12-robolectric-7732740.jar \
  --headers /path/to/runtime/classes.jar --toolchain /path/to/toolchain \
  --output /path/to/fresh-probe-build
python run-probe.py --adb /path/to/adb --probe-dir /path/to/fresh-probe-build \
  --output /path/to/fresh-inventory.json
python test-runner.py --probe-dir /path/to/fresh-probe-build
```

The default `inventory` action reads policy/journal metadata only; the runner
also stages its hash-checked temporary DEX. It reports fixed phases, schema
booleans, SIM-state enum and ordinal counts. Private directory names, subscription
IDs, owner tokens, APK UIDs and original setting values stay in the guest.
Two consecutive observations must identify the same ready slot-0 fake subscription
with a numeric non-VOXI operator before recovery. The bounded readiness observation
does not establish a fixed production delay or authorize changing an unknown owner.

Explicit actions use an ordinal from the current inventory. Inspect again if the
inventory changes. `selection` uses that record's original token and restores
carrier/lease/shared mode; `installation` requires the owner to be RESTORED with
`mode_owned=false`; `outer` requires installation RESTORED and invokes the old
fixture's full outer-policy restoration. `audit` invokes its existing policy audit.
These two actions run the retained entry in a separate child process: that entry
prepares its own main Looper, so invoking it in the already-initialized parent
would attempt to prepare the main Looper twice. This source defect is separate
from the observed Settings failure in selection recovery. The child has a
25-second deadline, a 32-KiB output limit, confirmed reader completion and exactly
one matching successful result. Excess or incomplete output fails closed.
Cleanup must confirm the complete original outer observation; audit must report
no fixed policy changes. Neither action has yet been reached in the actual run.
Each layer has a fresh report and must succeed before the next layer. No failed
mutation assertion is converted to success or silently retried. This investigation
did **not** pass those layers; do not create a new fixture over the pending original.

The runner records host duration, exit status and `exit_indicates_sigkill`. Exit
137 alone does not prove a timeout: the earlier uncaught READY-guard failure also
ended that way before its 40-second bound. A host observation timeout leaves guest
termination and restoration unverified; inspect the same running handle/state.
The host runner does not reboot, restart, disable a package, or change SELinux.
The guest probe terminates only its own outer/audit child on that child's deadline;
it does not terminate Phone, system_server or the emulator. The separate host
resource monitor manages only its identity-verified emulator process.

See [the actual recovery/control evidence](../reports/20261007-api37-recovery/README.md).
See also [calibrated package observations](../reports/20261007-api37-calibrated-recovery/README.md).
Full API37 lifecycle, production Magisk mounting, carrier traffic, app-own-UID
controls and real dual active SIMs remain unverified.
