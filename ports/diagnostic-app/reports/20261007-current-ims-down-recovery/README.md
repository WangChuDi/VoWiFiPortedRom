# Actual IMS DOWN recovery and native business verification

The existing Android11 raphael full replacement was observed disconnected:
[three read-only samples](before-read-only.json) showed IMS unregistered and native
SMS unavailable while full Phone, IMS and system_server process identities remained
stable. The installed tool was exact 0.9.11. No repair or traffic was requested.

An additional [strict path check](payload-path-assumption-failed.json) found matching
IWLAN bytes but rejected its differing installed path. That check's path assumption
was too narrow for this device. A [fresh calibrated payload check](payload-privilege-verified.json)
then confirmed all three installed APK byte digests and all three module-mounted
APK byte digests match the exact 0.9.4 archive. All installed packages report
SYSTEM, PRIVILEGED and UPDATED_SYSTEM_APP flags; their current paths differ from
the module mount paths. The module metadata also matches the archive. The original
failed check is preserved; the new result proves bytes and privilege rather than
pretending its strict path assertion passed. No APK was installed during these checks.

The [link boundary](link-boundary.json) showed physical Wi-Fi and successful ePDG
DNS, QNS IWLAN_SELECTED, authenticated IKE/child open with transforms, an attributed
IMS interface and two P-CSCF servers. Full component ownership remained ACTIVE,
mask 7 and persistent ENABLED. IMS itself was DOWN; its previous SIP 200 response
and REGISTER counter 11 were historical and did not mean it was currently registered.

One existing permission-protected, per-slot periodic refresh broadcast was sent
after exact tool/IMS hash, current owner and call-idle checks. The
[refresh observation](periodic-refresh-failed.json) remained unregistered throughout
the bounded window with no REGISTER counter increase. Broadcast completion does
not independently prove the receiver invoked its function, and no counter increase
does not prove no TCP attempt occurred. [Fixed historical error metadata](historical-fixed-errors.json)
contains SocketTimeoutException at `SipConnectionTcp:77`, whose socket connect
has a 12-second deadline. These are process-history observations without calibrated
timestamps; they do not prove the current attempt failed, UDP was blocked, or the
specific root cause of this disconnect.

The [existing idle reload](idle-reload-recovered.json) was then used after comparing
both control scripts against the exact 0.9.4 module archive and rechecking the owner
and idle state. It reloaded Phone, preserved the transaction and boot counter, and
independently observed REGISTERING followed by WLAN REGISTERED with voice, SMS,
native IMS SMS support and dispatcher available. APKs, module and APN were unchanged.
Same IWLAN generation values were observed before/after; that alone does not prove
continuity of the IKE process or session.

The [authorized real-business batch](authorized-business.json) then passed on exact
tool0.9.11/IMS0.4.5/module0.9.4: an explicit phone-account 191 call connected with
294 transmitted and 294 audio-playback frames, active call and SIP BYE200. One
native INFO SMS to 85075 returned RESULT_OK/network0, TX1/OK1, RX4/system ACK4,
no SMS failures, one new native inbox row and a new messages notification.
The same IMS PID/generation and Phone/owner were preserved during traffic.
Human audible speech remains unconfirmed. No account login or SMS-body export was
used. A registered-client rebind was exercised before traffic and completed without
Phone reload; it is distinct from restoring an unregistered IMS session.

The temporary short-code test policy was restored after traffic; this required
Phone reload for MIUI's original policy0. The [final read-only window](final-read-only.json)
then observed three ready WLAN/voice/SMS/native-dispatcher samples with full Phone,
IMS and system_server identity continuity and all calls idle. This short window
does not establish long-term automatic recovery.

[Summary](summary.json) explicitly keeps automatic DOWN recovery, modern real-carrier
coverage and real dual active SIM support unverified. [SHA256SUMS](SHA256SUMS)
covers all ten fixed JSON reports. No credentials, SIM identities, owner tokens,
raw logs, phone numbers other than authorized test short codes, or SMS bodies are
included. Production binaries are the preserved prior release; this evidence does
not represent a new IMS engine release.
