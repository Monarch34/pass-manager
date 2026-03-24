# Building PassManager

Index: [docs/README.md](README.md) · [REPOSITORY_LAYOUT.md](REPOSITORY_LAYOUT.md)

You’ve got two Gradle worlds: **Android at the repo root** (`:app` only in `settings.gradle.kts`) and **desktop under `desktop/`** with its own wrapper. They don’t share one multi-module graph — open root in Android Studio for the phone app; treat `desktop/` as its own project when you work there.

---

## Debug vs release (what to run when)

Rough map:

- **Android day-to-day:** `./gradlew :app:assembleDebug` — no R8 shrink, normal debug signing.
- **Android “what ships”:** `./gradlew :app:assembleRelease` — minify + shrink. Signing comes from `keystore.properties` if you created it; if not, you still get a minified APK but it’s signed with the **debug** key (fine for smoke-testing, **not** for Play).
- **Desktop day-to-day:** `cd desktop && ./gradlew run` — runs out of `build/`, no installer.
- **Desktop installer:** `packageMsi` on Windows — needs a **real** JDK with `jpackage` plus **WiX** on PATH. More below.

If you’re uploading to Play, use **`bundleRelease`**, your **release** keystore, and whatever Play Console wants besides that.

---

## Before you blame the code

**JDK:** AGP wants a **full JDK 17+**, not a random JRE. It calls `jlink` under the hood. Android Studio’s **jbr** is usually fine for Android + `desktop run`. **Temurin 17** (or similar) is the boring safe choice if you want one JDK everywhere.

Set **`JAVA_HOME`** to that JDK and restart terminals after you change it. On Windows, something like:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

(tweak the path if Studio lives elsewhere).

**SDK:** For command-line Android builds you need **`local.properties`** in the repo root with `sdk.dir=...` — copy from `local.properties.example`. Android Studio normally creates this when you open the project.

**Wrong Java from the IDE:** If Gradle suddenly can’t find `jlink` and the error path mentions `.cursor` or an extension JRE, your editor picked a minimal runtime. Point `JAVA_HOME` at a full JDK, or copy the snippet from **`gradle.properties.example`** into **your user** `~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle\gradle.properties` on Windows). Don’t put machine paths in the **project** `gradle.properties` — that file is shared.

In Android Studio: *Settings → Build → Gradle → Gradle JDK* should match what you expect.

After JDK changes: `gradlew --stop`, then sync/build again.

---

## That `jlink` error

If you see something like:

`jlink executable ...\.cursor\extensions\redhat.java...\jre\...\jlink.exe does not exist`

Gradle isn’t using a JDK. Fix `JAVA_HOME` / user `gradle.properties` as above.

---

## Android — usual commands

```bash
gradlew --stop
gradlew :app:assembleDebug
gradlew :app:testDebugUnitTest
```

(`.\gradlew.bat` on Windows.) You still need `local.properties` for CLI builds.

---

## Desktop — run it

Not the `:app` module. From `desktop/`:

```bash
cd desktop
./gradlew run              # Unix
gradlew.bat run            # Windows
```

Or from repo root: `.\gradlew.bat -p desktop run` / `./gradlew -p desktop run`.

### Phone can’t connect (weird 172.30.x.x)

If the phone times out to **`172.30.240.x`**-style addresses, the QR was probably built from a **Hyper-V / WSL virtual NIC**, not your Wi‑Fi. The app tries to prefer real LAN ranges (`192.168…`, `10…`, `172.16–31…`), but you still need the PC and phone on the **same network**, Windows **Firewall** allowing inbound on the pairing port (or an allow rule for PassManager Desktop), and a QR that shows an IP the phone can actually route to. Restart desktop after you change Wi‑Fi.

### “Challenge required” / second scan dies

Only **one** HTTP handshake per desktop process. Scan again without restarting and you’ll get **409** `already_paired` — no challenge in the body. **Restart the desktop app**, get a fresh QR, scan that.

---

## Release APK and MSI (build machine only)

Outputs are yours to handle however you like; this file only describes **how to produce** them.

**Android release** (repo root, `local.properties` in place):

```bash
./gradlew :app:assembleRelease
# Windows: .\gradlew.bat :app:assembleRelease
```

APK lands in `app/build/outputs/apk/release/app-release.apk`. For Play, also `bundleRelease` and sign properly.

**Signing:** `keystore.properties` in the repo root (see `keystore.properties.example`) → release uses your keystore. No file → still minified, but **debug-signed**.

**Windows MSI:** `jpackage` lives in a **full** JDK. Android Studio’s **jbr** often **doesn’t** ship `jpackage`, so point `JAVA_HOME` at something like [Eclipse Temurin 17](https://adoptium.net/), new shell, then:

```bash
cd desktop
./gradlew packageMsi
```

WiX has to be on **PATH** ([wixtoolset.org](https://wixtoolset.org/)) — Gradle will complain about `light`/`candle` if it isn’t. Packaging knobs live in **`desktop/build.gradle.kts`** under `compose.desktop` → `nativeDistributions`.

The `.msi` shows up under `desktop/build/compose/binaries/…/msi/` (exact subfolder varies with Compose/Gradle). Remember: desktop is the **companion**; the vault stays on Android.

---

## Files worth knowing about

| File | In git? | Notes |
|------|---------|--------|
| `gradle.properties` | yes | Shared flags, no secrets |
| `gradle.properties.example` | yes | Hints for *user* `~/.gradle/gradle.properties` |
| `keystore.properties.example` | yes | Template → copy to `keystore.properties` (ignored) |
| `local.properties` | **no** | SDK path, local only |
| `local.properties.example` | yes | What to put in `local.properties` |

If Gradle creates `gradle/gradle-daemon-jvm.properties`, don’t commit it — it’s ignored and can fight your JDK choice.
