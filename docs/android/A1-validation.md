# A1 — RNSLite JVM validation

Protocol source: meshstar `c2c854742af32d95c01090b9193dea327a0b65a5`.
Implementation: `6cca2e8`; additional signed malformed-voice rejection cases in `772c000`.

## Scope

Pure Kotlin/JVM module `:rnslite`, package `net.meshstar.rnslite`; no Android imports,
network transport, persistence, endpoint or production credentials. JDK 21,
JVM 11 bytecode, BouncyCastle 1.85. Strict dependency locks and SHA-256 verification
remain enabled. All 11 newly pinned Kotlin artifacts were also independently
downloaded from Maven Central and matched against their SHA-256 pins. `meshstar` and the iOS reference were not modified.

## Local validation

```sh
python -m pip install -r tools/meshstar/interop-requirements.txt
./gradlew :rnslite:test
```

22 tests: 22 passed, 0 failed, 0 skipped. This includes 14 vector/provenance tests,
7 negative/resource-limit tests and 1 real Python interoperability test.
The final local run did not use `--write-locks` or disable verification.

| Vector section | Validation |
| --- | --- |
| version / rns | Version 1 and RNS 1.5.7 metadata checked |
| identities / name_hash_lxmf_delivery | Both private/public identities, hashes and delivery addresses |
| fixed / hkdf / encrypt / sign | Fixed IV/ephemeral key; derived keys, ciphertext and Ed25519 bytes |
| lxmf | MessagePack, message hash/signature, packed message, raw packet and proof |
| lxmf.via_transport | Header 2 bytes and preserved packet hash |
| rnsi | Full frame, type, destination and truncated hash |
| meshcore | Exact fragments for both MTUs; duplicate/out-of-order assembly |
| textcodec | Every sample, exact v1 dictionary, raw deflate and both character encodings |
| mailbox | Both directions, UTC three-day pickup window, public address and PoW nonce/digest |
| voice | Exact encrypted frame, inner bytes, signature and payload limit |
| voice_multipart | Every frame, part indices, NO_PROOF, assembly, last-frame hash and proof |
| codec2 | Fixture metadata and lossless encrypted carriage; native encoding is A6 |

The JSON is copied byte-for-byte. Its provenance is in `rnslite/README.md`, and
`rns_lite.sha256` is asserted by a test. No vectors were rewritten to match Kotlin.

## Independent interoperability

The JUnit test creates fresh ephemeral test identities and ciphertext. Python
RNS 1.5.7 / LXMF 0.9.2 unpacks Kotlin's packet, decrypts it, validates its LXMF
signature/fields and confirmation. Python then produces a signed encrypted
reply; Kotlin validates the full packet, hash, message and Python confirmation.
The test uses the actual reference libraries, no HTTP mocks or network daemon.
Only Reticulum's daemon usage-accounting callback is bypassed during lookup.

## Fail-closed cases

Wrong identity, modified ciphertext/MAC/signature, unknown sender, mismatched
sender address, malformed packet/MessagePack/fragment, unsupported dictionary,
truncated deflate, output expansion limit, invalid voice flags/parts, overlong
voice note, explicit proof hash mismatch, wrong last-frame confirmation, expired
or conflicting fragments, excessive/cancelled PoW are rejected.

## CI

[CI run for `b9de4ca`](https://github.com/algosmd/meshstar-android/actions/runs/43014610259):
Android assembly and all JVM tests passed. RNSLite: 22/22, zero skipped. App:
850 discovered, 847 passed and 3 pre-existing skipped tests. Test XML is available
in the `unit-test-results` artifact. API 26/34 launch results are in the A0 report.
