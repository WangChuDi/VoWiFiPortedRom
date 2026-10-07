# Shizuku installation, selection and restoration feasibility

## Result

The installed Shizuku13.6 manager was started using its installed native starter
under ADB UID2000 on the Android11 test phone. Its bundled public `rish` assets
were used as the bridge, identifying the client as `com.android.shell`.
No manager-private data or authorization database was edited.

The bridge successfully executed the installed tool's read-only
`ShellCapabilityProbe` under UID2000. CarrierConfig, WFC settings, provisioning
and an IMS registration callback were accessible. APN access failed with
SecurityException; existing Magisk module file access failed. Native IMS-SMS
support remained false. These are observations on a rooted phone that already
contains privileged replacement components, not proof of installation on an
unrooted phone.

Actual creation of a randomized, otherwise unused `/data/adb` directory failed
under the Shizuku ADB identity. The system partition write-access hint was false.
No attempt was made to write a system component or change a carrier override.

Shizuku/rish followed by the existing authorized `su -t 0` route obtained UID0,
matched the global mount namespace, found the Magisk CLI, and successfully
created, wrote, read and removed an isolated test file under `/data/adb`.
The existing module property file hash remained unchanged.
This establishes entry-path prerequisites, not end-to-end deployment or recovery.

| Operation | ADB Shizuku | Root Shizuku / Shizuku followed by su |
| --- | --- | --- |
| Read carrier/WFC/IMS diagnostics | Tested through rish; several APIs work | Existing root diagnostics remain usable |
| Install a Magisk module ZIP | Current workflow cannot access root module state | Feasible entry path; actual installation through Shizuku not tested |
| Deploy privileged IWLAN/QNS/IMS APKs | Cannot reproduce the current system-module deployment | Requires root filesystem/mount context; full deployment not tested |
| Select already installed components through CarrierConfig | MODIFY_PHONE_STATE is granted; override operation untested | Existing root transaction may be reused after namespace and owner checks |
| Restore a recorded component owner transaction | Cannot access the root-owned backups/leases | Feasible entry path; full restore through Shizuku not tested |

Root-started Shizuku itself was **not** tested. The verified root path was an
ADB-started server followed by `su`; it still depends on root and the Shell's
existing Magisk authorization. A future tool UserService must obtain its own
Shizuku authorization and verify the effective UID, SELinux access and mount
namespace. The current tool0.9.18 has **no Shizuku SDK backend**.

## Why selection is different from installation and restoration

`MainActivity.installModule` calls `magisk --install-module`; the API30 module
deploys APKs through a system module and subsequently checks installation and
permissions. Changing a package name in CarrierConfig alone does not make an
ordinary APK equivalent to these privileged components.

API30 `control.sh` / `subscription-control.sh` preserve per-owner snapshots,
persistent carrier XML and shared selection state under `/data/adb`. Restoration
uses these records, reloads the phone as needed, verifies providers and handles
the companion service. A no-root "restore default keys" operation is not a full
restoration of that transaction.

The modern `ModernProviderTransaction` calls `ICarrierConfigLoader.overrideConfig`
and separately preserves the original bundle and persistent XML.
`ModernInstallationTransaction` also validates privileged APK placement and
tracks permission/AppOps state. Its controllers require UID0. These guards must
not simply be removed to accept UID2000.

No test override was applied to the active subscription: an override may replace
an existing bundle, so even a supposedly harmless test key could alter providers.
Configuration-write feasibility remains unverified.

## Proposed application backend

Use an authorized Shizuku UserService for diagnostic and transaction entry points.
Expose operation-specific capabilities rather than treating "Shizuku connected"
as permission to install, replace and restore everything.

1. Probe actual server/UserService UID and each required operation. Keep ordinary
   application Context for network APIs where the shell Context was rejected.
2. In ADB mode, offer the successfully verified read-only operations. Keep full
   deployment/restoration unavailable unless a separately authorized root path
   is present; report unavailable states without inventing success.
3. In root mode, verify the mount namespace before reusing the existing root
   transaction engine. Preserve exact owner, idle, snapshot, lease, APK hash,
   persistence and restoration verification checks.
4. Treat any ADB-to-su escalation as a separate root authorization boundary.
   The tool's Magisk grant does not imply Shizuku permission, and the tested
   Shell route does not prove future tool UserService authorization.
5. Return structured status/errors over Binder, with timeout and server-death
   handling. Do not depend on the rish launcher's outer exit code.

The custom ECJ/D8/aapt2 build currently has no AAR dependency pipeline. SDK
integration additionally requires pinned official API/provider dependencies,
their transitive class inputs, manifest/provider integration and the explicit
application permission flow. No UI/backend integration is claimed here.

## Evidence and CLI pitfall

Accepted closed metadata is in
[reports/20261008-shizuku-control](reports/20261008-shizuku-control/README.md).
Shizuku's public APK/dex assets remain local test inputs and are not redistributed
in this repository. The on-device CLI assets are isolated under
`/data/local/tmp/codex_shizuku_probe_20261008`; the server remains running in ADB
mode. No modules, APK providers, CarrierConfig overrides or IMS services were
installed, switched, restored, reloaded or restarted by this experiment. No call
or SMS was initiated.

The installed rish loader returned outer exit0 even for a remote `false` command
and denied access checks. An initial permission report inferred success from
that status and is explicitly rejected. Accepted checks require an explicit
remote exit marker. One marked run did not receive its marker and was rejected;
a subsequent bounded run completed. These observations are CLI transport
limitations, not proof that an integrated SDK UserService has the same behavior.
The legacy read-only probe's `shizuku_transport_tested:false` field remains
unchanged: it does not embed the SDK; the enclosing experiment records that it
was launched through the installed rish bridge.

## Primary sources

- [Official Shizuku API guide](https://github.com/RikkaApps/Shizuku-API): root vs
  ADB identity, authorization, UserService and Context limitations.
- [Official Shizuku implementation](https://github.com/RikkaApps/Shizuku): Binder
  forwarding and system-specific ADB permissions.
- [Official startup guide](https://shizuku.rikka.app/guide/setup/): root and ADB
  startup modes; OEM-specific USB debugging restrictions.

The original phhusson/ims source attribution and GPL-2.0 notices are unchanged.
