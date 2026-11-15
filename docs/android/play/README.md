# Play closed-test package — 0.3.0 (8)

`listing-0.3.0-6.json` contains title, short/full description and release notes for
en-US, ru-RU, ro-RO, de-DE and fr-FR. All fields meet the 30/80/4000/500-character
limits used here. Based on read-only `meshstar/docs/store/metadata-0.1.1.json`,
adapted to implemented Android behavior; no unsupported iOS retention/wipe or
satellite delivery guarantee is advertised. French is an Android translation.

Privacy URL: https://meshstar.net/privacy
Support URL: https://meshstar.net/support
Category: Communication. No in-app purchases or advertising.
External radio hardware and satellite service are separate requirements/costs.

## Signing and artifact

- `:app:bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab`.
- Version name/code: `0.3.0` / `8`; package: `net.meshstar.app`.
  Listing copy remains in `listing-0.3.0-6.json`; builds 7–8 change permissions and carrier URL validation,
  not the advertised feature set.
- Upload signing reads private Gradle properties only:
  `MESHSTAR_UPLOAD_STORE_FILE`, `MESHSTAR_UPLOAD_STORE_PASSWORD`,
  `MESHSTAR_UPLOAD_KEY_ALIAS`, `MESHSTAR_UPLOAD_KEY_PASSWORD`.
- No signing file, password, personal path, or certificate subject belongs here.
- Upstream publishing workflow is stored as an inert `.disabled` reference;
  CI builds/tests but does not create a Play release.
- LGPL Codec2 is a separate shared library. Source, license and replacement/relink
  instructions are bundled for export from Settings. See `codec2/README.md`.

## Console checklist

1. Create/select `net.meshstar.app`, enroll in Play App Signing and register the
   upload certificate generated for this application. Back up the upload key
   privately before distribution; never put it in source control.
2. Complete app access, content rating, target audience and permissions/foreground
   service declarations truthfully. There is no demo login. Bluetooth needs two
   phones; LoRa needs companion radios; satellite needs a configured box/service.
3. Resolve `data-safety.md` and update the website policy for Android before
   submitting declarations. Do not claim public channels are E2E private chats.
4. Use actual Android screenshots (synthetic fixtures only), not the iOS images
   in the source store folder. Existing A4 image set is a basis; voice needs
   updated Android capture before a public listing advertises that screen.
5. Upload the signed AAB to a **closed** testing track, add the designated tester
   account, and use the Play opt-in link. Do not promote to production.
6. A debug-signed installation cannot be updated directly with a Play-signed one.
   Preserve/plan contact and identity migration before any uninstall; do not erase
   a live test installation merely to make the signature match.
7. Repeat the A2–A5 checks and the A6 voice/background checklist on the actual
   Play-installed build. A locally installed minified APK is useful validation
   but does not satisfy “installed from Play”.
