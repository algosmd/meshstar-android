# M10 regional policy — Android 0.4.0 (versionCode 14)

Branch: `codex/m10-lora`. Port of iOS 0.4.0 (7). Android uses the next monotonic
versionCode after 13; it does not reuse an older store code. No Play upload,
physical installation or live radio reconfiguration was performed in this pass.
The source repository was read only.

## Implemented

- Same `RadioPolicy.json` bytes as iOS: all 48 entries in meshcore-open `34ea0cb`,
  including 27 Russian profiles, plus MeshStar's separate narrow CR4/8 variant
  for **49 total records**. Upstream narrow CR4/5 stays unchanged. Source,
  checked date, region, countries and ham flag are retained. 433 MHz is
  CEPT-only. Community power values are not treated as legal power limits;
  existing conservative MeshStar ceilings remain where already defined.
- Country-first node setup, SIM MCC/locale suggestion, explicit confirmation,
  filtered profile list and custom editor. Full occupied channel must fit a
  verified country band. Unknown bands fail closed. No GPS request.
- Consent persists separately from credentials. Changing country or an observed
  SIM MCC revokes node approval. The confirmed profile is applied/read back
  before app-originated companion adverts are allowed; the final BLE write
  rechecks approval too. A changed in-flight choice cannot approve old settings.
- Satellite route hidden for IN/CN/RU/IR/PK/BD/EG/MM/SD/AF, localized banner.
  Send and queued text/voice posts are gated, including between voice parts.
  Relay, Internet, box setup, polling and receipt proofs remain available.
- en/ru/ro/de/fr strings, country names in the selected app language, compact
  rectangular controls. Country selection also appears in settings.
- `SECURITY.md`, source MIT attribution and store-country checklist added.
  No server address, credential, device ID or private signing data added.

## Sources and limits

Preset facts: [meshcore-open radio_settings.dart](https://github.com/zjs81/meshcore-open/blob/34ea0cb/lib/models/radio_settings.dart),
checked 2026-11-16. The array contains 48 entries despite the requested count 49.
Bands: [LoRa Alliance RP002-1.0.5 country table](https://resources.lora-alliance.org/technical-specifications/rp002-1-0-5-lorawan-regional-parameters).
The referenced regulatory-50-countries file was unavailable in the source repo.

All source entries remain in the catalog. Off-Grid 433 and 14 Russian profiles
are visible but disabled because their full occupied bandwidth exceeds the
verified bands (including the 868.731 MHz group's 868.69975 MHz lower edge).
No parameter was silently rounded or altered. Russian presets do not change
Satellite restrictions or store availability. This check does not certify
antenna gain, duty cycle, power or a specific hardware model.

## Validation

- JVM: **52 app tests** in `net.meshstar.*`, **29 MeshCore tests**, no failures.
  Includes catalog/country filtering, custom band edges, stored consent, SIM
  change, queued Satellite blocking and preserved Relay/Internet routes.
- Debug APK and minified signed release APK build successfully. R8 keep rules
  cover the policy JSON DTOs and stored confirmation state.
- Synthetic emulator: **5 instrumented tests passed** (regional UI plus existing
  MeshCore settings tests). Satellite is absent while Relay/Internet remain.
  Existing occupied-slot confirmation and radio apply/readback UI tests pass.
- Wizard screenshots in five locales and two themes were generated and reviewed
  locally. Raw screenshots, runtime logs and artifacts remain outside Git per
  `AGENTS.md`. No physical location or device metadata was collected.
- The catalog was byte-compared with iOS; all 48 upstream radio parameter tuples
  and all 27 Russian records are preserved.

## Physical acceptance pending

This does not replace Mesh Lab validation on physical devices. With the owner's
chosen country: confirm profile, read back radio and send advert; change country
and confirm that advert stays blocked until approval/readback; check Relay,
Internet and incoming notifications with Satellite hidden. Check SIM-country
change where Android exposes the carrier hint. No real RF settings were changed.

Firmware boot/periodic adverts are autonomous and cannot be suppressed before
BLE connects. The guard controls app-issued advert commands. SIM/locale hints
are refreshed on foreground entry; the user must confirm their country.

Catalog SHA-256: `786c1e3f3f26ec4b8aa396ba29483f40c1af1e9e29a66cb00f503da5d2e0d906`. Full source audit: 48/48 community radio tuples and source power values match the pinned upstream file.
