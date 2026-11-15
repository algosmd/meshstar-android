# Codec2 1.2.0 for Android

Upstream: https://github.com/drowe67/codec2, tag 1.2.0, commit
`06d4c11e699b0351765f10398abb4f663a984f36` (same source as the iOS reference).
Archive SHA-256: `a78184eb3510de152ee225a0716d9a72f8f4bdbe79031783c5bbd84a9787d846`.

Unchanged speech DSP and codebooks are compiled as **libcodec2.so**, a shared
LGPL-2.1 library. The small JNI bridge is a separate shared object. KISS FFT keeps
its BSD license. No Codec2 object files are statically linked into the application.
The Gradle module pins NDK/CMake, uses `-O2 -ffp-contract=off`, generates codebooks
with the upstream generator and builds all four app ABIs with 16 KB alignment.

Build offline after installing the pinned SDK/NDK dependencies:

```sh
./gradlew :codec2:assembleRelease
```

`prepare.py` extracts the included full source archive to the ignored build tree.
No upstream DSP modifications are applied. All JNI input is bounded; only mode
1200 is exposed. One codec instance processes an entire note, including padding
of its final PCM block. Independent calls are serialized for upstream DSP state.

The app offers license text and export of this complete source archive, generated
build recipe and JNI source in Settings. Modified libraries may be rebuilt,
replaced in an APK, and the APK signed with a user's own key. Reverse engineering
for debugging modifications of this library is permitted. See `upstream/COPYING`
and `upstream/KISS-FFT-COPYING`; the component is supplied without warranty.
