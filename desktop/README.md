# Desktop application (`desktop/`)

Compose Desktop client and Ktor-based LAN pairing server. This directory is a **standalone Gradle project**, not the Android `:app` module.

## Run

```bash
cd desktop
./gradlew run          # macOS / Linux
gradlew.bat run        # Windows
```

From the parent directory:

`./gradlew -p desktop run` or `.\gradlew.bat -p desktop run`

## Windows MSI (`packageMsi`)

`jpackage` is not in Android Studio’s **JBR**. Use a **full JDK 17** and point Gradle at it from this folder only:

1. Copy **`gradle.properties.example`** → **`gradle.properties`** and set **`org.gradle.java.home`** to your Temurin (or other full JDK 17) install.
2. Run **`package-msi.cmd`** from this directory.

That script sets a **local** `GRADLE_USER_HOME` under `desktop/.gradle-packaging-userhome/` so a global **`%USERPROFILE%\.gradle\gradle.properties`** (for example `org.gradle.java.home` → Studio JBR for Android) does **not** override your desktop JDK. Nothing is changed in system or user environment variables.

If you have no conflicting `~/.gradle` property, `.\gradlew.bat packageMsi` after `.\gradlew.bat --stop` is enough.

WiX on **`PATH`** is optional; the build can download WiX. See [../docs/BUILD.md](../docs/BUILD.md).

## See also

- Android application: `../app/`
- Overview and security: [../README.md](../README.md)
- Build, JDK, MSI: [../docs/BUILD.md](../docs/BUILD.md)
- Layout: [../docs/REPOSITORY_LAYOUT.md](../docs/REPOSITORY_LAYOUT.md)
