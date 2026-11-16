# M10 regional policy — Android 0.4.0 (versionCode 15)

Branch: `codex/m10-lora`. Port of iOS 0.4.0 (8). Android uses the next monotonic
versionCode after 14; it does not reuse an older store code. No Play upload,
physical installation or live radio reconfiguration was performed in this pass.
The source repository was read only. Android build 14 and all M10 reports were pushed to `origin/codex/m10-lora` at `075a803` before this change.

## Implemented

- Same `RadioPolicy.json` bytes as iOS: all 48 entries in meshcore-open `34ea0cb`,
  including 27 Russian profiles, plus MeshStar's separate narrow CR4/8 variant
  for **49 total records**. Upstream narrow CR4/5 stays unchanged. Source,
  checked date, region, countries and ham flag are retained. 433 MHz is
  CEPT-only. Community power values are not treated as legal power limits;
  existing conservative MeshStar ceilings remain where already defined.
- Country-first node setup, SIM MCC/locale suggestion, explicit confirmation,
  filtered profile list and custom editor. Custom profiles must fit a verified
  country band across the full occupied width; unknown bands fail closed for
  custom input. Community profiles outside the table require a country-specific
  warning and explicit confirmation. No GPS request.
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

All source entries remain in the catalog. Off-Grid 433 and affected Russian
profiles are selectable with a warning, not disabled. The warning names the
country and explains that this is a community preset used at the operator's
responsibility. Cancel makes no change; explicit confirmation supplies a scoped
exception. Custom edits do not inherit an exception even with identical numbers.
Stored consent binds country, preset ID and radio parameters; country/MCC changes
invalidate it. Readback and the final advert gate accept only the scoped profile.
No radio parameter was silently rounded or altered. Country/ham filtering and
Satellite restrictions are unchanged. This check does not certify antenna gain,
duty cycle, power or a specific hardware model.

RU uses three subbands: **864–865, 866–868, 868.7–869.2 MHz**, each with `source`, document identifier and checked date. Source: [GKRCh 07-20-03-001, Appendix 1, revision 2023-09-27](https://internet-law.ru/documents/dop_documents/11/gost_92756/0/reshe.html) (published decision text mirror). Other country bands retain the source below.

## Validation

- JVM: **53 app tests** in `net.meshstar.*`, **29 MeshCore tests**, no failures.
  Includes catalog/country filtering, custom band edges, stored consent, SIM
  change, queued Satellite blocking and preserved Relay/Internet routes.
- Debug APK and minified signed release APK build successfully. R8 keep rules
  cover the policy JSON DTOs and stored confirmation state.
- Build 15: **3 regional instrumented tests passed**. The new test exercises warning, cancellation and explicit confirmation in all five locales; cancellation sends no action, confirmation carries the exact preset ID and acknowledgement. Satellite hiding and wizard rendering also pass. Screenshots of all five warnings were inspected locally. Build 14 baseline: 5 instrumented regional/node UI tests passed.
- Build 14 wizard screenshots in five locales and two themes were generated and reviewed
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

Catalog SHA-256: `7085877d1885a7e7848e832744ad645292549ae433babc387ba31e4f16462b89`. Full source audit: 48/48 community radio tuples and source power values match the pinned upstream file.
