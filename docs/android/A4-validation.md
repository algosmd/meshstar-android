# A4 — Unified chats and design

Build: **0.3.0 (4)**, application ID `net.meshstar.app`.
Implementation commit: `c373b0a`.
Specification: read-only meshstar `0fcceb6`, `docs/codex/m11-android.md` and
`docs/codex/design-base-bitchat.md`; AlGos rules take priority.
The source specification and iOS checkouts were not modified.

## Implemented

| Requirement | Result |
| --- | --- |
| One directory, three tabs | Bluetooth / LoRa / Satellite, search, nearby contacts and one conversation row per linked identity. Bluetooth public chat stays on the main Bluetooth page. |
| Identity merging | Explicit association by BLE fingerprint and RNS address, never by nickname. One combined history and stable contact color; Noise and RNS cryptographic identities remain separate. |
| Unread | Compact red rectangular corner counters on tabs and rows, separate transport counts, persistent RNS read markers. Existing history is marked read once during migration. |
| Route menu | Auto / LoRa / Relay / Satellite / Internet, localized full labels and antenna for LoRa/Relay. Auto uses available Bluetooth, then configured Internet, then box. Manual paths never silently fall back. |
| Route status | Box `link.online` produces antenna → 🌐 via point. Historical messages retain the route captured at submission/receipt; Internet has 🌐, Satellite 🛰. Server status never grants signed delivery. |
| Key verification | Contact QR/fingerprint and one-tap verified/unverified action. Pins bind to the exact public key and persist in encrypted storage. BLE verification still uses the existing challenge/response. |
| Header | MeshStar/@nickname with QR and +. Title opens settings; internal back is a square icon. The temporary Back / My QR / Settings header is removed. |
| QR/import | My QR has Copy QR and Share. Camera and Paste link use the same parser for meshsat1, current meshstar links and legacy meshsat input. Clipboard is read only on tap. |
| Design | Monospace regular 10/12/14 sp, orange own messages, stable peer colors, 3 dp corners, 22 dp composer and 26 dp header painted controls with separate 44 dp hit areas, 8 dp between QR/+ painted frames. |
| Metadata | No Bluetooth icon beside message time. Signature OK and check remain together on one line; route text yields space first. |
| Languages | Complete new UI/status/error catalogs in en/ru/ro/de/fr. Other inherited locales fall back to English for these keys. Visible inherited nearby/onboarding branding corrected. |
| Existing storage | Update preserves identity, configured carriers, contacts, queued ciphertext and history. Migration adds optional verification/link/read fields without changing packet formats. |

The live A2 result is recorded separately in A2-validation.md: text through two
boxes with signed confirmations in 4 and 9 seconds. A3 Internet text in both
directions with signed confirmation was accepted before this UI update.

## Validation

- Full local app JVM suite: **875 discovered, 872 passed, 3 upstream skips**.
- RNSLite: **22/22 passed**; no RNSLite source/vector changes.
- Debug application and instrumentation APKs: **BUILD SUCCESSFUL**.
- Local API 26: **7/7 instrumented tests passed** (Keystore persistence/tamper,
  unified entry and QR, complete signed cold/warm links, directory/routes,
  one-tap verification, paste and Russian scanner Activity-owner regression).
- New JVM cases cover explicit identity linking, Auto/manual selection, read/pin
  persistence and old-state migration, box-address validation, link.online and
  historical route stability without granting delivery, and catalog parity.
- Design capture tests: **2/2 passed**, 121 captures per size (240 standard
  combinations plus two Russian route-menu captures with font scale 130%).
- [CI run 43160505815](https://github.com/algosmd/meshstar-android/actions/runs/43160505815)
  for `c373b0a`: **all jobs passed** — build/JVM tests, API 26 and API 34.
  Each emulator passed launch, rotation and all seven instrumented cases.

## Design evidence

Open [the offline comparison gallery](screenshots/A4/index.html).
It displays each Android capture beside the nearest archived iOS **0.2.0 (21)**
reference, with selectors for language, theme, screen size and page.

| Matrix | Coverage |
| --- | --- |
| Android small | 720×1280 px at 320 dpi; 360×640 dp |
| Android large | 1080×2340 px at 400 dpi; 432×936 dp |
| Languages | en, ru, ro, de, fr |
| Themes | light and dark |
| Pages | Bluetooth, LoRa, Satellite, chat, routes, My QR, contact, settings, box, node, language, scanner |
| Extra | Russian route selector at 130% text size on both sizes |
| iOS baseline | 14 original build 21 English fixture captures, medium and large, light/dark where archived |

Android images capture application content, excluding OS status/navigation bars.
The iOS baseline archive contains English only: this report does not claim a new
five-language iOS build or exact screen-for-screen feature parity. The gallery
names the selected nearest reference where an exact counterpart is absent.
All captures contain synthetic fixtures, not live contacts, endpoints or credentials.
The snapshot test renders the actual unified UI without loading runtime storage.

Reproduce with the test APK installed and an emulator configured to each size:

```sh
adb shell am instrument -w -e class net.meshstar.DesignSnapshotsTest \
  -e meshstarSnapshots true -e viewport small \
  net.meshstar.app.test/com.bitchat.android.ui.PerformanceTestRunner
python3 tools/meshstar/design-gallery.py
```

Use viewport `large` for the second size. Capture output is under the app's
external `files/a4` directory. Default CI does not run this opt-in snapshot suite.

## Live acceptance — refreshed UI

On 2026-11-12 the tester confirmed **Android 0.3.0 (4) ↔ iPhone through Internet**:
text arrived in both directions and both senders received ✅. The test used the
unified Satellite directory and the explicit Internet route selection. Existing
contacts and carrier settings survived the update. No endpoint, contact identity,
credential or message contents are recorded.

## Scope and remaining live checks

- A4 exposes LoRa/Relay controls but does not implement the A5 MeshCore transport.
  These selections explicitly show unavailable and cannot send or silently switch
  to a paid path. The node screen explains this; no real node is claimed connected.
- The A4 box path implements `link.online` and persisted via-point display. Radio
  hop/SNR and radio-specific proof-path evidence require the A5 receive metadata.
- The composer is shared text UI. Recording, media controls and the remaining
  notification work belong to A6; no voice/media support is claimed here.
- Existing Bluetooth engine is wired into the unified directory/public/DM UI.
  Physical Android↔iOS/upstream Bluetooth regression remains the A5 device gate;
  emulator UI tests cannot establish radio interoperability.
- The refreshed UI Internet text/proof round trip is accepted above. A box-path
  repeat on build 4, offline/restart and server-side mailbox ACK live checks remain
  pending; the A2 live box result is from build 3 and failure-path mock tests pass.
- No Play publication, deployment endpoint or credential was added. Existing
  release/georelay workflow fork guards remain unchanged.

## Local install artifact

The ARM64 debug APK was installed as an update, without uninstall or clearing data.
SHA-256: `f8c8bcf34a4b60f2f8705752268f93d4f646ebdcbd169cb285289898fd2f0780`.
