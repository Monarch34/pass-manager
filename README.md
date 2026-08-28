# PassManager

A password manager that keeps everything on the device it runs on.

Vault entries are encrypted with a key that never leaves memory in the clear and is unwrapped from a passphrase you choose. There is no account, no server and no sync. The only outbound request the app can make is an optional site-icon lookup, which is off until you turn it on.

## Layout

One Gradle build. The dependency arrows only point downward, and each module is defined by what it is allowed to know:

```
apps/android          Compose UI
ios/framework   ──┐   assembles PassManagerKit.xcframework for the SwiftUI app
                  │
core/vault    ────┤    storage: envelopes, search, merge
core/format   ────┤    the .pmvault container
protocol      ────┤    the desktop pairing wire contract
                  │
core/crypto   ────┘    primitives, key wrapping
core/domain            the item model
```

`core/crypto` knows nothing about items. `core/format` knows nothing about storage. Neither can grow a dependency on the other by accident, because neither can see it.

## The line between shared and native

Everything above the cryptographic primitives is one Kotlin implementation shared by every platform: how a container is framed, what its additional authenticated data covers, the order keys are wrapped in, how an import merges, how entropy is counted.

The primitives themselves are not shared. AES-GCM, X25519, HKDF and the CSPRNG come from the generator each platform already maintains and patches — the JCA provider on Android and the JVM, CryptoKit and Security.framework on Apple. Bundling one portable implementation of those would be less code and strictly worse: it would put a password manager's cryptography on something this project maintains alone, on three platforms, rather than on three that are audited and updated by their vendors.

Argon2id is the exception, because neither platform ships it. There is exactly one vendored copy of the reference implementation, built for every target.

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
