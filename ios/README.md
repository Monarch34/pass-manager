# iOS

Track B of the PassManager program. Everything under `ios/` is owned by this
track; `docs/FORMAT.md` and `docs/IOS_PARITY.md` are normative and read-only from
here.

## Layout

```
ios/
  PassVaultCore/            Swift package with two libraries.
    Sources/
      CArgon2/              Vendored phc-winner-argon2 (portable ref.c, no SSE/AVX).
      PassVaultCore/        Pure: no database, no Keychain, no UI.
        Models/             ItemCategory, ItemPayload (+ PayloadJson codec)
        Crypto/             SecureBytes, KdfParams, Argon2id, AesGcm, Hkdf
        Vault/              VaultCore — the two-key model
        PmVault/            PmVaultFile — .pmvault v1 reader/writer
      PassVaultStorage/     Persistence, on GRDB/SQLite.
        VaultDatabase       Schema + DatabaseMigrator ("v1")
        VaultStore          Repository: header projection, CRUD, metadata
        ItemCrypto          The three per-row envelopes (payload/title/address)
        VaultHeaderCache    Decrypted header cache, cleared on lock
        VaultSearch         Search over decrypted headers, never over SQL
        ImportMerge         .pmvault merge planning (plan, then apply)
    Tests/PassVaultCoreTests/
    Tests/PassVaultStorageTests/
```

No UI and no Keychain in either library — and deliberately no UIKit, because CI
runs `swift test` on the macOS host. `PassVaultCore` is kept free of the GRDB
dependency so the crypto can be reasoned about on its own.

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

The first build resolves `apple/swift-crypto` (3.x) and `groue/GRDB.swift` (6.x)
from the network; after that `.build/` is warm and offline builds work.

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
- The vault list query must never select `encrypted_data`. There is a test
  asserting that on the SQL text itself.
- `insert` takes `createdAt` and `updatedAt` separately. Android's repository
  collapses them, which is fine for a newly typed item and wrong for an import.
- Search folds case but NOT diacritics, and collapses the Turkish dotted/dotless
  I the way Java's simple case mapping does. See the comment on
  `VaultSearch.foldForSearch` for exactly where iOS and Android still differ.
