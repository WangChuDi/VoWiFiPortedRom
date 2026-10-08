# Network detection and detailed APN display, tool0.9.19

## Behavior

The existing link check still reads configuration/status and resolves ePDG
candidates without sending IKE. A separate **网络检测** button explicitly sends
bounded IKE_SA_INIT probes to UDP500 and UDP4500 and then renders the other link
observations. Automated post-action checks use the ordinary, non-probing path.
Busy state disables both check buttons, SIM selection and mutation actions.
Neither path calls, sends SMS, reads SMS bodies, authenticates a SIM, or changes
APN, carrier overrides or installed components.

Selected-SIM carrier name, SIM operator name and home MCC/MNC are reported
separately from the currently reported network operator and roaming state.
A PLMN identifies the home network, not uniquely the MVNO retail brand. In
particular the observed Vodafone UK23415 is the network used by this VOXI card.
The registered network operator may be empty in airplane mode; an OEM network
name may remain reported even then.

## What the network result establishes

ePDG domains are operator-specific candidates. The standard home domain is
`epdg.epc.mnc<MNC-padded-to-3>.mcc<MCC>.pub.3gppnetwork.org`.
The diagnostic also considers visible static/roaming static and configured PLMN
CarrierConfig values. It limits candidates to3 and does not implement the entire
modem/AOSP ePDG selection algorithm: no PCO, location-specific, EHPLMN or
visited-country NAPTR discovery. A candidate is not proof of the endpoint the
current modem/service actually selected.

DNS and UDP sockets are explicitly bound to the same physical Wi-Fi Network.
They bypass a phone VPN, while still following that Wi-Fi's current gateway,
including any transparent router proxy. The code uses temporary source ports;
actual modem/IMS traffic can have different source-port, UID or routing policy.
Only the physical Wi-Fi path is covered by this feature.

DNS has a4-second shared deadline. Active tests sample up to4 addresses, at most2
per candidate with a preference for both IP families, and make one request per
address/port. Individual receive waits are2.2 seconds with a5.5-second shared
collection deadline; sockets are closed. The existing32-second diagnostic
watchdog returns partial state and disables new replacement actions on timeout.

Probes use a fresh random SPI, DH public value and nonce, and a valid SA_INIT
proposal. UDP4500 uses the four-zero non-ESP marker. No IKE_AUTH/EAP/identity
payload or Child SA is sent. A matching response must satisfy the source socket,
SPI, IKE header, message ID, lengths and payload checks. Valid COOKIE or error
notifications may have zero responder SPI (RFC7296 §2.6).

Results distinguish:

- `IKE_RESPONSE`: matched unauthenticated IKE response, normal or Notify/Cookie.
  This establishes an observed reply for that endpoint/port, not authentication,
  accepted SIM provisioning, an IPsec tunnel or usable VoWiFi.
- `NO_RESPONSE`: this attempt received no reply. It does not prove a blocked port;
  packet loss, policy, proposal compatibility, rate limiting or session state
  can matter. Independent4500 testing does not validate an entire NAT-T exchange.
- `UNMATCHED_RESPONSE`: data arrived but did not match a valid response to the
  request. It is not shown as successful IKE reachability.
- `UNAVAILABLE`: the operation or its observation could not complete, with only
  an exception class exposed.

IKE_SA_INIT responses have not authenticated the responder's identity. DNS/UDP
results and actual selected-SIM IWLAN/IMS observations therefore remain separate.

## APN fields and scope

The user explicitly requested detailed APN settings, including credentials.
The tool now shows name, APN, username, password, MMSC, MMS proxy/port, MCC, MNC,
type, protocol, roaming protocol, authentication type and enabled state.
It reports the preferred internet record and up to4 database records whose type
includes `ims` or `*`, filtered by selected subscription and home PLMN. An IMS
database record is **not** proof of the modem's currently loaded APN.

Missing columns/results remain unavailable; known null/empty values are shown
as unset. CLI fallback uses an explicit projection, retains commas in type and
field values, and rejects duplicate/missing field delimiters. Ownership is
rechecked before and after reads. No subscriber identity fields are queried.
The current14-column scope is the requested/common settings, not every column
in the carriers provider.

Password is masked initially, with an explicit reveal/hide button; SIM changes,
new checks and errors discard the previous display. Credentials are carried
only in the private tool diagnostic response and are not written to the host
evidence reports. The app provides no diagnostic export feature. Screenshots
used for acceptance must remask the password first.

## Clarified diagnostic/action text

- Process observation compares the phone and system-server identities around
  a check to catch restarts/stale observations. Stable identities do not prove
  that Binder, IMS or the operator is working.
- Runtime preflight executes23 fixed cross-version core signature lookups in
  the root app_process loader. The visible count is neither the complete API
  set nor23 interfaces selected specifically for Android11. The SDK separately
  chooses the API30 or experimental modern engine; service shared-library
  visibility and actual invocation/binding remain separate concerns.
- IMS carries voice and SMS over IP. The SMS observations separately describe
  Android's SMS service/client/dispatcher and actual transport counters; the
  SMS application subsequently manages the inbox and notifications. Voice
  registration does not establish SMS delivery or notification behavior.
- The current replacement workflow requires an installed/enabled Magisk module
  and root. First deployment needs a reboot. Later provider selection/trials
  can run while the system is active and may reload the phone service; they
  are not a module-free deployment using root alone.

## Validation

The consolidated tool build passed compiled manifest, signature, exact embedded
engine and existing production contracts. New host tests run the actual packet
codec/APN parser with malformed lengths, source/SPI/message mismatches,4500
markers, zero-SPI notifications, incomplete CLI fields and password masking.
These are host contracts, not a carrier interoperability test.

One actual device batch installed only tool0.9.19 and retained the exact0.9.18
APK. Three replacement APK hashes, module properties and phone/IMS process
identities remained unchanged. The observed internet APN was Talkmobile PAYG /
`payg.talkmobile.co.uk` / `default,mms,supl`; one Vodafone UK `ims` record was
returned. All14 preferred fields were available.

The Vodafone home domain resolved to3 IPv4 addresses. The bounded sample tested
two addresses on both ports and received no matching responses. Independently,
the existing IWLAN Child SA was open and IMS remained WLAN registered. This
contrasting observation is retained and explicitly prevents interpreting the
probe timeout as a failed current VoWiFi network. Native SMS support remained
false; this UI/network feature does not repair that existing issue.

Closed reports are under
[reports/20261008-network-detection](reports/20261008-network-detection/README.md).
UI acceptance status is tracked separately from the successful protocol/device
metadata tests. Root/Shizuku capabilities are unchanged; the SDK remains absent.
Android17 runtime work remains deferred.

## Sources

- [3GPP TS23.003 §19.4.2.9, via ETSI](https://www.etsi.org/deliver/etsi_ts/123000_123099/123003/16.06.00_60/ts_123003v160600p.pdf)
- [AOSP EpdgSelector](https://android.googlesource.com/platform/packages/services/Iwlan/+/327fe36/src/com/google/android/iwlan/epdg/EpdgSelector.java)
- [Official IWLAN CarrierConfig keys](https://developer.android.com/reference/android/telephony/CarrierConfigManager.Iwlan)
- [RFC7296 IKEv2, especially §§2.6 and2.23](https://www.rfc-editor.org/rfc/rfc7296.html)

Original phhusson/ims attribution and GPL-2.0 notices are preserved. The new
probe implementation only implements the standard packet format; it does not
import Shizuku manager APK assets or change the replacement services.
