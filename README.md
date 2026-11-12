# MeshStar Android

Android client under development, based on the GPL-3.0 upstream
[permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android).
See [NOTICE](NOTICE.md) for provenance and [LICENSE](LICENSE) for the license.
The original project documentation is preserved in [README.upstream.md](README.upstream.md).

Current scope: A0/A1 bootstrap and protocol library, A2 encrypted RNS identity,
contacts/QR and text through a box, A3 Internet mailbox text with signed delivery,
and A4 unified chats with English, Russian, Romanian, German and French UI.
The main screen has **Bluetooth / LoRa / Satellite** tabs; use the header QR or
**+** for contacts/settings, or tap **MeshStar/@nickname** to open settings.
This is **not yet** the complete MeshStar iOS feature set: MeshCore integration
is A5 and recording/media/notification work is A6. LoRa and Relay are visible
route choices but remain unavailable until a MeshCore transport is implemented.
The application keeps the upstream Bluetooth protocol and Kotlin package names.

- Application ID: `net.meshstar.app`; source namespace: `com.bitchat.android`.
- Display name: MeshStar; version: `0.3.0` (code `4`).
- Wear is excluded from Gradle settings.
- MeshStar protocol code is isolated in pure JVM module [`:rnslite`](rnslite/README.md).
- No MeshStar deployment endpoints or credentials are configured or bundled.

## Build and test

Use JDK 21 and the SDK/build tools pinned in `gradle/libs.versions.toml`.
Set `sdk.dir` in your ignored `local.properties` or use `ANDROID_HOME`.

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/meshstar/interop-requirements.txt
./gradlew :app:assembleDebug :app:testDebugUnitTest :rnslite:test
```

The GitHub Actions workflow builds a universal debug APK, runs app/JVM tests and
launches the APK on API 26 and 34 emulators. APK and JUnit XML are CI artifacts.
Debug APKs are test builds, not signed Play releases. BLE interoperability must
still be tested on physical devices in A5; emulators cannot establish that result.

Validation reports: [A0](docs/android/A0-validation.md), [A1](docs/android/A1-validation.md),
[A2](docs/android/A2-validation.md), [A3](docs/android/A3-validation.md),
[A4](docs/android/A4-validation.md).

Carrier URLs default to empty and are entered by the tester in the app. Credentials
and history are encrypted using a non-exportable Android Keystore wrapping key;
RNS identity stays independent of the upstream BLE Noise identity. HTTP is allowed
for explicitly configured boxes; HTTPS uses normal platform trust. Redirects are
rejected. The app never logs carrier requests or embeds deployment addresses.

**My QR** shows the existing `meshsat1:` RNS contact payload for iOS/box compatibility.
**Copy QR** and **Share** export that contact payload. Input accepts
`meshstar://contact`, legacy `meshsat://contact`, and raw RNS contacts. Box credentials
can be entered manually or imported from a `meshstar://box` setup link and must be
explicitly saved before use. Existing iOS box-contact QR payloads contain a public
identity, not a server URL or bearer credential.

`release.yml` and `fetch-georelays.yml` are gated to the upstream repository.
They cannot publish, attest or push changes in this fork. CI only builds test APKs.

## Upstream updates

```sh
git remote add upstream https://github.com/permissionlesstech/bitchat-android.git
git fetch upstream
```

Keep upstream changes separate from MeshStar protocol modules. Do not rename
`com.bitchat.android` or change Bluetooth wire identifiers during branding work.
