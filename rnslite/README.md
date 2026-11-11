# RNSLite (Kotlin/JVM)

GPL-3.0, part of MeshStar Android. Pure JVM module in `net.meshstar.rnslite`,
with no Android imports, network clients, persistence or configured endpoints.

Protocol source: meshstar commit `c2c854742af32d95c01090b9193dea327a0b65a5`,
`docs/rns-lite.md`, `docs/mailbox.md`, `core/box/voice.py` and the iOS RNSLite reference.
The golden JSON is copied byte-for-byte from `tests/vectors/rns_lite.json`;
its source commit is recorded here rather than inserting an invalid JSON comment.
SHA-256: `271cde646179664ca17f361c22c302852d61c15270acafccb3ef1d5951765f3e`.
The keys and messages in the vectors are deterministic protocol test fixtures,
not deployment credentials or real user history.

## Validation

Use JDK 21. Bytecode targets JVM 11 for the Android client.

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/meshstar/interop-requirements.txt
./gradlew :rnslite:test
```

Alternatively set `RNSLITE_PYTHON` to an existing interpreter with the pinned
oracle packages. The interoperability test fails if the oracle is unavailable;
it is never silently skipped. It starts no Reticulum daemon or interfaces.
The CI workflow runs the same test alongside all app unit tests.

Coverage: identity/address derivation, Ed25519, X25519, HKDF, authenticated tokens,
LXMF/MessagePack, RNS packets and implicit/explicit proofs, relay headers, RNSI,
MeshCore fragmentation/reassembly, textcodec with the v1 dictionary, mailbox
addresses and proof of work, voice v1, multipart/NO_PROOF/last-frame proofs.
Malformed input and resource limits are tested separately.

Codec2 bytes remain opaque in A1. The extra `codec2` fixture is checked for
framing and lossless carriage; native encode/decode conformance belongs to A6.
No audio encoder, transport or application UI is connected in this stage.

Fragment and voice assemblers must be called from one transport queue. They use
bounded state and monotonic expiration. PoW supports cancellation and refuses
remote difficulty above 24 bits. Production encryption defaults to fresh random
ephemeral keys and IVs; explicit randomness parameters exist for golden tests.
