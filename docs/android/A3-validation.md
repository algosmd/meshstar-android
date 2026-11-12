# A3 — Internet mailbox text carrier

Source: meshstar `docs/mailbox.md` and A1 vectors at
`c2c854742af32d95c01090b9193dea327a0b65a5`; implementation `8c05d62`.
Build: `0.3.0 (3)`. No deployed server address is included.

## Implemented

- `net.meshstar.mailbox.MailboxTransport`: JSON POST drop/pickup/ack. No box
  credential, cookies, redirects, request logger, public mailbox or push registration.
- `wait: 25`, 40-second read / 45-second call timeout, immediate next polling cycle
  with a short scheduling gap. Queries cancel when no owner remains or settings
  change. Empty Internet server is the default and disables its workers.
- Yesterday/today/tomorrow UTC mailboxes for every contact, groups ≤255 addresses.
  Cursor keys include the server and address-set hash: changing contacts/day causes
  a safe replay. At most 64 cursor sets are retained. Additional groups poll with
  wait=0 so a large contact set does not block behind several long polls.
- RNS ciphertext and hashes are unchanged across paths. Durable outgoing ciphertext
  is retried after connectivity returns; server acceptance is not ✅.
- On `429` with `detail.error == "stamp required"`, solve exactly `detail.pow_bits`
  and retry once. Mining runs away from UI threads, is cancellable and rejects
  difficulty above the A1/iOS mobile ceiling of 24. Ordinary rate limits back off.
- Inbox response validation checks size, IDs, cursor, selected mailbox and kind.
  Persist messages/pending proofs → drop signed proof → ACK → persist cursor.
  Failure before ACK leaves the old cursor; replay is deduplicated. An ACK failure
  can safely regenerate the same proof. A crash after ACK cannot lose the already
  persisted message. Incoming voice frames are durably retained without claiming
  playback/voice confirmation (A6).
- Polling shares the existing upstream mesh foreground-service lifecycle. While
  the Satellite UI is visible it also works without Bluetooth/location permission,
  needed for emulator acceptance. Service + Activity leases never duplicate workers.
- Separate sender loop keeps a 25-second pickup from delaying outgoing text.
  Retries back off to at most 60 seconds (or bounded Retry-After). Shutdown/settings
  cancellation closes OkHttp calls; no main-thread network wait or PoW.

## Automated evidence

Local full suite: app 869 discovered / 866 passed / 3 upstream skips; RNSLite 22/22.
17 added carrier tests, including:

- Correct stamp challenge, exact nonce validation and no box credential header.
- Excessive PoW refusal; ordinary 429 does not trigger mining.
- Day-window pickup and 25-second wait field; proof drop before ACK.
- Proof submission failure/restart/replay; no duplicate message.
- Disk save failure: no proof, ACK or cursor advance.
- Five-minute synthetic offline interval, restart and identical ciphertext retry.
- Wrong mailbox/cursor rejection; cancellation of an outstanding long poll.
- Server delivery/acceptance never substitutes for a valid peer signature.

[CI run](https://github.com/algosmd/meshstar-android/actions/runs/43019588805)
passed build/JVM tests and API 26 Keystore/screen checks (2/2). Those checks also
passed on the local ARM64 API 26 emulator. [The repeated run](https://github.com/algosmd/meshstar-android/actions/runs/43020865478)
is fully green on both API 26 and 34 with the bounded launch gate in `c4f7d25`. Mocks establish failure handling; they do not establish
live Android↔iPhone service interoperability.

Build 3 (`b8919d7`) also passed [CI](https://github.com/algosmd/meshstar-android/actions/runs/43153859877):
build/JVM suite and API 26/34, four instrumented tests per API. This adds app-link
and camera-free contact import validation.

## Live acceptance — bidirectional Internet text confirmed

On 2026-11-12, the tester confirmed build `0.3.0 (3)` exchanges text between
Android and iPhone through **Internet (mailbox)**, with ✅ at the sender in both
directions. This confirms the live text/proof round trip. No server address,
credentials, contact keys or message contents are recorded.

Offline recovery, server-side ACK cleanup and restart continuity below remain
pending live verification (their mock tests pass). Satellite/box text delivery
is a separate A2 check; this Internet result does not establish that path.

## Live acceptance checklist

1. In Settings enter Internet server on Android; the same service is configured
   on iOS. Exchange RNS contact QR codes if not already paired.
2. **Passed:** Internet text in both directions, with ✅ for both senders.
3. Disable networking for five minutes; enqueue text, then restore networking.
   Confirm queued ciphertext is retried and one copy arrives with ✅.
4. Confirm consumed server entries are empty after ACK, using the tester's own
   server-side inspection. No server credentials/address/logs are committed.
5. Restart Android and repeat: identity and contact mailboxes must remain stable.

Background longevity and vendor power restrictions require physical devices (A5/A6).
A foreground service is used when the upstream service is enabled/eligible; this
stage does not claim an always-on service without its permissions or bypass Android
power management. No notification or native Codec2 claim is made at A3.
