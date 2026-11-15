# Android M10 — implementation handoff

A6 `090ac65` is on `main`. M10 work is on `codex/m10-lora` and has not been
published to Play or merged to main.

| Stage | Version | Android code | Implementation commit | Report |
| --- | --- | --- | --- | --- |
| P1.0 channels and batch 21 | 0.4.0 | 9 | `35b6d55`, `613e321`, `8aa9e75` | [P1.0](M10-P1.0-validation.md) |
| P1.1 companion settings | 0.4.0 | 10 | `72143ed` | [P1.1](M10-P1.1-validation.md) |
| P1.2 repeater admin | 0.4.0 | 11 | `26b6f93` | [P1.2](M10-P1.2-validation.md) |
| P1.3 paths and trace | 0.4.0 | 12 | `5351174` | [P1.3](M10-P1.3-validation.md) |
| P1.4 OSM map and final integration | 0.4.0 | 13 | `03e2e63` | [P1.4](M10-P1.4-validation.md) |

Codes remain monotonic after A6 (8). A separate local APK was retained for each
stage. Use the final code 13 for remaining acceptance after completing the first
channel check on the installed code 9.

Final JVM suite: app 899 tests (three existing skips), MeshCore 28, RNSLite 22;
zero failures/errors. Instrumented UI/compatibility checks passed on an isolated
API 26 emulator, including 100 synthetic screen captures in five locales,
light/dark and 320/427 dp widths. No real-device coordinates or private data used.

Lint is not clean: 873 errors, one fatal diagnostic and 355 warnings are reported
under the repository's non-failing lint configuration. The fatal diagnostic is
`SpecifyForegroundServiceType` in the existing APK download worker, outside M10.
Most errors concern existing translations and permissions. No lint errors were
reported for the new map, radio, admin, hashtag or path UI/protocol files. Missing
lint dependency-lock configurations were completed without upgrading libraries.
This result does not certify release readiness.

Physical P1.0 hashtag exchange/leave was confirmed by the user on 2026-11-15:
channel opening, text in both directions and slot release passed on code 9.
Occupied-slot replacement and badges still need hardware confirmation. Remaining
M10 stages are pending live acceptance; companion/repeater changes require the
user's confirmation. The stage reports contain the remaining checklist.
