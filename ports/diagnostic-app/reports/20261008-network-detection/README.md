# Network detection evidence, 2026-10-08

Tool0.9.19 build/host contracts and actual-device metadata are recorded separately.
`COMPLETED` for a probe means the bounded test finished, not that VoWiFi works.
The sampled UDP ports had no matching responses even while the independently
observed existing IWLAN/IMS session was registered.

No APN username/password, SMS body, OTP, subscriber identity or raw phone log is
included. APN credential presence is recorded only as booleans. APN values other
than credentials are the fields the user explicitly requested to inspect.
No engine update/reboot/provider mutation/call/SMS was performed.

The source build snapshot is immutable and reuses the existing0917 embedded
engine bytes; those newer engines were not installed. Test/docs additions after
that snapshot do not change the compiled production tool's inputs.

See [the implementation and result boundaries](../../NETWORK-DETECTION-20261008.md).
