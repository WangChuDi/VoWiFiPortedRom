# Actual PREPARING snapshot interruption, API33/API36

`second.json` passed **49 stages on each named root guest concurrently**: the
40-stage existing installation/selection/shared-role/direct-supervisor regression,
eight new preparation stages, and the preparation cleanup boundary. It does not
repeat the six-stage long-running resident lifecycle from the prior 46-stage batch.

The same production helper performs both snapshot boundaries. A separately guarded
test observer pauses after a complete original bundle is synced to its private
temporary file, or after that file is atomically committed but before PREPARED.
The fixture records root PID/build/boot/start ticks privately. Device-side SIGKILL
requires that record, current Linux start time, exact nonce cmdline and actual core
and owner PREPARING phases. The host never supplies a PID. It waits for the actual
child to exit; a new process also verifies the saved process is dead before recovery.

The ordinary production owner restore accepts only a complete saved original,
matching build/persistence identity and unchanged full live configuration plus
an empty transient layer. It validates the saved file's original bytes/presence.
Recovery of the staged bundle commits those saved bytes before proceeding; it
does not recreate a baseline from live configuration. Missing/null/malformed or
conflicting saved originals remain refused. In the committed-snapshot case the
fixture additionally withholds the saved original and confirms refusal while the
carrier remains PREPARING; it then puts back the exact same private bytes and
retries successfully. That withholding is a synthetic fault after the actual kill.

Both actual kill/recovery cases confirm full original carrier/lease/role state
and repeat recovery. Every tracked preparing process is terminal before policy
cleanup. Preparation → roles → supervisor → selection → outer permission cleanup
stops at the first failing boundary. Final cleanup and unchanged phone PID passed.

## Failure history

`first.json` passed the existing regression and the staged-file SIGKILL/recovery
on both guests, then failed during the second-process readiness observation.
The old process identity was still on disk; its `/proc` entry had disappeared, so
the probe returned ErrnoException before the successor checkpoint replaced it.
The host failure path correctly deferred outer policy cleanup but left the two
successor fixture processes live. Follow-up device commands verified each current
journal/cmdline/start time, killed those exact processes, observed zero matching
processes, and completed all five cleanup boundaries. `first-cleanup.json` is a
separate manual follow-up observation, not a rewrite of the failed first report.

Readiness now reports false for an absent identity or departed process instead of
mistaking the previous identity for a new-process failure. The host waits on its
same live child within a bounded window. Failure cleanup also waits for that exact
fixture checkpoint before using guarded kill, and refuses outer cleanup if any
tracked child remains live. `second.json` uses this corrected fixture and runner.

## Identities and scope

49-stage helper: `b5cdd951f8fad439b78c0a5883350063fea57fd7e5562c0dde47e123802cb40f`.
49-stage module: `eaf914040506725a5ef3d375cf45ffc5924219a0ab4c26d70cdd0f3a61b89c6f`.
Final helper: `5eb7925a5399ee54fa57f5c956a2820a0d4735ef6f77a2d001ae1a4ce52938a8`.
Final module: `ace364cc8727c5f386cea57a40de67c57b20be21c8e2ae22ad18eec8aeec3eaf`.
First helper: `3bd51e468f27b0431677de50bace395329dbc5c3469b21afd4e3f33278d3ce2f`.
First module: `97ccc11f7ba74f3164b748c5830601cc29de32c317a060f95f9fa9b97afb6e52`.

Independent source review then identified the same exit window through Java NIO:
the process can disappear after `Os.stat` but before `/proc/PID/stat` or cmdline is
read. Readiness now also returns false for NoSuchFileException on exactly those
two expected process files; other file failures remain errors. Cleanup explicitly
matches each tracked child's expected checkpoint and checks both kill proof fields.
Only fixture/host source changed after the 49-stage batch; production restoration
source is unchanged. `third.json` passed the targeted **18-stage** batch on each
guest with the final helper/module above. It exercises both actual SIGKILL/recovery
cases and cleanup again, not the entire 49-stage suite or a deterministically
forced NIO scheduling race. The final tool0.9.1 embeds this final module.

The guests have one non-VOXI fake SIM. No production module mount, USIM/carrier
authentication, modern voice/SMS, OS reboot or dual active SIM was tested. These
two boundaries do not cover every earlier incomplete snapshot write or every
APPLYING/restoration process-kill point. Removed or changed subscription recovery
still refuses at the live-owner checks and needs a separate identity-safe design.
The existing API30 phone engine was not changed; diagnostic tool0.9.1 includes
this newer modern helper in its separate modern asset.

```sh
python ports/android12/build-runtime.py
python ports/android12/package-module.py --signed-dir /path/to/retained-signed-apks
python ports/android12/runtime/check-installation-emulator.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --module ports/android12/out/modern-services-installation-stage.zip --preparing \
  --output /path/to/fresh-preparation-report.json
```

`--preparing-only` runs five prerequisites, eight new stages and five cleanup
boundaries (18 total); it is not the full regression. It cannot be combined with
the resident lifecycle option. New sources retain GPL-2.0 and the original
[phhusson/ims attribution/license](../../../../android11/THIRD_PARTY.md).
