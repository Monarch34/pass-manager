# PassManager

**Build:** [docs/BUILD.md](docs/BUILD.md) · **Usage:** [docs/USAGE.md](docs/USAGE.md)

PassManager keeps the vault and cryptography on the phone. The Windows desktop application provides LAN pairing and a larger UI after the session is verified; it is not intended as a second vault backend.

The repository contains **two applications** (Android `:app`, desktop under **`desktop/`**) and a small shared **`protocol/`** JVM library (pairing wire types + CBOR) that both builds consume via Gradle composite `includeBuild`. Android Studio at the repo root still loads **`:app`** only.

| | |
|--|--|
| **Android** (`app/`) | Vault, Argon2, biometrics, QR scan to pair |
| **Desktop** (`desktop/`) | Compose UI + small Ktor server for pairing |

The vault, its keys and all cryptography stay on the device; pairing traffic stays on the LAN. No cloud backend, no accounts. The one optional outbound connection anywhere in the product is the **Site icons** setting, which is off by default and, when on, contacts `t0.gstatic.com` and no other host.

---

License: [MIT](LICENSE). Layout: [docs/REPOSITORY_LAYOUT.md](docs/REPOSITORY_LAYOUT.md).

---

## What it does

- **AES-256-GCM** at rest; **Argon2id** for the master key (64 MiB, 3 iterations, parallelism 4 — memory is where Argon2's resistance lives, and 64 MiB sits well above every OWASP reference configuration; the rationale for the iteration count is in `KdfParams.kt`).
- **Device-bound vault key:** the wrapped key is sealed a second time with an Android Keystore key that cannot leave the phone, so a stolen copy of the database is useless on another machine. New vaults get this from the start; existing ones are offered the upgrade once a backup exists.
- **Encrypted export/import** (`.pmvault`): passphrase-protected, portable, and the only way to move a vault between devices or platforms — see [docs/FORMAT.md](docs/FORMAT.md).
- **Biometric unlock** through Android Keystore (new fingerprint enrollment invalidates the key).
- **Desktop pairing** over LAN: **X25519** per session, **HKDF-SHA256** for the session key, **8-character safety number** for MITM detection before the session is trusted. Subsequent traffic uses **AES-GCM** with direction-prefixed nonces; replay attempts are rejected.
- Toolbar **refresh** on desktop re-fetches vault metadata from the phone; the phone rate-limits how often that’s allowed.
- The app declares `android.permission.INTERNET`: pairing is LAN-only, and the single WAN use is the optional **Site icons** lookup, which is off by default.
- Titles/addresses are stored encrypted per field so the list can render without decrypting whole items.
- **Site icons:** off by default on both platforms — no icon request is made at all. When switched on (Android: Settings; desktop: the control above the vault list) each entry’s site address is sent to `t0.gstatic.com` — Google’s icon CDN, which is where `www.google.com/s2/favicons` redirects to anyway — and no other host is contacted, because the loader refuses to follow redirects.

---

## Where things live

See [docs/REPOSITORY_LAYOUT.md](docs/REPOSITORY_LAYOUT.md) for the full map. Short version: **`app/`** = Android, **`desktop/`** = desktop + [desktop/README.md](desktop/README.md).

---

## Pairing flow (high level)

```
Desktop                                    Phone
───────                                    ─────
Generate ephemeral X25519 keypair
Display QR(ip, port, desktopPub, token)
                                           Scan QR
                                           Generate ephemeral X25519 keypair
                              POST /v1/pair/handshake(phonePub, token)
ECDH(desktopPriv, phonePub) → sharedSecret
                                           ECDH(phonePriv, desktopPub) → sharedSecret
Both: HKDF-SHA256(sharedSecret, salt=sortedConcat(phonePub, desktopPub)) → sessionKey
Both: safetyNumber = SHA-256(sortedConcat)[0..3] → 8 hex chars
Desktop shows safetyNumber on VerifyScreen
                                           Phone shows safetyNumber + 6-digit code
 ◄──── User visually confirms codes match ────►
User types 6-digit code on desktop
Desktop sends: Verify(code) [AES-GCM encrypted]
                                           Phone checks code → VerifyOk(safetyNumber)
Desktop validates safetyNumber matches ────►
Session is Active — subsequent messages are AES-GCM with monotonic nonce counters
```

The HTTP/WebSocket leg is **not TLS**. The session key never goes over the wire in the clear — both sides derive it — but someone on your LAN could still try to swap keys before ECDH finishes. That’s why the **safety number** matters: if the 8 characters don’t match on both screens, stop and don’t enter the 6-digit code.

---

## Security notes (quick reference)

| Concern | What we did |
|---------|-------------|
| Stolen phone | Vault encrypted; need passphrase or biometric |
| Weak passphrase | Argon2id makes offline guessing slow; the lock screen also backs off after repeated failures (2 s doubling to 60 s) |
| Stolen database file | v2 vaults add a Keystore-sealed outer layer, so the file cannot be attacked off-device — losing the phone or clearing app data makes that vault unrecoverable without a `.pmvault` backup |
| Backup leaking data | `allowBackup=false`, extraction rules lock it down |
| Screenshots | `FLAG_SECURE` on the sensitive UI |
| LAN MITM while pairing | ECDH + manual comparison of the safety number on both devices |
| Replay after pairing | Nonces with direction byte; reuse throws |
| Keys/passwords in heap | Keys, passphrases and the desktop-send path use `ByteArray`/`CharArray` that are zeroed after use. Item payloads are not: JSON decoding and Compose text fields both produce immutable `String`s, so decrypted field values do sit on the heap until GC — an accepted residual, noted in the code where it happens |
| Spamming “send password” | Cap per session + cooldown on the phone |
| Handshake spam | Rate limit on `/v1/pair/handshake` |
| Desktop listening everywhere | Binds to the chosen LAN address, not `0.0.0.0` |

**Lock states (Android):** cold start needs passphrase; after backgrounding you get warm lock (biometric or passphrase again); unlocked means the vault key is only in memory and never written to disk.

---

## Dependencies

Pinned in **`gradle/libs.versions.toml`** (Kotlin, Compose BOM, Hilt, Room, Ktor, CameraX, ML Kit, etc.). Update versions there and sync Gradle.

---

## Suggested manual checks (crypto / lock changes)

1. `:app:assembleDebug` succeeds.
2. Force-stop the app, reopen: passphrase required (cold lock).
3. Background then foreground: biometric or passphrase (warm lock).
4. Vault screen: screenshots blocked (`FLAG_SECURE`).
5. Copy password, wait for clipboard clear, paste: empty.
6. Change fingerprint enrollment: biometric behavior matches expectations.
7. `adb backup com.passmanager`: no usable vault export (`allowBackup=false`).
8. Desktop pairing: safety numbers match on both devices before completing pairing.
