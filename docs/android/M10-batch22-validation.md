# Batch 22 — Android 0.4.0 (versionCode 16)

Branch: `codex/m10-lora`. Source: `meshstar/docs/codex/app-feedback.md`, master, batch 22 (read only, 2026-11-16).

## Implemented

1. Incoming text and assembled voice from the HTTP box snapshot `link.online` at reception and display antenna → globe / “via point”. The saved label does not change when the box later goes offline. Mailbox traffic keeps its separate globe label.
2. Automatic LoRa routing and the bridge-proof heuristic use authenticated radio observations with SNR, expiring after five minutes. HTTP inbox/history and mailbox delivery cannot establish direct radio reachability. A bridge proof cannot manufacture its own evidence. The previous 24-hour cache is not used to select Auto.
3. Auto offers Internet only after authenticated mailbox reception (message, voice or valid delivery proof). Capability persists across restarts; legacy verified mailbox history is recognized. A successful drop alone does not establish capability. Explicit Internet selection remains available for the first exchange, including when it is the only configured RNS path. Existing QR formats contain no mailbox capability flag; no QR/wire-format extension was invented.
4. Internet messages still lacking confirmation after ten minutes stop retrying the mailbox. An Auto message may move to currently available direct LoRa with recent radio evidence; the original ciphertext/hash and voice frames are preserved. Otherwise the UI says “recipient is not on Internet · choose another route”. No automatic paid fallback is introduced. Explicit Internet requests are not silently rerouted. Late valid proofs still resolve the original message and clear the warning; stale HTTP replies cannot overwrite its new route. NO_PROOF voice is excluded from the receipt timeout.
5. Voice Auto uses proven direct LoRa, a confirmed mailbox, or the HTTP box. It never sends voice through Relay. With no suitable route it displays the localized box/Wi-Fi hint and does not enqueue voice for radio. Explicit direct-LoRa selection remains available.

New hints are translated into English, Russian, Romanian, German and French. RNS formats, encryption, channel secrets and BLE mesh identifiers are unchanged.

## Live acceptance still required

- Receive text and voice from a box with `link.online`, verify antenna → globe and signature status; disable the link and confirm older labels remain unchanged.
- For a contact heard only through the HTTP box, Auto must not choose direct LoRa or an unproven Internet mailbox. Voice must use HTTP (or a proven mailbox), or show the hint without transmitting radio frames.
- Bootstrap a mailbox exchange with manual Internet, receive a valid reply/proof, then verify Auto can use it after restarting.
- With no receipt for more than ten minutes, verify same-packet direct-LoRa fallback only when recent radio evidence exists; otherwise verify the offline-recipient label. Deliver a late proof and check ✅ replaces that label.

No physical radio settings were changed, no devices updated and no store upload performed in this task. Raw logs and synthetic test artifacts remain local.

## Automated validation

56 app JVM tests and 29 MeshCore tests passed. Debug and signed/minified Release APKs built successfully.
