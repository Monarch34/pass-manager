# PassManager — Desktop app

This folder is a **standalone Gradle project** (Compose Desktop + LAN pairing server). It is **not** an Android module.

## Build

```bash
cd desktop
./gradlew run              # macOS / Linux
gradlew.bat run            # Windows (cmd/PowerShell)
```

From the **parent** repo root you can also run:

`./gradlew -p desktop run` (Unix) or `.\gradlew.bat -p desktop run` (Windows), using the **root** wrapper with the desktop project directory.

## Context

- **Android app** lives in the parent folder (`../app/`).  
- **Pairing protocol** and product overview: [../README.md](../README.md)  
- **JDK / SDK troubleshooting:** [../docs/BUILD.md](../docs/BUILD.md)  
- **Repo file map:** [../docs/REPOSITORY_LAYOUT.md](../docs/REPOSITORY_LAYOUT.md)
