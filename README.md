# PassManager

A password manager that keeps everything on the device it runs on.

Vault entries are encrypted with a key that never leaves memory in the clear and is unwrapped from a passphrase you choose. There is no account, no server and no sync.

Neither application makes any outbound request. The Android manifest declares no permissions at all, `INTERNET` included, so the phone app is incapable of one; the iOS app links Foundation, PDFKit and LocalAuthentication and nothing that speaks to a network. A vault leaves the device only as a file you export on purpose.

## Layout

One Gradle build. The dependency arrows only point downward, and each module is defined by what it is allowed to know:

```
apps/android          Compose UI
ios/framework   ──┐   assembles PassManagerKit.xcframework for the SwiftUI app
                  │
core/vault    ────┤    the open vault: search, merge, export, the blob store
core/format   ────┤    the .pmvault container
                  │
core/crypto   ────┘    primitives, key wrapping
core/domain            the item model, password strength and generation
```

`core/crypto` knows nothing about items. `core/format` knows nothing about storage. Neither can grow a dependency on the other by accident, because neither can see it.

## The line between shared and native

Everything above the cryptographic primitives is one Kotlin implementation shared by every platform: how a container is framed, what its additional authenticated data covers, the order keys are wrapped in, how an import merges, how entropy is counted.

The primitives themselves are not shared. AES, HMAC-SHA-256 and the CSPRNG come from the implementation each platform already maintains and patches — the JCA provider on Android and the JVM, Security.framework and CommonCrypto on Apple. Bundling a portable AES would be less code and strictly worse: it would put a password manager's block cipher on something this project maintains alone, on three platforms, rather than on three that are audited and updated by their vendors.

Two things sit above that line because no platform offers them, and both are pinned by published test vectors that run on all three targets:

- **Argon2id**, and the BLAKE2b it is defined in terms of. There is no Argon2 in the JCA, none in CommonCrypto and none in CryptoKit. The alternative to one shared implementation was three different ones — a JNI binding on Android, a pure-Java one on the desktop, vendored C on Apple — that would have to agree byte for byte forever, on a vault that a phone writes and a desktop opens.
- **GCM's authenticator, on Apple only.** CryptoKit has AES-GCM whole and is Swift-only, which Kotlin/Native cannot bind; CommonCrypto's GCM entry points are not in the iOS SDK's public headers. So on Apple the mode is assembled from Apple's AES and a GHASH implemented here. The block cipher — the part that is genuinely dangerous to write in software — stays the platform's everywhere.

Running the same vectors on every target is also what proves the three agree with each other, which is the property a vault moving between them depends on.

## Building

Requires a JDK 17 or newer and the Android SDK, located through `ANDROID_HOME` or a `local.properties` you create yourself — that file is deliberately not in the repository, because it holds a path that is only true on one machine.

```bash
./gradlew build
```

That compiles and tests the JVM and Android targets, and type-checks the iOS source sets — Kotlin/Native compiles Apple `.klib`s on any host. Linking Apple binaries, running simulator tests and assembling the framework need macOS and Xcode, and are verified in CI.

```bash
./gradlew assemblePassManagerKitXCFramework   # macOS only
```

## Versions

The Kotlin, Gradle and Android Gradle Plugin versions in `gradle/libs.versions.toml` are not independent choices. They are the single point where three published compatibility tables intersect, and moving one moves the others. The comment in that file shows the arithmetic.
