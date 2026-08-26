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
| **Settings** | Auto-lock timeout, passphrase change, device-bound protection, vault export/import, site icons (off, or on via Google’s favicon service), biometric toggle. |
| **Lock** | Use the lock action when you want the vault key cleared from memory; auto-lock also applies after the timeout when the app backgrounds (see Settings). After five wrong passphrases the unlock button waits — 2 seconds, doubling to a minute — and the screen counts down. Biometric unlock is unaffected, and a correct passphrase clears the wait. |

### Backing up: export and import (`.pmvault`)

**Settings → Backup → Export vault** writes every item to a single encrypted `.pmvault` file through the system file picker. It asks for a **separate export passphrase** — not your master passphrase. That one passphrase is the only thing protecting the file, so pick a strong one and store it somewhere other than the file itself. Anyone holding both has your vault.

**Settings → Backup → Import vault** reads such a file back. Before anything is written you get a summary — how many entries are new, how many will be overwritten, and the titles of the ones being replaced — plus an **Add only** option that inserts what is missing and leaves everything you already have untouched. Items are matched by id and the newer version wins; **nothing is ever deleted by an import**.

> **This file is the only way to move a vault.** A vault created or upgraded on a recent version is sealed with a key that never leaves that phone. There is no cloud copy, no key escrow, and no way to open the vault file on different hardware. If the phone is lost, factory reset, or the app's data is cleared, a `.pmvault` backup is the *only* thing that brings your entries back — the correct master passphrase will not be enough. Export before you upgrade, before you switch phones, and whenever you have added entries you would mind losing.

### Device-bound protection

**Settings → Security → Device-bound protection** shows whether this vault's key carries the extra Keystore layer. New vaults have it from the start. Older ones are offered the upgrade once — and the app asks you to export a backup first, because the upgrade cannot be undone and, from that point on, the backup file is your only recovery path. You can proceed without one, but the app will make you confirm that separately.

### If the vault will not open

If the phone's Keystore no longer holds this vault's key — after clearing the app's data, a factory reset, or restoring the app onto different hardware — the lock screen says so and offers a recovery screen. Your passphrase is not the problem and cannot help; the key it needs is genuinely gone.

That screen does exactly one thing: after you type a confirmation word, it **erases the vault on this phone** — every item, the vault metadata, and both device keys — and returns you to onboarding. This exists because importing a backup needs an unlocked vault to import *into*, so an unopenable vault has to be cleared out of the way first. The intended sequence is: erase → create a new vault → **Import vault** from your `.pmvault` file. Without a backup file there is nothing to restore, and the entries are gone.

### Desktop pairing (LAN)

1. Start the **desktop** app on a PC on the **same LAN** as the phone ([desktop README](../desktop/README.md)).
2. On the phone, open **Desktop link** (or the drawer entry that starts pairing — see app navigation).
3. Scan the **QR code** shown on the desktop (CameraX + ML Kit) or enter connection details if your flow supports it.
4. **Compare the safety number** on both screens before confirming; then complete verification on the desktop as prompted.
5. After the session is active, the desktop can request allowed actions (e.g. vault list refresh, password send) subject to **rate limits** on the phone.

Pairing traffic stays on the LAN and there is no cloud vault backend. The only optional outbound connection in the app is the **Site icons** lookup, which is off by default and contacts `t0.gstatic.com` and nothing else when on.

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
| Unlock button greyed out with a countdown | Too many wrong passphrases. Wait it out, or use biometrics if enabled. |
| “The device key for this vault is gone” | The Keystore key that sealed this vault no longer exists; the passphrase cannot substitute. Recovery screen → erase → new vault → import your `.pmvault` backup. |

---

## Further reading

- [REPOSITORY_LAYOUT.md](REPOSITORY_LAYOUT.md) — where code and modules live.
- [README.md](../README.md) — product overview and pairing diagram.
- [FORMAT.md](FORMAT.md) — the `.pmvault` container, byte for byte.
