# Repository layout

Two Gradle projects, one git repo. They don’t share a root `settings.gradle.kts` — that’s the main thing to internalize.

## Repo root = Android

Open this folder in Android Studio. It loads **only** `:app`; `desktop/` is invisible to that Gradle import.

| Path | What it is |
|------|------------|
| `settings.gradle.kts` | `include(":app")` — no desktop module |
| `build.gradle.kts` | Plugin versions (`apply false`); not the Android application module |
| `gradle.properties` | Heap, AndroidX flags — safe to commit |
| `gradle.properties.example` | Copy bits to **your** `~/.gradle/gradle.properties` if Java is wrong |
| `gradle/libs.versions.toml` | Android dependency versions |
| `gradle/wrapper/*`, `gradlew*` | Wrapper for **root** / Android builds |
| `local.properties.example` | Copy → `local.properties` with `sdk.dir` (that file stays local) |
| `LICENSE`, `README.md` | Legal + project front door |

Stuff that should never be committed: `build/`, `.gradle/`, `local.properties`, and whatever else `.gitignore` lists for secrets and IDE junk.

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

## `desktop/` — Windows (and theoretically other) desktop

Think of it as a mini-repo: its own `settings.gradle.kts`, `gradle/`, `gradlew*`, and `libs.versions.toml`. None of that is wired into the Android root project.

| Path | What it is |
|------|------------|
| `desktop/build.gradle.kts` | Compose Desktop app + `nativeDistributions` (MSI, etc.) |
| `desktop/src/main/kotlin/` | UI, Ktor server, crypto glue |
| `desktop/src/main/resources/` | Fonts, static bits |

Again, `desktop/build/` and `desktop/.gradle/` are throwaway.

There’s a short [desktop/README.md](../desktop/README.md) in that folder too.

## `docs/`

Markdown only — nothing here participates in a build.

## Commands at a glance

| | |
|--|--|
| Android debug APK | From root: `./gradlew :app:assembleDebug` (`.\gradlew.bat` on Windows) |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Desktop run | `cd desktop && ./gradlew run` |

Details and release/MSI steps: **[BUILD.md](BUILD.md)**.
