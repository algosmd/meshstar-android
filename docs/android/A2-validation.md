# A2 — Protected identity, contacts and box text

Protocol/spec source: meshstar `c2c854742af32d95c01090b9193dea327a0b65a5` (read only).
Implementation: `8c05d62`; emulator gate: `c4f7d25`. Build: `0.3.0 (3)`, application ID `net.meshstar.app`.

## Implemented

- A separate RNS identity, never the upstream BLE Noise identity. Android Keystore
  holds a non-exportable AES-256-GCM wrapping key. It seals the 64-byte RNS seed,
  credentials, contacts, ciphertext outbox, history, proofs and cursors in
  `noBackupFilesDir`. Writes use `AtomicFile`; filenames are authenticated as AAD.
  No plaintext fallback or silent identity rotation after a missing/invalid key.
  The encrypted file limit is checked before reading/overwriting, so an oversized
  write cannot replace readable state. Persisted schema classes have explicit R8
  keep rules for future minified builds.
  This is wrapped RNS key material, not a claim that Android 26 can natively
  generate both Reticulum asymmetric algorithms in Keystore.
- My QR, fingerprint, contact paste and CameraX/ML Kit scanner. Address/public-key
  mismatch and duplicate link parameters are rejected. Imports require an action;
  opening an external link never registers or sends credentials automatically.
- Box status check, `GET /api/pair`, canonical phone QR registration with
  `POST /api/pair`, user-entered URL/token and optional setup-link import.
- Raw encrypted LXMF packet sending; queued → sent → cryptographically verified
  delivered. Server `delivered` is only evidence of sent status; it cannot create ✅.
- Received text decoding, signature verification and durable proof queue with
  `no_proof=true`. Unknown/bad-signature messages are visibly unverified, never
  confirmed. Packet-hash dedup survives restart; seen history is capped at 5,000.
- Box cursors belong to its status `addr`, not its URL. Pending statuses are
  revisited because an existing server row can change without a new ID.
- Status/HTTP errors expose codes without logging endpoint/credential/request data.
  Redirects are denied, standard HTTPS trust stays enabled. Cleartext HTTP is
  allowed for explicitly configured box endpoints; no endpoint is bundled.
- Existing panic wipe also stops new polling, closes state admission and erases
  the RNS storage/key. After reset the RNS screen requires an application restart.
- Minimal Satellite / Internet screen reachable without completing BLE onboarding.
  The full unified navigation/localization/design pass remains A4.

## QR compatibility detail

The source iOS `SatContact` and Python box encode RNS contacts as `meshsat1:addr:pub:name`.
That wire prefix is **not** the old application URL scheme. Changing it would break
existing iOS/box scanners. My QR therefore displays the canonical RNS payload;
Copy MeshStar link emits a `meshstar://contact` wrapper. Input accepts that wrapper,
legacy `meshsat://contact`, and the canonical payload. Android→iOS sharing uses the
canonical QR/copy button. The BLE Noise verification format is not repurposed.

Existing box-contact QR codes carry identity, not HTTP credentials. Manual URL/token
configuration works with them. A `meshstar://box` setup link can additionally carry
URL/token; this additive Android import format is not claimed to be emitted by the
existing box/iOS UI. The user reviews it before Save and check.

## Validation

Local full JVM suite: 869 app tests discovered, 866 passed, 3 upstream skips;
RNSLite 22/22. The 17 carrier tests include pairing, both contact-link schemes,
address/key rejection, unsigned/forged delivery, unknown senders, durable receive/
proof replay, URL change, redirects, reset admission and the A3 cases below.
A further rendered-QR test (encode image → decode image → import contact) passes
for a Unicode name, including Cyrillic and emoji; QR generation explicitly uses
UTF-8. Debug APK and instrumentation APK compile with strict dependency verification.

CI: [A2/A3 run](https://github.com/algosmd/meshstar-android/actions/runs/43019588805).
The API 26 gate passed launch, rotation, real AndroidKeyStore encryption/reopen/
tamper/key-loss checks and UI navigation through QR/settings/scanner (2/2).
The same 2/2 instrumented tests passed on the local ARM64 API 26 emulator; the
normal app was installed/launched and its empty Satellite / Internet screen
visually inspected. The first API 34 CI attempt timed out during cold launch,
without a crash. `c4f7d25` adds a bounded cold-start wait plus a visible-UI check;
[the repeated run](https://github.com/algosmd/meshstar-android/actions/runs/43020865478)
is fully green: build, API 26 and API 34 (both instrumented tests on each).

## Build 3 — external links and camera-free import

Implementation: `b8919d7`. [Build 3 CI](https://github.com/algosmd/meshstar-android/actions/runs/43153859877)
passed build/JVM tests and both API 26/34 emulator jobs, including all four
instrumented tests on each API.

- MainActivity registers VIEW / DEFAULT / BROWSABLE for both `meshstar` and
  `meshsat`, with hosts `verify`, `user`, and `share`.
- Cold launch and `onNewIntent` reach the same signed QR verification handler
  used by the camera and paste UI, even before Bluetooth onboarding completes.
- The verification and RNS scanner screens expose an editable link field,
  **Paste link**, and **Open link**, without requiring camera permission.
  Clipboard data is read only after the user's explicit tap.
- New BLE verification QR links use `meshstar`; legacy schemes are input aliases.
  Signed canonical bytes and Noise/RNS identities remain separate. A valid QR
  self-signature alone does not mark a peer mutually verified without the BLE
  challenge/response. Expired/invalid links show a result instead of silently failing.
- `user` selects the named contact; `share` imports a draft/contact for review.
  Neither handler automatically sends messages or credentials.
- A new instrumented fixture resolves all six scheme/host combinations and opens
  a complete signed synthetic verification link, then delivers its legacy alias
  to the same Activity via `onNewIntent`. It also checks manual clipboard paste
  and the common verification result. The CI gate now runs four instrumented
  tests on API 26 and 34. Local API 26: **4/4 passed**, including clipboard
  paste and the cold/warm link flow. No real keys or contacts are used as fixtures.
- Build 3 was installed as an update (no data clear/uninstall). APK version and
  signature were checked. APK SHA-256:
  `6cb35cf79de3237856512b994e6f504c1aa9d144f1b3979b424c38509d7cf807`.

## Publication and public-source audit

`release.yml` and `fetch-georelays.yml` are manual-only and their root jobs are
restricted to the upstream repository. They cannot publish/attest/auto-commit in
this fork. Geo-relay workflow permissions were reduced to read-only. CI publishes
only synthetic test results and test APK artifacts, never a store release.

Pre-push broad audit: 40 inherited matches, unchanged from A0; zero matches in
new MeshStar runtime/test sources. No deployment addresses, credentials, device
identifiers or private logs are committed. SDK/local properties/build products
remain ignored. No writes were made to meshstar or iOS sources.

## Live acceptance — partial

The tester enters real addresses/credentials only in the emulator UI. No endpoint
is requested in chat or added to tests. The local API 34 image could not be
unpacked within available disk space. A smaller API 26 image with an existing
userdata image successfully boots; build 0.3.0 (2) is installed and open for the
tester. No user files were deleted to make room.

The tester confirmed **Box paired** in the emulator on this build. Cross-device
text/proofs and airplane-mode recovery have not yet been confirmed and are not
claimed as complete.

1. Open Satellite / Internet → My QR. Exchange canonical RNS QR contacts with iOS.
2. Settings → enter box URL/token → Save and check. Confirm paired/available.
3. Select the peer → Satellite. Send text each way through the two boxes.
4. Verify same 16-byte addresses/fingerprints and ✅ only after signed proof.
5. Restart the app; confirm identity, history and pending confirmations survive.

Physical BLE interoperability remains A5. Voice UI/Codec2 remains A6; encrypted
incoming voice frames are retained for later support rather than discarded.
History is capped at 10,000 messages; full inbox/outbox conditions fail without
acknowledging unsaved data. This is not yet the A4 retention-management UI.

References: [Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[network security configuration](https://developer.android.com/privacy-and-security/security-config).
