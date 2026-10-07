# Unregistered IMS versus registered SMS-client recovery

The actual Android11 full replacement was found in IMS DOWN with a healthy observed
IWLAN child and selected QNS route. The existing periodic refresh request did not
restore registration; the existing same-owner idle Phone reload did. Actual 191
voice and native INFO SMS/inbox/notification then passed, and final read-only
samples were ready after restoring the temporary test policy.

This differs from the [registered-client failure](IMS-CLIENT-REBIND-20261007.md).
Client rebind requires an already registered, initialized, current IMS feature;
it cannot recover a DOWN transport. On this API30 device, the tool's existing
“重新拉起 · 空闲时重载电话服务” action is the verified manual recovery for this observed
DOWN state. It reloads Phone and requires an idle, selected owner; it is not a
whole-device reboot and does not change the APN or replacement payloads.

See [the fixed real-device reports](reports/20261007-current-ims-down-recovery/README.md).
Automatic reconnect still needs work. Existing source has retry backoff and network
callbacks, but the exact cause of this session remaining DOWN is not established.
Historical TCP-connect timeout frames and a previous SIP200 are not proof of the
current network or registration state. A future fix must capture fresh transport
attempt provenance and verify a real DOWN-to-registered transition before claiming
automatic recovery. Android12–17 real-carrier and dual-active-SIM validation remain
separate requirements.
