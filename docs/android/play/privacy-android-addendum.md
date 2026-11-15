# Android privacy policy update for the website

Suggested replacement / addition for the public privacy page. This is a draft;
it has not been published to the website. Keep the existing iOS-specific details
under a separate iOS heading.

## Platform and publisher

MeshStar is a messenger for iPhone and Android. The publisher is the developer
shown on the relevant App Store or Google Play listing. Contact:
support@meshstar.net.

## Android storage and permissions

On Android, the RNS identity, RNS contacts, RNS history and transport settings are
encrypted locally using a non-exportable Android Keystore key. App backups are disabled.
The app does not require an account, telephone number or email address.

Bluetooth and nearby-device permissions support phone-to-phone communication and
MeshCore companion radios. On Android versions that require it for Bluetooth or
Wi-Fi discovery, the app may also request location permission. MeshStar does not
use this permission to collect geographic coordinates or share your location.
Camera access is used for local QR scanning; microphone access is used for
recordings you initiate. QR decoding is performed locally, without a network
scanning service. Android battery and notification settings affect background
message reception.

Private RNS messages and voice notes are end-to-end encrypted. Public Bluetooth
messages and ordinary MeshCore channels are visible to their participants.

## Optional internet mailbox

The internet-server address is empty by default. If you configure a mailbox,
it stores encrypted packets until pickup/acknowledgement or expiry. The operator
cannot read their encrypted content. Routing identifiers, packet sizes and request
timing are processed to deliver messages and prevent abuse. The developer's
mailbox uses a daily-key HMAC of the connecting IP address for in-memory limits,
with access logging disabled; raw IP addresses are not retained by this service.
Third-party operators may have their own retention policies.

## Developer-operated demo gateway and relay

Our demo gateways and relay keep operational logs containing token names or
recipient-address hashes, request types, timestamps and sizes for at most 30 days.
We use these technical identifiers to provide and troubleshoot delivery, not for
advertising, profiling or sale to third parties. The logs do not contain readable
private message or voice content.
Our demo gateways and relay are accessed through HTTPS or a protected VPN. The
app also supports user-configured servers; their transport security and policies
are determined by their operators.

## Request deletion of server logs

To request deletion of data held by a demo gateway or relay we operate, email
support@meshstar.net with the subject “MeshStar data deletion”. Identify the
service and approximate dates, and provide the relevant public identifier or
token name if known. Do not send private keys, secret tokens or message content.
We use this information only to locate and remove the associated operational
records. Logs otherwise expire within 30 days. For a server operated by someone
else, contact that operator. There is no user account to delete.

Removing the Android app erases its local app storage. Server log deletion is a
separate request; deleting a local chat or uninstalling the app does not itself
contact the server to remove logs.

## Platform-specific features

The existing “Report” email action and triple-tap emergency wipe descriptions
apply to iOS where implemented. Do not describe them as Android features in
0.3.0. Android Bluetooth blocking is available through the /block command.

## Review before publishing

- Keep Android and iOS behavior clearly separated, especially Keychain/Keystore,
  retention UI, emergency wipe and Report.
- Replace “no third-party SDKs” with the accurate claim “no advertising or analytics
  SDKs are enabled”; the Android app does use third-party software libraries.
- The operator confirmed HTTPS/VPN-only access for developer-operated services;
  do not extend that claim to every user-configured HTTP server.
- Set the actual publication date when the page is updated.

## Follow-up after the November 13 website update

The Android/mailbox/deletion sections are now published. Correct the remaining
statements with the following wording:

- Summary: “No advertising or analytics SDKs are enabled. The app uses
  third-party libraries for functions such as Bluetooth, QR decoding and audio.”
- Permissions: “On Android, nearby-device and location permissions support
  Bluetooth discovery, including background discovery where required by the
  operating system. MeshStar does not use these permissions to read, store or
  share geographic coordinates.” The current Android onboarding still requests
  location permissions on Android 12 and later; do not claim it uses only Nearby
  devices there.
- Retention: “On iPhone, you can choose how long each chat is kept. Expired
  messages are deleted locally.” Do not advertise this UI for the Android build.
- Emergency wipe: “On iPhone, the triple-tap emergency wipe erases keys,
  contacts, history and settings.” In Your choices, qualify emergency wipe with
  “where available”; uninstalling remains an Android local-data deletion option.

These are website text corrections, not changes to the app or its permissions.
