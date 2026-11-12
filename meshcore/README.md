# MeshCore companion protocol

Pure Kotlin/JVM command queue, Nordic UART frame parsing and RNS fragment session.
Android scanning/GATT lives in `app/src/main/java/net/meshstar/meshcore`.

- One frame per BLE write/notification; no serial framing wrapper.
- APP_START, device/channel discovery, explicit occupied-slot replacement, contacts
  and adverts, SEND_CHANNEL_DATA (0x3E, data type 0xFFFF), datagram receive (0x1B),
  MSG_WAITING and destructive SYNC_NEXT_MESSAGE drain.
- A single outstanding command; unsolicited pushes never satisfy its reply.
  Timeout/cancellation invalidates the connection so late replies cannot leak into
  the next command. Contact streams use an inactivity timeout.
- Complete incoming packets/native text are handed to storage before the next
  queue read. RNS fragments use the unchanged `:rnslite` assembler.
- At least 1.5 seconds between radio submissions, including packet fragments,
  proofs, native text and adverts. A full TX queue gets bounded backoff.
- Only an empty slot may be configured automatically. Occupied/mismatched slots
  require a new explicit replacement action and a valid channel secret.
- MESHSAT is a transport, never an ordinary text channel.
- Node/contact location fields are skipped and never retained. No raw-frame
  logging, baked-in server addresses or production credentials.

`NodeConfiguration` is stored only inside the app's encrypted state. UI channel
rows omit channel secrets. Synthetic unit tests run with `./gradlew :meshcore:test`.

Reference behavior: the read-only iOS MeshCore implementation; protocol overview:
[official companion protocol](https://github.com/meshcore-dev/MeshCore/blob/main/docs/companion_protocol.md).
Physical acceptance and limitations are in [A5](../docs/android/A5-validation.md).
