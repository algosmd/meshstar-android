# MeshStar Android

Android client under development, based on the GPL-3.0 upstream
[permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android).
See [NOTICE](NOTICE.md) for provenance and [LICENSE](LICENSE) for the license.
The original project documentation is preserved in [README.upstream.md](README.upstream.md).

Current scope: A0 application bootstrap and A1 RNSLite protocol library.
This is **not yet** the complete MeshStar iOS feature set: box/mailbox transports,
MeshCore integration, unified chat UI and Codec2 recording are later stages.
The application keeps the upstream Bluetooth protocol and Kotlin package names.

- Application ID: `net.meshstar.app`; source namespace: `com.bitchat.android`.
- Display name: MeshStar; version: `0.3.0` (code `1`).
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

Validation reports: [A0](docs/android/A0-validation.md), [A1](docs/android/A1-validation.md).

## Upstream updates

```sh
git remote add upstream https://github.com/permissionlesstech/bitchat-android.git
git fetch upstream
```

Keep upstream changes separate from MeshStar protocol modules. Do not rename
`com.bitchat.android` or change Bluetooth wire identifiers during branding work.
