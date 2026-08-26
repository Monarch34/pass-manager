# iOS

Track B of the PassManager program. Everything under `ios/` is owned by this
track; `docs/FORMAT.md` and `docs/IOS_PARITY.md` are normative and read-only from
here.

## Layout

```
ios/
  PassVaultCore/            Pure Swift package: crypto, models, .pmvault container.
    Sources/
      CArgon2/              Vendored phc-winner-argon2 (portable ref.c, no SSE/AVX).
      PassVaultCore/
        Models/             ItemCategory, ItemPayload (+ PayloadJson codec)
        Crypto/             SecureBytes, KdfParams, Argon2id, AesGcm, Hkdf
        Vault/              VaultCore — the two-key model
        PmVault/            PmVaultFile — .pmvault v1 reader/writer
    Tests/PassVaultCoreTests/
```

No UI, no persistence, no Keychain in this package — and deliberately no UIKit,
because CI runs `swift test` on the macOS host.

## Building and testing on a Mac

Requires Xcode 15 or a Swift 5.9+ toolchain. From `ios/PassVaultCore`:

```sh
swift build
swift test
```

To run one suite or one test:

```sh
swift test --filter PmVaultFileTests
swift test --filter Argon2idTests/testPublishedReferenceVector
```

To work on it in Xcode, just `open Package.swift` from the same directory.

The first build resolves `apple/swift-crypto` (3.x) from the network; after that
`.build/` is warm and offline builds work.

CI runs the same `swift test` on `macos-14` for every push to `ios-app` — see
`.github/workflows/ios.yml`.

## About the vendored Argon2

`Sources/CArgon2` is the reference `phc-winner-argon2` C implementation, taken
unmodified from the upstream repository. It is vendored rather than pulled from
libsodium because libsodium's `crypto_pwhash` supports `p=1` only, and
`docs/FORMAT.md` pins `p=4`.

Only the portable `ref.c` is compiled — never the SSE/AVX variants — so the same
bytes come out on every architecture the app ships to. `include/argon2.h` is the
sole public header; `include/module.modulemap` exposes it to Swift as `CArgon2`.

`Sources/PassVaultCore/Crypto/Argon2id.swift` is the only thing allowed to touch
that C API. Its tests pin the output against the published Argon2id reference
vector (v=19, m=65536, t=2, p=1, `"password"` / `"somesalt"`), so a bad vendoring
job fails loudly rather than silently deriving different keys than Android.

## Interop invariants worth not breaking

- Payload JSON omits fields that carry the Kotlin default, and always writes `id`
  and `title`. That is `encodeDefaults = false` parity.
- Forward slashes must not be escaped; Foundation does that by default and
  `PayloadJson` turns it off.
- AES-GCM output is `ciphertext || tag` with the nonce carried separately, which
  is what JCA produces and what `docs/FORMAT.md` specifies.
- A `.pmvault` reader validates the whole header before deriving anything.
