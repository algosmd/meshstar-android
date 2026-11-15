# A6 — Voice, background reception and Play preparation

Build **0.3.0 (8)**, package `net.meshstar.app`; Play contains build 8 in the closed Alpha draft (not available to testers).
Status: implementation and automated validation completed; physical Internet, LoRa and Satellite
voice accepted. The 30-minute background test and actual
Play installation are tracked below and are not claimed as passed.

Read-only references: meshstar `0fcceb6`, `docs/codex/m11-android.md`, box voice API
and vectors; iOS M7 `45daf4c`. Neither reference checkout was modified.

## A5 publication

A5 report commits `316e35c` and `c8b5d89` are included in `origin/main`; push
completed through `c8b5d89`. A remote CI result is not claimed in this report.

## Implementation

| Requirement | Result |
| --- | --- |
| Codec2 | Separate `:codec2` module, upstream 1.2.0 pinned archive/checksum, mode 1200, JNI and shared `libcodec2.so`, unchanged DSP/codebooks. NDK 28.2, CMake 3.22.1; four ABIs. LGPL-2.1 and KISS FFT BSD license/source export in Settings. |
| Recording/playback | PTT microphone permission, countdown, 31.92-second maximum, preview/delete/send, exclusive playback with audio focus. Recording stops when leaving/backgrounding; PCM stays in memory. Radio-compression and LoRa-airtime hints in en/ru/ro/de/fr. |
| Frames/proofs | Existing RNSLite formats unchanged. Max 1400 bytes/frame, multipart durable reassembly, last-frame signed proof only after full verified note. NO_PROOF respected. Invalid/unknown senders never get a trusted receipt. |
| Carriers | Satellite posts each binary frame to `/api/rns/voice`; receipt checked using actual `id`, `bytes`, `dest` contract. Internet uses voice drops with existing PoW/pickup/ack. Direct LoRa uses existing fragment pacing. Relay voice is explicitly unavailable. |
| Reliability | Partial outgoing progress persisted; retry resumes identical encrypted frames. Incoming parts survive process restart before destructive mailbox ack. Duplicates, expiry and storage limits bounded. Storage failure publishes neither delivery nor proof. |
| Notifications | Incoming RNS text/voice and ordinary MeshCore channels use the existing BLE notification channel/sound settings. Generic private lock-screen content, no replay of historical notifications, open chat suppressed, read notifications cancelled. |
| Background | Existing mesh foreground service owns mailbox/box polling and companion BLE. Mailbox uses 25-second long poll. No added timer-only background service. Actual screen-off endurance remains a separate acceptance item. |
| Privacy | Offline ZXing scanner replaces ML Kit telemetry dependency. Automatic Nostr/Tor/relay-directory startup and legacy payment command disabled; upstream source and Bluetooth identifiers/wire protocol preserved. No server endpoint or credential embedded. |
| Release | versionCode 8 (Play draft remains 7), R8/resource shrinking, upload signing references private Gradle properties. Upload key generated outside source control; no credentials printed or committed. Upstream release workflow moved to an inert `.disabled` file. |
| Store | Five-language Android listing adapted from `docs/store/`, 512×512 icon, 1024×500 feature graphic, A4 synthetic screenshots available. Data safety worksheet records E2E/public-channel distinction and operator-confirmed server metadata policy. |

## Automated checks

- Full application JVM suite: **892 discovered, 889 passed, 3 upstream skips**.
- RNSLite: **22/22**, including voice, multipart/proof and existing protocol vectors.
- MeshCore: **9/9**.
- New route/notification tests: 6 voice-carrier + 2 notification cases.
- Debug API 26 instrumentation: **13/13** (existing flows plus native Codec2 and
  offline QR tests).
- Minified API 26 instrumentation: **5/5**, testing native golden encode, bounded
  decode/WAV, real Android Keystore persistence/tamper refusal and camera-frame QR.
  Test-only keep rules retain externally called test entry points/Kotlin helpers;
  the production AAB does not include those rules or the test APK.
- Codec2 encoder output equals the `codec2` vector byte for byte; native decode
  accepts the maximum note and rejects malformed/overlong input.
- Voice API receipt test re-run after comparing with the actual box implementation:
  correct endpoint, octet-stream body, `bytes`/`dest` receipt, no unsigned delivery.
- Signed production AAB built successfully; JAR signature verification passed.
- All 24 packaged native libraries inspected: all 64-bit LOAD segments support
  16 KB alignment; no local build path found in packaged native binaries.
- CI now installs pinned NDK/CMake and runs 13 device cases on API 26/34. No remote
  A6 CI result is claimed before its source is published.

## Physical acceptance

A minified, **debug-signed** release candidate was installed over the existing
build on the Android 11 / MIUI 12.5 test phone. Version name/code and launch were
verified; identity/data were not cleared. USB update succeeded. It is not the
Play-signed build and is not described as a Play installation.

| Scenario | Evidence / status |
| --- | --- |
| Internet voice Android → iPhone | Tester confirmed playback and signed ✅ on 2026-11-13 |
| Internet voice iPhone → Android | Tester confirmed playback and signed ✅ on 2026-11-13 |
| Direct LoRa voice both ways | Tester confirmed playback and signed ✅ on 2026-11-13 |
| Satellite voice both ways | Tester confirmed playback and signed ✅ on 2026-11-13 |
| Screen off for a continuous 30 minutes | Deferred by tester; not started/accepted. Short A5 success is not substituted |
| Notification opening/read clearing on the physical phone | Awaiting physical confirmation; automated notifier checks passed |

The final candidate refresh corrected the voice box receipt to the real API
contract and retains the same Internet voice implementation as the accepted run.

## A2–A5 release / Play checklist

The previous accepted debug results remain documented in A2–A5. Repeat them on the
actual Play-installed release; **all entries below are pending on Play**:

- [ ] A2: identity persists, contact QR/paste and legacy schemes, pairing, satellite
  text in both directions with recipient-signed ✅.
- [ ] A3: Internet text in both directions with ✅, empty server disables mailbox,
  retry/long-poll behavior and restart persistence.
- [ ] A4: unified list/routes, key verification, unread badges, language/theme UI.
- [ ] A5: Bluetooth public/private with iPhone and upstream client, background
  reception, companion/channel setup, simultaneous BLE, LoRa and Relay text ✅,
  Public send/receive/history without duplicates.
- [ ] A6: voice on all three supported paths, multiline/multipart note, receipt,
  and incoming notifications with the screen off for 30 minutes.

## Artifacts

- Production upload-signed AAB: `app/build/outputs/bundle/release/app-release.aab`.
  SHA-256: `b968ba1af650b94bccc4d22c33d6595b15be32ae1e964b87bdcf6add53ee881b` (build 8).
- Local minified validation APK SHA-256:
  `04a180eb1cca8f208ab5eea94429ba7f3f2e08a773adaa3de3b3ca176c030057`.
- Play materials: `docs/android/play/`; sources/licenses: `codec2/` and bundled
  `codec2-source.zip`. Native debug symbols and R8 mapping remain local build output.

## Privacy and release limits

No device serials, personal paths, endpoints, tokens, contact identifiers, real
messages, raw logs or physical screenshots are included. Device location was not
read or recorded. Store screenshots are pre-existing A4 synthetic fixtures.

At the initial A6 preparation, website privacy described iOS and relay operational logs. The Android
Data safety worksheet is not a submitted “no collection” declaration: operator
metadata retention and HTTPS/VPN deployment have been confirmed. The Android
website amendment was prepared locally; the later website follow-up below records its publication and remaining wording notes.
Play Console setup is ongoing; closed-track upload/review/installation must be
recorded only after the corresponding state is actually observed.

## Play Console setup progress

Privacy URL, no-login access, no ads, 18+ target audience, government/financial/
health declarations, Communication category and public support contacts saved.
IARC questionnaire accepted and saved with the owner’s explicit agreement.
ESRB/USK/ClassInd: Everyone; PEGI: parental guidance; generic IARC and selected
regional ratings: 12+. These are content ratings, separate from the 18+ target.
The form declares BLE blocking, no in-app reporting and no chat moderation.
Five-language listing with three A4 synthetic screenshots, app icon and feature
image is saved and marked ready for review. The existing account trader status was retained. Owner approved marking only the assistant-created feature image as AI-assisted. No production
release or closed-track availability is claimed at this stage.

The signed AAB version 6 (0.3.0) was uploaded and processed by Play Console; the
closed Alpha release with five-language release notes was saved as a draft.
One owner-designated tester is attached to the channel; no tester identifiers
are recorded here. The saved country scope is 177 available countries/regions, excluding
France. At that point the website policy update and endurance test were outstanding; the later sections record the website update and build-8 draft.

Data safety was saved: Device or other IDs collected optionally for App
functionality, no third-party sharing, non-ephemeral processing, protected transit
and deletion-request URL. No account creation or external-account login.
Only the assistant-created feature graphic is marked AI-assisted; the existing
icon and actual app screenshots are not.

## Build 7 permission cleanup

Play analysis of build 6 required declarations for background discovery,
foreground services and broad photo/video access. Build 7 removes broad media
and legacy external-storage reads: attachment pickers already grant access to
individual selected URIs. The inherited APK download worker is disabled both at
scheduling and execution; its WorkManager foreground service and DATA_SYNC
permission/type are removed. Background Bluetooth discovery permissions and the
connectedDevice/location service types remain unchanged for compatibility.

`:app:bundleRelease :app:testDebugUnitTest` passed again: 892 tests, 889 passed,
3 upstream skips, zero failures/errors. Upload signature verified. The merged
release manifest has versionCode 7, none of the removed permissions, and retains
ACCESS_BACKGROUND_LOCATION. This does not replace the deferred physical
30-minute endurance test or the pending Play-installed regression checklist.

Permission explanation drafts are in `play/permission-declarations.md`.
The owner supplied a demonstration link. Playback worked, but the inspected
middle/end portions showed iPhones; Android background-permission and reception
evidence was not verified. No demonstration link has been submitted. An Android
clip or the relevant timestamp is requested.

Play Console processed build 7 and saved it as `0.3.0 (7)` in the Alpha draft,
replacing build 6 in that draft (6 remains in the artifact library). The review
page now reports **two** errors: background-discovery permission declaration and
foreground-service declaration. The broad photo/video permission error is gone.
The release has not been submitted for review or made available to testers.

## Website follow-up

The public privacy page was refreshed and verified on November 13: it now covers
Android, the Internet mailbox and deletion requests for server logs. Remaining
wording corrections: replace the blanket absence of third-party SDKs with no
enabled advertising/analytics SDKs; do not claim Android 12+ exclusively uses
Nearby devices (the current onboarding still requests location permissions);
scope per-chat retention and triple-tap wipe to iOS where implemented. Proposed
replacement wording is in `play/privacy-android-addendum.md`.

## Batch 20.1 — carrier HTTP policy (November 15)

Read-only specification: meshstar `44ba4cf`, `docs/codex/app-feedback.md`.
Box and mailbox settings, including previously stored endpoints at request time,
reject public cleartext HTTP before DNS resolution or credential transmission.
HTTP accepts only canonical IPv4 literals in RFC1918/CGNAT ranges
`10/8`, `172.16/12`, `192.168/16`, `100.64/10`, and localhost/loopback
(`localhost`, `127.0.0.1`, `::1`). Arbitrary DNS names, including names resolving
to private addresses, require HTTPS; this prevents DNS rebinding of HTTP hosts.
Localhost is pinned to loopback. Redirects and proxies are disabled on the carrier
client, including when a custom client is supplied. Rejected settings preserve
the existing configuration; clearing the Internet server still disables it.
The HTTPS-required hint is translated into all five application languages.

**Platform limitation:** Android Network Security Configuration supports domain
matching, not CIDR rules. `network_security_config.xml` retains the platform
cleartext opt-in needed for arbitrary user-configured private IPv4 addresses;
the exact allowlist is enforced by `CarrierNetworkPolicy`/`CarrierHttp`, not by
XML. This is enforcement for BoxTransport/MailboxTransport, not an OS-wide
firewall for every upstream networking component. No claim is made that an XML
CIDR restriction exists. See the official
[Network Security Configuration documentation](https://developer.android.com/privacy-and-security/security-config).

Private-LAN HTTP is still unencrypted HTTP; this change does not prove that all
metadata is encrypted in transit. The existing Data safety answer remains based
on the operator's separate HTTPS/VPN-only demo-service confirmation.

Version codes 6 and 7 were already processed by Play; the new local artifact uses
8. Physical endurance, Android permission video and Play-installed regression
remain pending.

Build 8 validation: `:app:testDebugUnitTest :app:bundleRelease` passed.
897 JVM tests discovered, 894 passed, 3 upstream skips, zero failures/errors.
The five new policy tests cover range boundaries, spoofed names, public HTTP
rejection before DNS/credentials, preservation of saved configuration, working
loopback HTTP and redirect rejection even with a redirect-enabled supplied client.
All five localized hints are present; the merged manifest points to the network
config and reports versionCode 8. AAB upload signature verified.

## Build 8 upload and local installation (November 15)

Google Play processed AAB 8 and saved release `0.3.0 (8)` in the closed Alpha
draft, replacing 7 in that draft. The tester mailing list remains selected.
The age-rating questionnaire is submitted; the saved Data safety answers still
declare collection, encryption in transit and no account creation.

Two blocking declarations remain: background discovery and foreground services.
Both need a suitable Android demonstration video. The previously supplied iPhone
video was not submitted as Android evidence. This release is **not available to
testers** and has not been sent for review. Upload alone does not close A6/M11.

A minified local validation APK with the existing test signing identity was
installed as an update on the physical Android phone, preserving application
data. Package version reports `0.3.0 (8)`; a cold launch returned success and
the application process stayed running. This is not a Google Play installation
or a substitute for the pending 30-minute screen-off and Play-release checks.

## Source publication check — November 15

Before publication to `main`, the Gradle check for `:app:testDebugUnitTest
:rnslite:test :meshcore:test` succeeded with all tasks up-to-date. Existing
results match the unchanged source inputs: application 897 tests / 3 skips,
RNSLite 22 tests, MeshCore 9 tests, zero failures/errors. This was an incremental
validation, not a new physical or instrumentation run. The local production AAB
SHA-256 still matches the build-8 artifact recorded above.

The source publication includes the Codec2 source/license export, offline scanner,
voice/notifications, release and HTTP-policy work, sanitized reports and Play
materials. Signing files, private Gradle properties, machine-local configuration,
raw logs and build outputs are excluded.
