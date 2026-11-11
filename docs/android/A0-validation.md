# A0 — Android fork and build validation

Base: upstream `permissionlesstech/bitchat-android` at `14ab73c`.
Bootstrap commits: `91430d8`, `52f558e`; A1: `6cca2e8`;
launch/permission fix: `772c000`; final branding/recreation gate: `975c151`; JVM fixture: `b9de4ca`.
Repository: public `algosmd/meshstar-android`, GPL-3.0; `upstream` remote configured.

## Application

- Display name MeshStar in localized resources, replacement adaptive launcher icon.
- Application ID `net.meshstar.app`; version `0.3.0`, version code `1`.
- Kotlin/Android namespace stays `com.bitchat.android`.
- Wear is excluded in `settings.gradle.kts`.
- Nostr/geohash and Bluetooth wire formats remain upstream code; A4 will supply the MeshStar UI.
- Internal shutdown permission/action are scoped to the application ID so the fork
  does not claim the upstream app's custom permission when installed alongside it.
- Upstream release publishing and relay-refresh workflows are manual-only.
  The Android CI runs on push/PR and never publishes a Play release.
- Upstream release certificate pin is cleared for the fork; no new release key
  or signer has been configured. Debug artifacts are for testing only.

## Reproduction

JDK 21; SDK platform 37.0, build tools 37.0.0, Gradle 9.6.1.
The runtime compatibility target remains minSdk 26.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :rnslite:test
```

Install the Python oracle first as described in `rnslite/README.md`.
Strict Gradle locks and dependency checksum verification stay enabled.

## Observed results

- First app build: successful; universal debug APK published as CI artifact.
- Final app unit tests: 850 discovered, 847 passed, 3 skipped, 0 failures/errors.
- The 3 skips already exist upstream in `CommandProcessorTest` (join/unknown-command cases).
- Combined Android + RNSLite build on `b9de4ca`: successful; RNSLite 22/22.
- API 26 / API 34 launch acceptance: both passed on clean x86_64 emulators.
  Installation, initial launch, activity recreation by rotation, process survival
  and resumed app activity were verified by `tools/meshstar/emulator-smoke.sh`.
- Final code CI is green: build + emulator (26) + emulator (34).
- Actual APK metadata verified: `net.meshstar.app`, `0.3.0 (1)`, MeshStar, minSdk 26.
  APK signature verification passed. Universal APK SHA-256:
  `f352790ebbc3b058c80134b831b46d1e135d730c753ae0201bc8d7c9cda38301`.
- [Download CI APK artifact](https://github.com/algosmd/meshstar-android/actions/runs/43014610259/artifacts/13851644487).
- Final documentation also pins Python/artifact Actions to the exact SHAs used
  in the successful run, and ignores the local Python virtual environment.

CI runs:
- [Bootstrap](https://github.com/algosmd/meshstar-android/actions/runs/43011162000)
- [Diagnostic A0/A1](https://github.com/algosmd/meshstar-android/actions/runs/43012613107)
- [Final code validation](https://github.com/algosmd/meshstar-android/actions/runs/43014610259)

The first launch smoke test installed both APKs but failed its post-launch state
check. It is not counted as successful. The diagnostic run passed API 34 and
identified an upstream API 26 crash: onboarding returned an unregistered dummy
Bluetooth receiver on a device without an adapter, then tried to unregister it
while disposing the activity. The manager now returns null for that case;
cleanup handles only real registrations. Tests cover missing/present adapters
on SDK 26 and 34; the emulator gate also rotates the screen in both directions.
No BLE service identifiers, packets, discovery or transport implementation changed.

## Public-source audit

No MeshStar mailbox/bridge/Funnel endpoints, production keys, IMEI, channel
credentials or box tokens were added. `local.properties`, SDKs, environment
settings, raw device logs and local build products are outside source control.
The RNSLite vector identities are intentionally public deterministic fixtures.

The broad scan requested by the spec is **not literally empty** on the retained
upstream: 40 inherited matches. These include ordinary cryptographic variable
names/comments, generated Cashu URL syntax, workflow references, synthetic tests,
a public upstream relay entry and upstream Wi-Fi Aware's compatibility constant.
All 40 were reviewed; the A1 module adds no matches. Removing these wholesale
would conflict with the requirement to retain upstream transports. They are not
MeshStar deployment credentials. No production values were substituted.
The validation reports describe this audit; documentation is reviewed separately
from runtime configuration.

No physical Android BLE/LoRa claim is made: that acceptance belongs to A5.
The local emulator attempt was limited by available disk space; API 26/34
acceptance is performed on clean GitHub-hosted emulator instances.
