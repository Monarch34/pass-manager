# Repository layout

This monorepo holds **two independent Gradle projects** (Android + desktop). They do **not** share a single `settings.gradle.kts`.

---

## 1. Repository root — Android Gradle project

These files configure **only** the Android app (`:app`). Opening the repo root in Android Studio loads this project.

| Path | Role |
|------|------|
| `settings.gradle.kts` | Includes `:app` only. **Does not** include `desktop/`. |
| `build.gradle.kts` | Root **plugin** versions (`apply false`); no Android `application` plugin here. |
| `gradle.properties` | JVM heap, AndroidX, Kotlin style — **safe to commit** (no machine paths). |
| `gradle.properties.example` | Copy snippets to **user** `~/.gradle/gradle.properties` if JDK is wrong — see comments inside. |
| `gradle/libs.versions.toml` | Version catalog for **Android** dependencies. |
| `gradle/wrapper/*` | Gradle Wrapper for the **Android** project. |
| `gradlew`, `gradlew.bat` | Run Android builds from repo root. |
| `local.properties.example` | Template for **`local.properties`** (SDK path) — copy to repo root; file is gitignored. |
| `LICENSE` | Project license. |
| `README.md` | Product overview, quick start, protocol & security. |

**Generated / local (never commit):** `build/`, `.gradle/`, `local.properties`, `.idea/` (optional ignore).

---

## 2. `app/` — Android application module

| Path | Role |
|------|------|
| `app/build.gradle.kts` | Android application plugin, dependencies, `compileSdk`, etc. |
| `app/proguard-rules.pro` | R8 / ProGuard rules for **release**. |
| `app/schemas/` | Room **exported** JSON schemas (`exportSchema = true`) — commit these. |
| `app/src/main/` | Production code, resources, manifest. |
| `app/src/test/` | JVM unit tests. |
| `app/src/androidTest/` | Instrumented tests. |

**Generated:** `app/build/` (gitignored).

---

## 3. `desktop/` — Desktop Gradle project (separate)

Treat `desktop/` as its **own** repository-style project: own wrapper, own catalog, own `settings.gradle.kts`.

| Path | Role |
|------|------|
| `desktop/settings.gradle.kts` | Desktop root project name & config. |
| `desktop/build.gradle.kts` | Compose Desktop / JVM application. |
| `desktop/gradle/libs.versions.toml` | Version catalog for **desktop** (independent from root). |
| `desktop/gradle/wrapper/*` | Gradle Wrapper for **desktop** (may differ from Android). |
| `desktop/gradlew`, `desktop/gradlew.bat` | Run desktop builds **from `desktop/`** (or `../gradlew -p desktop` from root). |
| `desktop/src/main/kotlin/` | Desktop UI + Ktor server + JVM crypto. |
| `desktop/src/main/resources/` | Fonts, etc. |

**Generated:** `desktop/build/`, `desktop/.gradle/` (gitignored).

See also **[desktop/README.md](../desktop/README.md)**.

---

## 4. `docs/` — Human-readable docs only

No build logic here. Safe to read offline.

---

## 5. Quick command reference

| Goal | Where to run | Command |
|------|----------------|---------|
| Android debug APK | Repo root | `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat …`) |
| Android unit tests | Repo root | `./gradlew :app:testDebugUnitTest` |
| Desktop run | `desktop/` | `./gradlew run` (Windows: `gradlew.bat run`) |

Full detail: **[BUILD.md](BUILD.md)**.
