# Desktop app (`desktop/`)

Compose Desktop UI plus a small Ktor server for LAN pairing. It is **not** part of the Android `:app` module — this directory is its own Gradle project.

## Run it

```bash
cd desktop
./gradlew run          # macOS / Linux
gradlew.bat run        # Windows
```

From the parent repo you can do the same with the root wrapper:

`./gradlew -p desktop run` or `.\gradlew.bat -p desktop run`

## Pointers

- Android vault + crypto: `../app/`
- Protocol and security write-up: [../README.md](../README.md)
- JDK issues, MSI builds, firewall/pairing notes: [../docs/BUILD.md](../docs/BUILD.md)
- Full tree: [../docs/REPOSITORY_LAYOUT.md](../docs/REPOSITORY_LAYOUT.md)
