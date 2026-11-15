# Permission declarations — draft pending demonstration video

## App purpose (500-character field)

MeshStar is a messenger for Bluetooth mesh, MeshCore LoRa radios, satellite gateways and an optional Internet mailbox. Users exchange text and voice without an app account. Private RNS messages are end-to-end encrypted; public radio/Bluetooth channels are shared conversations. External radio and satellite hardware is configured by the user.

## Background permission purpose (500-character field)

Receive Bluetooth mesh messages while the app is minimized or the screen is off. Android 10/11 require background location permission for background BLE discovery. MeshStar uses this permission to discover nearby Bluetooth peers, not to read, store or share geographic coordinates. The onboarding explanation appears before the permission request; users can skip it. A visible foreground notification indicates active mesh operation.

## Foreground services

- CONNECTED_DEVICE: ongoing Bluetooth communication with nearby phones and a
  paired MeshCore radio, including incoming messages while the screen is off.
- LOCATION: other use; BLE discovery supporting background mesh reception.
  No geographic location tracking is advertised or collected. The current
  service requests this type only when the corresponding permission is granted.
- DATA_SYNC: removed from build 7. The inherited APK downloader is disabled at
  scheduling and execution, including work persisted by an older build.
- Photo/video library: no broad library permissions in build 7. Attachments use
  Android content/document pickers and grants for individually selected URIs.

## Video needed before submission

Use an owner-provided YouTube link accessible to reviewers without login. Show
the Android permission explanation, Bluetooth-mesh enablement, MeshCore pairing,
backgrounding the app and delivery of an incoming message/notification. Google
recommends a short background-permission demonstration (about 30 seconds).
Do not expose server URLs, credentials, real conversations or contact identifiers.
No demonstration link or successful permission approval is claimed yet.
