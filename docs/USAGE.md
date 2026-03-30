# Usage

How to run PassManager after a successful build. **Build prerequisites and commands:** [BUILD.md](BUILD.md).

---

## Android app (`:app`)

### Run from Android Studio

1. Open the **repository root** in Android Studio (it loads the `:app` module and the composite `protocol` build).
2. **Build variant:** set **`:app`** to **debug** for development (see `local.properties` / SDK in [BUILD.md](BUILD.md)).
3. Choose a device or emulator and click **Run**.

### First launch

1. Complete **onboarding**: create a **master passphrase** (and optional biometric setup when offered).
2. After setup, the app locks until you **unlock** with the passphrase or biometrics.

### Everyday use

| Area | What to do |
|------|------------|
| **Vault** | Browse items, search, filter by category, sort. Open an item to view or edit. |
| **Add item** | Create logins, cards, notes, identity, or bank entries (categories match vault item types). |
| **Settings** | Auto-lock timeout, passphrase change, favicon mode (Google vs private fetch), biometric toggle. |
| **Lock** | Use the lock action when you want the vault key cleared from memory; auto-lock also applies after the timeout when the app backgrounds (see Settings). |

### Desktop pairing (LAN)

1. Start the **desktop** app on a PC on the **same LAN** as the phone ([desktop README](../desktop/README.md)).
2. On the phone, open **Desktop link** (or the drawer entry that starts pairing — see app navigation).
3. Scan the **QR code** shown on the desktop (CameraX + ML Kit) or enter connection details if your flow supports it.
4. **Compare the safety number** on both screens before confirming; then complete verification on the desktop as prompted.
5. After the session is active, the desktop can request allowed actions (e.g. vault list refresh, password send) subject to **rate limits** on the phone.

Traffic stays on the LAN; there is no cloud vault backend.

### Debug-only: demo vault data

In **debug** builds only, **Settings** includes a **Development** section with **Load demo items** (adds several sample items per category). **Unlock the vault first.**  
Release builds do not show this. Do not rely on demo data for security testing.

---

## Desktop app (`desktop/`)

- **Run:** `cd desktop` then `.\gradlew.bat run` (Windows) or `./gradlew run` — see [BUILD.md](BUILD.md).
- **Pairing UI, verify flow, vault browser:** see [desktop/README.md](../desktop/README.md).

---

## Tests (optional)

From the repo root:

```bash
.\gradlew.bat :app:testDebugUnitTest
```

Instrumented / migration tests require a device or emulator:

```bash
.\gradlew.bat :app:connectedDebugAndroidTest
```

---

## Troubleshooting

| Issue | What to try |
|--------|-------------|
| Gradle cannot find the SDK | Create `local.properties` from `local.properties.example` and set `sdk.dir`. |
| `jlink` / JDK errors | Use a **full JDK 17+** for `JAVA_HOME` / Gradle JDK — [BUILD.md](BUILD.md) JDK section. |
| Desktop MSI / `jpackage` fails | Desktop packaging needs a full JDK path — [BUILD.md](BUILD.md) Desktop section. |
| Pairing fails | Same Wi‑Fi/LAN, firewall allows the desktop port, complete safety-number verification. |

---

## Further reading

- [REPOSITORY_LAYOUT.md](REPOSITORY_LAYOUT.md) — where code and modules live.
- [README.md](../README.md) — product overview and pairing diagram.
