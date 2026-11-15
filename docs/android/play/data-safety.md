# Android 0.3.0 (8) — Data safety preparation

Privacy URL: https://meshstar.net/privacy

This is the reviewed worksheet for the declaration saved in Play Console.
It has not yet been sent to review with a release.
The website policy in read-only `meshstar/docs/store/privacy-policy.md` is written
for iOS. Its Android details and server retention statements must be reconciled
before the closed-track submission. Do not copy “no data collected” blindly.

## Verified client behavior

- No account registration, sign-in, phone-number or email requirement.
- RNS direct text and Codec2 voice are encrypted for the recipient and signed.
  The box/mailbox cannot decrypt the content. Public Bluetooth chat and ordinary
  MeshCore text channels are deliberately not private E2E conversations.
- No advertising or analytics SDK is intentionally enabled. The ML Kit scanner
  was replaced with local ZXing decoding because ML Kit documents telemetry.
  Nostr, its relay-directory updates and Tor startup are disabled in this build;
  the upstream source and Bluetooth wire protocol remain available/unchanged.
- RNS identity seed and persisted contacts/history/settings are encrypted locally
  with a non-exportable Android Keystore AES key. The app excludes backups.
  This is not iOS Keychain and not native Keystore storage of X25519/Ed25519 keys.
- Microphone is used only for user-initiated recording; camera only for scanning.
  Android location permissions are retained for the upstream BLE/Wi-Fi discovery
  lifecycle. Live geographic lookup is separately disabled. The website must not
  say that Android never requests a location permission.
- Internet mailbox address starts empty. User-configured endpoints receive routing
  addresses, hashes, timing, sizes and connection metadata, plus ciphertext.
  Box access credentials are sent only to the configured box.
- Incoming notifications use generic text; no content or contact preview is put
  on the lock screen by the new RNS/channel notifier. Android notification/sound
  settings control delivery; no remote push analytics service is added.
- Public radio traffic is user-directed sharing with participants. Report/share
  actions hand content to another chosen application; inspect that application's
  own privacy practices.

## Saved declaration and prerequisites before review

| Play question | Proposed answer / prerequisite |
| --- | --- |
| Accounts created in app? | No. Account-deletion flow is not applicable. Local data can be erased. |
| Message content collected by developer? | E2E private content is not readable by the developer. Apply Google's E2E exception only where its criteria hold; do not extend this claim to public/channel content. |
| Any collected/shared data? | Yes: Device or other IDs, collected for App functionality by the developer-operated demo gateway/relay; not shared with third parties. Operator confirmed token names / recipient address hashes, timestamps and sizes retained at most 30 days, with deletion on request. E2E content does not exempt this metadata. |
| Third-party SDK collection? | ML Kit removed; retained dependencies and enabled services audited. Re-evaluate when enabling any upstream feature or changing an SDK. |
| Encrypted in transit? | RNS private payloads: yes. Do not claim every connection is TLS: user-configured HTTP box URLs remain supported. Operator confirmed all developer-operated demo gateway and relay access is exclusively HTTPS or protected VPN, with no exposed HTTP. The declaration of encryption in transit relies on that deployed protection; it does not claim the client rejects every third-party HTTP URL. |
| Data deletion? | Local erase supported. Operator confirms deletion on request through the policy contact and retention at most 30 days. The public policy must reflect this behavior. |
| Ads / tracking? | No advertising or analytics feature in this build. |

Suggested concise public wording:

“MeshStar requires no account and does not use advertising or analytics.
Private RNS messages and voice notes are end-to-end encrypted; relay and mailbox
operators cannot read their content. Public Bluetooth chats and ordinary radio
channels are visible to participants. Keys and history stay in protected storage
on your device. Optional servers process routing metadata to deliver messages;
see the privacy policy for operator-specific retention.”

## Sources

- [Google Play Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en): SDK behavior is included; encrypted-content exceptions have conditions; closed testing needs the form.
- [ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure): the reason to replace the scanner.
- Read-only policy source: `meshstar/docs/store/privacy-policy.md`, revision `0fcceb6`.

No production endpoint, credential or device diagnostic is included in this worksheet.

## Operator confirmation and source audit — 2026-11-13

The operator confirmed no retained raw IP addresses and no accounts, personal
profile, contact-book or location collection. Mailbox code uses a daily-key HMAC
of the client IP for in-memory limits; access logging is disabled. Box default
logging level is warning. Relay source logs routing identifiers and delivery
metadata. No deployed reverse-proxy logs were inspected. The 30-day retention
and deletion-on-request statements are operator attestations, not independently
measured deployment properties.

## Build 8 transport restriction

Box and mailbox reject public HTTP before saving or sending; private literal IPs
and localhost remain allowed. This does not encrypt ordinary LAN HTTP and does
not change the operator-confirmed HTTPS/VPN basis of the saved declaration.
