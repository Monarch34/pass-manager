# Building PassManager

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

## Commands

```bash
gradlew --stop
gradlew assembleDebug
gradlew testDebugUnitTest
```

Requires **`local.properties`** with `sdk.dir=...` for CLI builds (see **`local.properties.example`**).

---

## Desktop app (Compose Desktop)

The **`desktop/`** project is a **separate Gradle build** (not `:app`). From the **repository root**:

```bash
.\gradlew -p desktop run
```

Use the **same JDK** as Android (`JAVA_HOME` / Studio **`jbr`**). The `desktop` folder has no `gradlew.bat`; the root wrapper with `-p desktop` is correct.

### Desktop pairing: connect timeout to `172.30.x.x` or similar

If the phone logs **`ConnectTimeoutException`** to an address like **`172.30.240.x`**, the QR was built with a **WSL / Hyper-V virtual NIC** IP, not your Wi‑Fi IP. The desktop app **skips** common virtual interfaces and **prefers** `192.168.x.x` / `10.x.x.x` / `172.16–31.x.x` on real adapters.

**You still need:** PC and phone on the **same LAN**, Windows **Firewall** allowing inbound TCP on the pairing port (or allow the PassManager Desktop app), and the QR must show a reachable IP (check the desktop window / regenerate by restarting the desktop app after network changes).

### “Challenge required” / `HandshakeResponse` parse errors

The desktop allows **one HTTP handshake per desktop run**. A **second** scan returns **409** with `{ "error": "already_paired" }` (no `challenge` field). The app shows a clear message: **restart the desktop app** and scan the **new** QR. If the first attempt failed after the HTTP step, restart the desktop before scanning again.

---

## Files in repo vs local

| File | In Git? | Purpose |
|------|---------|---------|
| `gradle.properties` | Yes | Shared Gradle flags only (no machine paths) |
| `gradle.properties.example` | Yes | Copy optional `org.gradle.java.home` to **user** `~/.gradle/gradle.properties` |
| `local.properties` | **No** | Your SDK path — create locally |
| `local.properties.example` | Yes | Template / instructions |

Do **not** commit `gradle/gradle-daemon-jvm.properties` if Gradle regenerates it; it is gitignored and can fight with your chosen JDK.
