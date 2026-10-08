# Carrier references and interface catalogue — tool0.9.20

This is diagnostic/reference coverage, not a claim of replacement-engine carrier
compatibility. Actual SIM home PLMN, subscription carrier name, SIM operator name,
CarrierConfig candidates and APN records can be read for any card when exposed by
the ROM and permissions. Shared PLMN cannot uniquely identify an MVNO or product.
The replacement engine's VOXI23415 guard is unchanged. No APN, policy, provider,
phone service or engine is changed by browsing the catalogue.

## Official public references checked2026-10-08

| Reference | Verified fields and scope | Unknown / boundary |
|---|---|---|
| giffgaff Internet | APN giffgaff.com; user gg; password p; proxy blank; MCC234/MNC10; IPv4v6, roaming IPv4; PAP | Name/type not listed; no IMS APN statement |
| giffgaff MMS | APN giffgaff.com; credentials blank; Server/MMSC http://mmsc.mediamessaging.co.uk:8002; proxy/MMS proxy/port/auth blank;234/10; type mms | Separate from Internet; not SMS over IMS |
| CTExcel UK | Official EE collaboration | APN fields and VoWiFi policy not verified |
| CTExcel US | US business stated by official company introduction | US local APN fields and VoWiFi policy not verified |
| CTExcel Canada local | Official Canadian service/FAQ | Local-plan APN fields and VoWiFi policy not verified |
| CTExcel France | Official French business | APN fields and VoWiFi policy not verified |
| CTExcel Hong Kong specified monthly product | Name/APN CTExcel, official planId1609 | Other fields and other products not verified |
| CTExcel Italy | Official Italian business | APN fields and VoWiFi policy not verified |
| CTExcel mainland/Hong Kong travel card | Name/APN CTExcel in Canadian FAQ travel-card section | Does not establish Canadian local-plan settings |

Primary sources:

- [giffgaff Internet](https://help.giffgaff.com/en/articles/245215-internet-apn-settings-guide)
- [giffgaff MMS](https://help.giffgaff.com/en/articles/245265-mms-apn-settings-guide)
- [giffgaff WiFi Calling](https://help.giffgaff.com/en/articles/258841-understanding-wifi-calling-and-volte)
  says WiFi Calling is not supported abroad; compatible firmware, enabled switch,
  completed migration and UK account/location conditions still matter.
- [CTExcel global regions and EE collaboration](https://www.ctexcel.com/global/globalBusiness-en.html)
- [CTExcel US/Canada business introduction](https://www.ctexcel.ca/aboutus.jspx)
- [CTExcel Hong Kong specified monthly product](https://www.ctexcel.com.hk/planDetail?planId=1609&plantype=23)
- [CTExcel Canada FAQ, 陆港畅游卡 section](https://www.ctexcel.ca/faq.jspx)
- [CTExcel Italy announcement](https://www.ctexcel.com/global/globalArticle-italy-publish-en.html)

Unknown means not verified from consulted official sources, not unsupported or
blank. No CTExcel MCC/MNC is guessed from a host network, roaming location or brand.
Only giffgaff name plus23410 suggests its Internet reference. CTExcel entries need
manual product/region selection; selection has no effect on network targets.
These non-VOXI cards have not been physically tested here.

##23 fixed signatures, displayed individually

The probe runs in root app_process. It performs lookup only, not initialization,
API invocation, binding, permission tests or carrier registration. Its list is
fixed across versions. Missing here can mean absent from this loader even when
the IKE shared library is available to a service.

| # | Group | Class / member | Purpose |
|---|---|---|---|
|1| EAP | EapSessionConfig.Builder() | EAP configuration |
|2| EAP | Builder.setEapAkaConfig(int,int) | AKA subscription/app |
|3| EAP | Builder.setEapIdentity(byte[]) | EAP identity |
|4| IKE params | IkeSessionParams.Builder(Context) / Builder() | Parameter builder |
|5| IKE params | setServerHostname(String) | ePDG hostname |
|6| IKE params | setNetwork(Network) | Underlying network |
|7| IKE params | setLocalIdentification(IkeIdentification) | Local identity |
|8| IKE params | setRemoteIdentification(IkeIdentification) | Remote identity |
|9| IKE params | setAuthEap(X509Certificate,EapSessionConfig) | EAP authentication |
|10| IKE params | addIkeSaProposal(IkeSaProposal) / addSaProposal(IkeSaProposal) | Crypto proposals |
|11| IKE params | addIkeOption(int) | IKE options |
|12| IKE params | setRetransmissionTimeoutsMillis(int[]) | Retransmission |
|13| Lifecycle | IkeSession(Context,IkeSessionParams,ChildSessionParams,Executor,IkeSessionCallback,ChildSessionCallback) | Session creation |
|14| Lifecycle | IkeSession.close() | Graceful close |
|15| Lifecycle | IkeSession.kill() | Forced termination |
|16| Negotiated config | IkeSessionConfiguration.getPcscfServers() | IMS proxy addresses |
|17| Negotiated config | ChildSessionConfiguration.getInternalAddresses() | Inner addresses |
|18| Negotiated config | ChildSessionConfiguration.getInternalDnsServers() | Inner DNS |
|19| IPsec | IpSecManager.createIpSecTunnelInterface(InetAddress,InetAddress,Network) | Tunnel interface |
|20| IPsec | IpSecManager.applyTunnelModeTransform(IpSecTunnelInterface,int,IpSecTransform) | Tunnel transforms |
|21| CarrierConfig | ICarrierConfigLoader.Stub.asInterface(IBinder) | Config Binder |
|22| CarrierConfig | getConfigForSubId(int,String) / getConfigForSubIdWithFeature(int,String,String) / getConfigForSubId(int,String,String) | Config read |
|23| CarrierConfig | overrideConfig(int,PersistableBundle,boolean) | Config override |

Production signature fallback covers only4,10,22 and only missing methods allow
fallback. Invocation errors propagate. SDK/device/profile choose the two engine
families; the23-item report does not dynamically substitute every API.

Read-only phone check2026-10-08: API30 visible19–23 only (5/23), missing1–18.
At the same time the actual IWLAN child was open, IMS transport WLAN, SIP200;
native SMS support/dispatcher were false. Thus the5/23 result is not an IMS
registration verdict, and voice/SMS advertised capabilities are not SMS success.

## Network evidence

Different operators can use different ePDG hostnames; the generic standard
candidate contains the SIM MCC/MNC. Actual endpoint selection also uses modem/
carrier configuration. Candidate DNS and a reference selection do not determine
the modem's actual endpoint. [3GPP TS23.003 §19.4.2.9](https://www.etsi.org/deliver/etsi_ts/123000_123099/123003/16.06.00_60/ts_123003v160600p.pdf)

Independent valid IKE_SA_INIT responses on UDP500 or4500 are positive response
evidence only. Timeout remains unknown.4500 includes the non-ESP marker; these
probes use temporary source ports and physical Wi-Fi and do not authenticate SIM
or perform IKE_AUTH. [RFC7296](https://www.rfc-editor.org/rfc/rfc7296.html)

The UI now places selected-SIM actual tunnel and complete/stable framework WLAN
registration alongside that probe. A locally registered IMS instance with SIP200
is evidence of successful actual protocol interaction in that generation, not a
new heartbeat. It does not prove current reachability of both UDP ports or next
call/SMS success. Confirming current data flow needs a new actual-session response
or successful use, or bounded bidirectional capture correlated with that session;
transmit counts, historic registration, ping and TCP tests are insufficient.
No new heartbeat/capture is added in this release.

Process observation compares phone/system-server identities before/after. The
read-only check does not restart them. Crash/system maintenance or previous
replace/reload operations may change identity; cause needs supporting logs.

## Validation scope

One consolidated build validates public reference attribution, explicit unknown
fields, immutability, shared-PLMN refusal, distinct MMS credentials,23-key coverage
and incomplete/unstable session-evidence refusal, plus existing contracts and
signed APK/engine identities. Device installation and visible acceptance must be
recorded separately; catalogue tests do not establish CTExcel/giffgaff registration.

The signed0.9.20/versionCode32 build passed70 new boundary assertions and926
existing network/APN assertions, together with the remaining release contracts.
All27 compiled production Java/manifest inputs match the current source. The
frontend was installed with its exact APK hash and the prior0.9.19 APK retained.
Ordinary read-only diagnosis still observed WLAN registration and open Child SA;
three engine APKs, module properties and phone/IMS identities were unchanged.
The separate own-UID UI test is built, but visible acceptance is pending phone
unlock. See [closed validation reports](reports/20261008-carrier-references/README.md).
