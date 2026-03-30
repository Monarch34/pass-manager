# Build and environment

**After you can build:** how to run the apps and use pairing → **[USAGE.md](USAGE.md)**.

- **Android:** repository root. `settings.gradle.kts` includes `:app` and **`includeBuild("protocol")`** so `:app` depends on `com.passmanager:passmanager-protocol` (built from `protocol/`).
- **Desktop:** `desktop/` — its own `settings.gradle.kts`, wrapper, and `includeBuild("../protocol")` with the same dependency coordinates.
- **`protocol/`:** small Kotlin/JVM library (handshake + secure WebSocket message types, CBOR). Own `settings.gradle.kts`; not opened as an Android Studio module from the root import.

The Android and desktop apps are still **separate** Gradle builds; `protocol` is a **composite** included build consumed by both. Keep `protocol/build.gradle.kts`, `app/build.gradle.kts`, and `desktop/build.gradle.kts` aligned on **`com.passmanager:passmanager-protocol:<version>`** when you bump the library version.

---

## Files outside version control

| Item | Role |
|------|------|
| `local.properties` | `sdk.dir` for the Android SDK on this machine (see `local.properties.example`). |
| `keystore.properties` | Release signing when present (see `keystore.properties.example`); gitignored. |
| Keystore file (e.g. `.jks`) | Referenced from `keystore.properties`; gitignored. |
| User `~/.gradle/gradle.properties` | Optional; e.g. `org.gradle.java.home` (see `gradle.properties.example`). |

`local.properties` is host-specific. Copying it from another machine only works if `sdk.dir` is valid on this machine.

---

## Initial setup (each machine)

1. Clone the repository.
2. Install **JDK 17** and set **`JAVA_HOME`**. Open a new terminal after changing it.
3. Install the **Android SDK** (for example with [Android Studio](https://developer.android.com/studio)) and open the repository root, or create `local.properties` from `local.properties.example` and set `sdk.dir`.
4. From the repository root: `./gradlew :app:assembleDebug` (on Windows `.\gradlew.bat :app:assembleDebug`).
5. Desktop: `cd desktop`, then `./gradlew run` (or `gradlew.bat run` on Windows).

---

## JDK

The Android Gradle Plugin requires a **full JDK** (includes `jlink`), not only a JRE.

If the build fails with a missing `jlink`, point `JAVA_HOME` at a full JDK 17+, match Android Studio’s **Gradle JDK** to that JDK where applicable, run `gradlew --stop`, and rebuild.

Example — set `JAVA_HOME` for your Windows user (adjust the path to match your install):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

---

## Android (`:app`, repository root)

`app/build.gradle.kts`: `minSdk` 26, `targetSdk` 35, `compileSdk` 35 (verify in file if these change).

Command-line builds need `local.properties` with `sdk.dir`.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

**Debug:** `app/build.gradle.kts` — `isMinifyEnabled` is false.

**Release:** `isMinifyEnabled` and `isShrinkResources` are true; ProGuard/R8 uses `proguard-rules.pro`. Signing is defined in `app/build.gradle.kts`: if `keystore.properties` exists in the repo root, release uses `signingConfigs.release`; otherwise release uses the debug signing config.

Release APK output (default AGP layout):

`app/build/outputs/apk/release/app-release.apk`

---

## Desktop (`desktop/`)

```bash
cd desktop
./gradlew run
./gradlew packageMsi
```

From the repository root:

`./gradlew -p desktop run` or `.\gradlew.bat -p desktop run`

**`packageMsi`:** Compose Desktop calls **`jpackage`**, which ships with a **full JDK**, not Android Studio’s **`jbr`**. If Gradle’s effective JDK is Studio’s JBR, `packageMsi` fails with **`jpackage.exe` is missing**.

**Fix (Windows, no global env changes):**

1. Install [Eclipse Temurin 17](https://adoptium.net/) (or another **full** JDK 17).
2. Copy **`desktop/gradle.properties.example`** → **`desktop/gradle.properties`**, set **`org.gradle.java.home`** to that JDK.
3. Run **`desktop/package-msi.cmd`**. It uses a **project-local** Gradle user home so **`%USERPROFILE%\.gradle\gradle.properties`** (often `org.gradle.java.home` → JBR for Android) does not override **`desktop/gradle.properties`**.

**Alternatives:** one PowerShell session: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17…"`, then `cd desktop`, `gradlew.bat packageMsi`. If nothing in `~/.gradle` sets `org.gradle.java.home`, plain **`gradlew packageMsi`** after **`gradlew --stop`** is fine.

WiX on **`PATH`** is optional; Compose may download WiX. Packaging options are in **`desktop/build.gradle.kts`** → `compose.desktop` → `nativeDistributions`.

Build products appear under `desktop/build/`; the Gradle task log lists the generated installer path.

---

## Committed configuration templates

| File in repo | Purpose |
|--------------|---------|
| `local.properties.example` | Template for `local.properties`. |
| `keystore.properties.example` | Template for `keystore.properties`. |
| `gradle.properties.example` | Snippets for user-level `gradle.properties`. |
| `desktop/gradle.properties.example` | Template → `desktop/gradle.properties` for `org.gradle.java.home` (MSI); `desktop/gradle.properties` is gitignored. |
| `desktop/package-msi.cmd` | Windows: runs `packageMsi` with local `GRADLE_USER_HOME` so `~/.gradle` does not override the desktop JDK. |
| `gradle.properties` | Project-wide Gradle options (no machine-specific paths). |

If Gradle creates `gradle/gradle-daemon-jvm.properties`, do not commit it (it is listed in `.gitignore`).

---

## Git

Commit: application sources, both Gradle wrappers and their catalogs, `app/schemas/` when Room schemas change, tests, and documentation.

Do not commit paths listed in `.gitignore` (including `build/`, `.gradle/`, `local.properties`, keystores, and typical IDE files).

---

## Layout reference

[REPOSITORY_LAYOUT.md](REPOSITORY_LAYOUT.md)
