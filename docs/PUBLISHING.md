# Publishing PassManager to end users

**Audience:** you, the **only** person who builds from this repository.  
**Not** for people who receive the app: they never install JDK, Android Studio, Gradle, or WiX.

PassManager is **two** products. End users who want the full experience (vault on the phone + desktop companion) need **both** installables you ship.

---

## What end users do (zero development)

| Platform | They install | They do **not** need |
|----------|----------------|----------------------|
| **Android** | Your **`.apk`** (sideload) or install from **Google Play** (if you publish there) | Android Studio, SDK, repo |
| **Windows** | Your **`.msi`** (desktop app) | JDK, WiX, Gradle, repo |

They **download / run the installer → open the app**. For pairing, they use **phone + PC on the same Wi‑Fi** and follow the on-screen QR / safety-number flow (see root [README.md](../README.md)).

---

## What you do before anyone else sees the app

### 1. Android — produce an installable APK (or AAB for Play)

- **Sideload / direct share:** build a **release APK** — commands and output path: [BUILD.md — Sharing builds](BUILD.md#sharing-builds-with-others-release-apk--windows-msi).
- **Google Play:** you upload an **AAB** (`bundleRelease`), use a **release keystore** (not debug), and complete Play Console listing, privacy form, and signing. That is a separate process from this doc; start from `assembleRelease` / `bundleRelease` once `keystore.properties` is set (see `keystore.properties.example`).

**Recommendation:** for anything beyond a tiny trusted circle, sign releases with your own key (`keystore.properties`), not the default debug fallback.

### 2. Windows desktop — produce the MSI

- On **your** Windows machine: full **JDK 17** (with `jpackage`) + **WiX** on `PATH`, then `packageMsi` from `desktop/` — details: [BUILD.md — same section](BUILD.md#sharing-builds-with-others-release-apk--windows-msi).

### 3. Versioning

- Bump **`versionCode` / `versionName`** in `app/build.gradle.kts` and **`packageVersion`** (and optionally `version`) in `desktop/build.gradle.kts` so users and you can tell builds apart.

---

## How to get files to people

Use whatever you already trust: **cloud drive** (Google Drive, Dropbox), **messenger attachment** (if size limits allow), **your own website**, or **GitHub Releases** (upload `app-release.apk` + the `.msi` as binary assets).  

Optional but good practice: publish a **SHA-256** checksum of each file so users can verify downloads if they care.

---

## Short text you can paste to testers

> Install **PassManager** on your Android phone from the APK (allow install from this source if Android asks).  
> On your Windows PC, run the **PassManager Desktop** MSI and complete the installer.  
> Put phone and PC on the **same Wi‑Fi**, open the desktop app, then use the phone app to scan the QR and follow the **safety number** steps.  
> No developer tools are required on your side.

---

## Related docs

- **[BUILD.md](BUILD.md)** — exact Gradle commands, JDK/WiX, `local.properties`, pairing quirks.  
- **[README.md](../README.md)** — product overview, security model, protocol sketch.
