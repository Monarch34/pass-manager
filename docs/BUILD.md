# Building PassManager

**Doc index:** [docs/README.md](README.md) · **Repo layout:** [REPOSITORY_LAYOUT.md](REPOSITORY_LAYOUT.md)

---

This repository contains **two separate applications**:

1. **Android app** — Gradle project at the **repository root** (`:app` module only in `settings.gradle.kts`).
2. **Desktop app** — **Another** Gradle project entirely under **`desktop/`** (own `settings.gradle.kts` and wrapper).

Build each from the instructions below; they do not share a single multi-module Gradle graph.

---

## Development vs production (Gradle targets)

| Goal | Android (`:app`) | Desktop (`desktop/`) |
|------|------------------|----------------------|
| **Local development** | `:app:assembleDebug` — no R8 minify, debug signing | `gradlew run` — unpackaged JVM app |
| **Production-style artifact** | `:app:assembleRelease` — R8 + shrink, signing per `keystore.properties` (see below) | `gradlew packageMsi` — Windows installer (needs full JDK + WiX on the build machine) |

Release APKs intended for **Play Console** must use a **release keystore** (`keystore.properties`), not the fallback debug signing.

---

## Prerequisites (every clone — read this first)

1. **JDK 17+ (full JDK, not a minimal JRE)**  
   Android Gradle Plugin needs **`jlink`** (`bin/jlink` / `bin/jlink.exe`).  
   Recommended: **Android Studio**’s bundled runtime (**`jbr`**) or **Eclipse Temurin 17**.

2. **`JAVA_HOME`**  
   Point it at that JDK so command-line Gradle and editors behave consistently:
   - **Windows (PowerShell, persistent):**  
     `[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")`  
     (adjust path if Studio is installed elsewhere), then **restart** the terminal / IDE.
   - **macOS / Linux:** export `JAVA_HOME` in `~/.zshrc` or `~/.bashrc` to your JDK or Studio `jbr`.

3. **Android SDK**  
   Install via **Android Studio** → SDK Manager. For **command-line** builds, create **`local.properties`** in the project root (see **`local.properties.example`**).  
   `local.properties` is **gitignored**.

4. **Optional: Gradle JDK override**  
   If something still picks the wrong Java (e.g. an editor-bundled JRE without `jlink`), copy lines from **`gradle.properties.example`** into  
   **`%USERPROFILE%\.gradle\gradle.properties`** (Windows) or **`~/.gradle/gradle.properties`** (macOS/Linux).  
   **Do not** commit machine-specific paths into the project’s `gradle.properties`.

5. **Android Studio**  
   *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK* → choose **Embedded JDK** or the same **`jbr`** as `JAVA_HOME`.

6. After changing JDK: **`gradlew --stop`**, then sync/build again.

---

## JDK / `jlink` errors

If you see:

`jlink executable ...\.cursor\extensions\redhat.java...\jre\...\jlink.exe does not exist`

the build is using a **minimal JRE** (common with some editor extensions), not a full JDK. Fix **`JAVA_HOME`** and/or **`gradle.properties.example`** override as above.

---

## Commands (development)

```bash
gradlew --stop
gradlew :app:assembleDebug
gradlew :app:testDebugUnitTest
```

Requires **`local.properties`** with `sdk.dir=...` for CLI builds (see **`local.properties.example`**).

---

## Desktop app (Compose Desktop)

The desktop app is **not** the Android `:app` module. Build it using **either** of these (same JDK as Android: `JAVA_HOME` / Studio **`jbr`**):

**Option A — use the desktop wrapper (recommended):**

```bash
cd desktop
./gradlew run              # macOS / Linux
# Windows: gradlew.bat run
```

**Option B — from repo root, reuse root wrapper with `-p`:**

```bash
# Windows (PowerShell / cmd, from repo root)
.\gradlew.bat -p desktop run

# macOS / Linux
./gradlew -p desktop run
```

The **`desktop/`** directory includes its own **`gradlew` / `gradlew.bat`** and **`gradle/wrapper/`** so the desktop app can be built like a standalone project.

### Desktop pairing: connect timeout to `172.30.x.x` or similar

If the phone logs **`ConnectTimeoutException`** to an address like **`172.30.240.x`**, the QR was built with a **WSL / Hyper-V virtual NIC** IP, not your Wi‑Fi IP. The desktop app **skips** common virtual interfaces and **prefers** `192.168.x.x` / `10.x.x.x` / `172.16–31.x.x` on real adapters.

**You still need:** PC and phone on the **same LAN**, Windows **Firewall** allowing inbound TCP on the pairing port (or allow the PassManager Desktop app), and the QR must show a reachable IP (check the desktop window / regenerate by restarting the desktop app after network changes).

### “Challenge required” / `HandshakeResponse` parse errors

The desktop allows **one HTTP handshake per desktop run**. A **second** scan returns **409** with `{ "error": "already_paired" }` (no `challenge` field). The app shows a clear message: **restart the desktop app** and scan the **new** QR. If the first attempt failed after the HTTP step, restart the desktop before scanning again.

---

## Production artifacts (release APK + Windows MSI)

These commands run on the **maintainer build machine** only. Output files are what you distribute by your own channels (not documented here).

### Android — `assembleRelease`

From the **repository root** (with `local.properties` for CLI builds):

```bash
./gradlew :app:assembleRelease
# Windows: .\gradlew.bat :app:assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release.apk`

For **Play Console**, build **`bundleRelease`** (AAB) and sign with a **release** key.

**Signing:** With **`keystore.properties`** (copy from **`keystore.properties.example`**, gitignored) present, release is signed with that keystore. If it is **absent**, release is still minified but signed with the **debug** keystore (useful only for local verification — **not** for Play).

### Desktop — `packageMsi`

MSI packaging uses **`jpackage`**, which ships with a **full JDK 17+**, not the trimmed **Android Studio `jbr`**. If `packageMsi` fails with **`jpackage.exe` is missing**, set **`JAVA_HOME`** to a full JDK (e.g. [Eclipse Temurin 17](https://adoptium.net/)), restart the terminal, then:

```bash
cd desktop
./gradlew packageMsi
# Windows: gradlew.bat packageMsi
```

**WiX:** the WiX Toolset must be on **`PATH`**. Install [WiX](https://wixtoolset.org/) if Gradle reports missing WiX / light / candle. Packaging is configured in **`desktop/build.gradle.kts`** (`compose.desktop` → `nativeDistributions`).

**Typical output:** under `desktop/build/compose/binaries/` → `msi/` (e.g. `PassManager Desktop-1.0.0.msi`; exact path varies by Compose/Gradle version).

The desktop app is the **LAN companion**; the Android app remains the **vault** and pairing client.

---

## Files in repo vs local

| File | In Git? | Purpose |
|------|---------|---------|
| `gradle.properties` | Yes | Shared Gradle flags only (no machine paths) |
| `gradle.properties.example` | Yes | Copy optional `org.gradle.java.home` to **user** `~/.gradle/gradle.properties` |
| `keystore.properties.example` | Yes | Copy to **`keystore.properties`** for release APK signing (optional) |
| `local.properties` | **No** | Your SDK path — create locally |
| `local.properties.example` | Yes | Template / instructions |

Do **not** commit `gradle/gradle-daemon-jvm.properties` if Gradle regenerates it; it is gitignored and can fight with your chosen JDK.
