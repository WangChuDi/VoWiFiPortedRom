# Shared original role-policy resources, API33/API36

The final `fourth.json` passed **46 stages on each guest concurrently** with the
same helper and module bytes. This includes the former 39-stage installation,
selection, supervision and real resident-process suite plus seven shared-role
stages. The runner uses named root/QEMU guests with one ready non-VOXI fake SIM.
It neither mounts a production Magisk module nor changes the working API30 stack.

## Report history

| Report | Result | Evidence and correction |
|---|---|---|
| `first.json` | API33 passed; API36 failed | API36 `roles-trial` refused `prepared-installation-required` after the prior QNS-only cycle. The audit recorded PREPARED but not-ready permissions; it does not identify the exact late callback/cause. |
| `second.json` | Both failed after shared-role stages passed | A new explicit installation cycle restored and archived the old baseline before preparing again. API36 reported the previous profile not ready. Both later failed the old supervisor fixture's assumption that history contains exactly one record. |
| `third.json` | Both passed, 46 stages each | The history check now preserves every existing filename/digest and verifies exactly one new archive containing the restored baseline. |
| `fourth.json` | Both passed, 46 stages each | Same final helper, repackaged module with bounded monotonic supervisor-readiness waiting; this is the final module bundled into diagnostic tool0.9.0. |

The explicit new installation cycle does not bypass a failed permission check or
overwrite its recovery record. Production restore verifies the original seeded
policy and no pending owner before archival/preparation. Native unused-service
revocation is a plausible explanation for a prepared profile becoming stale; no
network or precise native-writer cause is established by these reports.

## Shared-role checks and limits

The first actual slot0 owner selects mask7 through the production transaction.
The second participant is **only a synthetic PREPARING owner journal**, with a
fixed impossible-test subscription and no carrier transaction. It selects no
provider, acquires no second SIM lease and is never claimed to be ACTIVE.

The checks prove, on these guests:

* Both participants reference the same original IWLAN/IMS role records and
  baseline UID/APK/grant/flags, rather than resampling already-selected policy.
* The first owner's own carrier is restored while shared role resources remain
  owned by the pending participant; installation restore refuses that pending owner.
* External IPsec MODE_IGNORED is preserved when release refuses it. The resource
  remains RESTORING and its owner remains unreleased. This does not assert that
  no other already-owned permission was changed before that refusal.
* Reopening release from RESTORING restores the final resource originals and
  removes the synthetic participant only after both groups are confirmed restored.
* Existing installation archives remain byte-identical across a fresh cycle.
* Finally waits for every tracked resident handle to terminate, then follows
  roles → supervisor → selection → outer policy cleanup. A failed boundary stops
  further cleanup and preserves unresolved recovery evidence.

The resident portion still waits beyond the real 90-second lease, verifies
background renewal, refuses a duplicate worker, performs identity/start-time-
checked SIGKILL, resumes a retained owner in a successor, then disables and
verifies original owner/installation restoration before exit. This is a real
supervisor-process kill, not an OS reboot or a kill at every carrier phase.

`dual_active_sim_verified`, `carrier_call_sms_verified`, `magisk_mount_verified`
and `os_reboot_verified` remain false. Actual selected ACTIVE-peer policy repair
requires two real active subscriptions and is not exercised by the synthetic peer.
No modem/USIM/VoWiFi carrier authentication, native call/SMS, Magisk mount/boot hooks,
legacy phone-cache reset or complete SDK31–37 runtime coverage is established.

## Final artifact identities

* Helper: `c2734741ec8f7bd88d7a1e95d0d5872142bba510d4e472d29da2bbd51b8e163a`
* Final module: `4d675ae57d4cba462e30f2c4d5c655f23848ad7621662d13bbd40c9dfa7ce279`
* Third-report module, before deadline change: `526b1398bf659139236146512a1fa9a9a4bebea0a278a1985c1167312df7ede7`

```sh
python ports/android12/runtime/check-installation-emulator.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --module ports/android12/out/modern-services-installation-stage.zip --resident \
  --output /path/to/fresh-shared-role-report.json
python ports/android12/module/test-supervisor-deadline.py --adb /path/to/adb \
  --guest 33:emulator-5574 --guest 36:emulator-5580 \
  --output /path/to/fresh-deadline-report.json
```

The separate `supervisor-deadline.json` runs the actual shell readiness body with
controlled clock/probe/daemon boundaries. Slow failed probes stop at the overall
deadline, an already-ready resident returns immediately, and an unavailable clock
refuses before probing. Daemon launch is mocked and its redirection goes to
`/dev/null`; no daemon or production module is started by that shell test.

New sources retain GPL-2.0; this resource coordinator does not replace or remove
the [phhusson/ims attribution and license](../../../../android11/THIRD_PARTY.md).
