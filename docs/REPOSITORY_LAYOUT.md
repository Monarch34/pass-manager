# Repository layout

The repository has **two** application Gradle builds (Android root + `desktop/`) and a **`protocol/`** library included by **composite build** from both.

## Repository root (Android)

Opening the repository root in Android Studio imports **`:app`** only. `desktop/` is not part of that project.

| Path | What it is |
|------|------------|
| `settings.gradle.kts` | `include(":app")` + `includeBuild("protocol")` with dependency substitution |
| `build.gradle.kts` | Plugin versions (`apply false`); not the Android application module |
| `gradle.properties` | Heap, AndroidX flags — safe to commit |
| `gradle.properties.example` | Optional snippets for the user’s `~/.gradle/gradle.properties` |
| `gradle/libs.versions.toml` | Android dependency versions |
| `gradle/wrapper/*`, `gradlew*` | Wrapper for **root** / Android builds |
| `local.properties.example` | Copy → `local.properties` with `sdk.dir` (that file stays local) |
| `LICENSE`, `README.md` | License and project overview |

Do not commit: `build/`, `.gradle/`, `local.properties`, and paths excluded by `.gitignore` (secrets, IDE files, etc.).

## `app/` — the phone app

| Path | What it is |
|------|------------|
| `app/build.gradle.kts` | The actual Android `application` module |
| `app/proguard-rules.pro` | R8 rules for release |
| `app/schemas/` | Room exported JSON — **do** commit when the schema changes |
| `app/src/main/` | Kotlin, resources, manifest |
| `app/src/test/` | JVM unit tests |
| `app/src/androidTest/` | Instrumented tests |

`app/build/` is generated; ignore it.

## `protocol/` (shared pairing messages)

Kotlin JVM module: `HandshakeRequest` / `SecureRequest` / `SecureResponse` / `PairingQrPayload`, and `SecureMessageCbor`. Built when you build `:app` or desktop; sources live only here (not duplicated under `app/` or `desktop/`).

| Path | What it is |
|------|------------|
| `protocol/settings.gradle.kts` | Standalone settings; `rootProject.name = "passmanager-protocol"` |
| `protocol/build.gradle.kts` | `kotlin("jvm")`, kotlinx-serialization CBOR; `group` / `version` set the Maven coordinates |

## `desktop/` (desktop application)

Separate Gradle project: `settings.gradle.kts`, `gradle/`, `gradlew*`, and `libs.versions.toml`. Includes `includeBuild("../protocol")` like the Android root.

| Path | What it is |
|------|------------|
| `desktop/build.gradle.kts` | Compose Desktop app + `nativeDistributions` (MSI, etc.) |
| `desktop/gradle.properties.example` | Optional `org.gradle.java.home` for `packageMsi` (full JDK; copy to `gradle.properties`, gitignored) |
| `desktop/src/main/kotlin/` | UI, Ktor server, crypto glue |
| `desktop/src/main/resources/` | Fonts and static resources |

`desktop/build/` and `desktop/.gradle/` are build outputs (gitignored).

See [desktop/README.md](../desktop/README.md).

## `docs/`

Documentation only; not part of any Gradle build.

## Build commands

See **[BUILD.md](BUILD.md)**.
