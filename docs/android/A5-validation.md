# A5 — Bluetooth regression and MeshCore companion

Build: **0.3.0 (5)**, application ID `net.meshstar.app`.
Status: **A5 implementation and primary live acceptance passed.** Direct LoRa
and Relay delivered text with signed confirmations in both directions. Concurrent
Bluetooth/MeshCore operation and Public history were also confirmed. Test coverage
limits are recorded below; they are not presented as completed hardware tests.
Specification: read-only meshstar `0fcceb6`, `docs/codex/m11-android.md`.
iOS behavior reference: `codex/m7-mailbox`, `45daf4c` (0.3.0 build 18).
Neither source reference was changed. RNSLite formats and vectors were not changed.

## Implementation

| Requirement | Result |
| --- | --- |
| BLE regression fix | Scanner/advertiser are acquired when used, rather than cached before permission/Bluetooth readiness. Existing service/characteristic identifiers and wire protocol remain unchanged. |
| Companion BLE | Separate Nordic UART adapter; filtered scan, service discovery, negotiated MTU, notifications, bounded writes, reconnect/backoff, RSSI. Same foreground owner as the existing mesh service. Stale connection callbacks are ignored. |
| Command queue | One request at a time; pushes cannot satisfy replies. Timeout/cancellation closes the connection. Contact streaming uses an inactivity deadline. Incoming content is persisted before the next destructive queue read. |
| MESHSAT channel | Discover an existing configured channel; only an empty slot may be written automatically. An occupied/mismatched slot requires a separate replacement confirmation and a valid secret. Readback verifies the write. Secret remains in encrypted storage. |
| RNS LoRa/Relay | Existing fragment format; at least 1.5 s between radio submissions. Relay adds HEADER_2 using the configured box transport identity without changing the signed packet hash. Only a valid recipient proof grants ✅. |
| Routes/status | Auto prefers Bluetooth, LoRa, Internet, then Satellite. Relay is explicit. Node name/RSSI/slot, antenna path, SNR/hops, via-point inference and whole signature line are shown. Manual unavailable paths do not silently fall back. |
| Contacts/adverts | Contacts and received adverts appear in the node screen. Only explicitly opened or active native DMs enter the chat directory. Unknown sender-prefix text is retained; ambiguous prefixes are not assigned to a contact. Location fields are skipped. |
| Ordinary channels | Foreign native channel/direct text is retained. MESHSAT cannot become a text chat. Native text can be sent to ordinary channels and known direct contacts; it does not claim RNS signature/proof delivery. |
| Public history | Box channel list/history polling, cursor keyed by box identity and channel name, merging by channel name + sender timestamp + text rather than slot. Imported rows are marked as box history. |
| Storage/lifecycle | Durable RNS outbox/proof queue, deduplication and bounded history. Node switching preserves the carrier of deferred receive/proof work. Previous BLE teardown completes before a new runtime starts. Reset also clears node work. |
| UI/languages | Node setup, scan/selection, slot/secret import, replacement, contacts/adverts/channels, optional MIUI reception hint in en/ru/ro/de/fr. Compact existing design retained. |

## Automated validation

- App JVM: **883 discovered, 880 passed, 3 upstream skips**.
- RNSLite: **22/22 passed**, including the existing Python interoperability oracle.
- MeshCore JVM: **9/9 passed**.
- Debug application and instrumentation APKs: **BUILD SUCCESSFUL**.
- Local API 26: **9/9 instrumented tests passed** on the final application/test
  APKs, including occupied-slot confirmation and hiding transport/empty channels.
- CI configuration includes `:meshcore:test` and nine instrumented cases on API
  26/34. No new remote CI result is claimed in this local report.

New tests cover occupied-slot consent and key validation, discovery of an existing
transport slot, datagram framing, foreign text, unsolicited pushes, timeout/late
reply isolation, contact streaming/inactivity, fragment pacing/reassembly, Relay
hash/proof compatibility, durable receive/dedup, via-point proof evidence, original
carrier preservation, native prefix ambiguity, cross-slot Public history merging,
and BLE endpoint availability after first-start permissions.

## Physical BLE regression — tester observations

Baseline **0.3.0 (4)** was installed manually after MIUI rejected USB installation.
Identity/data were not cleared. The first launch had unavailable BLE endpoints;
restarting the application started scan/advertising. Build 5 fixes this stale
endpoint caching and has dedicated regression tests.

Confirmed by the tester on 2026-11-12 using Android 11 / MIUI 12.5:

| Check | Result |
| --- | --- |
| Android MeshStar ↔ iPhone MeshStar public chat | Passed both directions on build 4 |
| Android MeshStar ↔ iPhone MeshStar DM | Passed both directions on build 4 |
| Home/background, incoming DM | Notification and message received |
| Screen off for about one minute, incoming DM | Notification and message received |
| Android MeshStar ↔ ordinary upstream client public/DM | Tester confirmed both chats in both directions; the other client platform was not specified |
| Build 5 Bluetooth with MeshCore connected | Tester confirmed public and direct chats work while the companion connection remains active |

The app's Bluetooth/location permissions and foreground service were active during
the scoped diagnostic check. The tester subsequently checked the settings:
**Autostart is off; the selected battery mode is “Battery saver”.** Background
and short screen-off reception succeeded without requiring unrestricted battery
mode or autostart. No claim is made that either setting was changed for the test.
The optional first-launch hint recommends checking them only
if background reception stops; it does not change system settings automatically.
One-minute screen-off success is not an overnight/Doze endurance result.

## Physical MeshCore acceptance

On 2026-11-12 the tester confirmed companion connection and the existing MESHSAT
channel configuration on Android **0.3.0 (5)**. With a second companion connected
to iPhone, both clients explicitly selected **LoRa** in their RNS conversation.
Text arrived **Android → iPhone and iPhone → Android**, and both senders received
signed ✅. This is direct LoRa acceptance, not a Relay or HTTP-box result.

On 2026-11-13 the tester confirmed the separate **Relay** scenario: messages
arrived in both directions and both senders received signed ✅. This records the
reported Relay/box-bridge acceptance; it does not claim measured latency or a
particular bridge-versus-orbit backhaul. No endpoint or message contents were
collected.

Also on 2026-11-13, the tester confirmed both additional scenarios:

- Bluetooth public and direct chats work while the MeshCore companion remains
  connected; neither transport displaced the other in this test.
- Ordinary Public channel send/receive works, and opening its history with the
  box connected shows messages without duplicates.

## Coverage limits

- Occupied-slot refusal, explicit replacement consent, and secret validation passed
  JVM/UI tests; destructive replacement on physical hardware was not performed.
- The final UI-only APK refresh hides empty slots. The last device hash check
  identified the preceding build 5 candidate with identical transport code. The
  device was unavailable for the subsequent verification attempt; final artifact
  replacement is not claimed.
- The ordinary upstream client public/DM check passed both ways. Its platform was
  not specified, so separate upstream iOS and Android matrix coverage is not claimed.

A6 voice recording/playback and completion of notification parity are not included.
Incoming encrypted voice frames are retained for later support; playback or voice
proof acceptance is not claimed. Native MeshCore text is distinct from signed RNS
messages. No Play upload or deployment change was performed.

## Privacy

Only synthetic fixtures are used in tests. No production endpoints, tokens,
channel secrets, contact identities, phone serials, raw logs or physical-device
screenshots are included. BLE diagnostics were reduced to fixed event categories.
No physical location data was read, decoded or retained.

## Local install artifact

Final ARM64 debug APK signature verified; it is staged in the physical device's
Downloads for manual update because MIUI refused installation through ADB.
Physical package verification confirms **0.3.0 (5)** and a successful launch.
The installed candidate has the same transport code and SHA-256
`2658b4955a92e9a30cbb540fd0358f38c3d1dc1a498a422d3ff32adb1aeb2ba8`.
The refreshed file below additionally hides empty MeshCore slots from the chat
list; its replacement on the physical device is pending.
SHA-256: `0fa26c9e25c37c55ae8925b0e8df5cc621e3c6dae5c75337a1166944c9a0db3f`.
