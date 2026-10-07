# Tool0.9.18: interface and UID2000 evidence

Scope is physical API30 UI acceptance and read-only ADB feasibility. It is not
SMS stability, modern carrier, Shizuku transport or clean non-root installation
acceptance. Production tool bytes are pinned in `tool-build.json`; the actual
instrumentation target hash is independently checked in each UI run.

| Evidence | Meaning |
| --- | --- |
| `tool-build.json`, `apk-validation.json` | Signed0.9.18 tool; exact independently built ingress-engine assets |
| `ingress-build.json` | API30/API31 candidate compile/sign proof; engines not deployed |
| `ui-light.json`, `ui-dark.json` | Own-UID measured/visible three-page checks passed while awake; no mutation action |
| `display-restoration.json` | Temporary night/font/USB stay-awake settings restored |
| `uid2000-capabilities.json` | Actual ADB UID2000 read-only results; no Shizuku SDK transport |
| `uid2000-file-scope.json` | Module file exists for root; shell read-access check failed |
| `final-state.json` | Original three engines/processes retained; WLAN registered; native SMS/dispatcher false, recovery LIMIT |
| `tool-update-native-ready-failure.json` | Tool installed successfully, stronger native-SMS-ready acceptance unmet |
| `ui-logic-only-locked.json` | Passed logic assertions but incomplete locked-screen drawing; not visual acceptance |
| `ui-visible-locked-failure.json`, `ui-dark-locked-failure.json` | Retained visible-content failures while locked |
| `summary.json` | Closed aggregate boundaries; unresolved SMS and deferred Android17 |

The current visible-content instrumentation's source hash is retained in
`ui-instrumentation-build.json`. The earlier logic-only test preceded that
additional assertion; its result is not substituted for the stronger run.
Production Java and manifest inputs still match the installed build exactly.

PNG files are application-owned view canvases inspected after successful awake
runs. They contain only this tool's interface, not SMS contents, incoming
notifications or other applications. Dark mode was combined with1.3 font scale;
light mode used the original1.0 scale. This does not establish every viewport or
OEM rendering path.

See [the implementation, artifact identities and Shizuku interpretation](../../MATERIAL3-SHIZUKU-20261008.md).
