# PassManager

**Build:** [docs/BUILD.md](docs/BUILD.md) · **Usage:** [docs/USAGE.md](docs/USAGE.md)

PassManager keeps the vault and cryptography on the phone. The Windows desktop application provides LAN pairing and a larger UI after the session is verified; it is not intended as a second vault backend.

The repository contains **two applications** (Android `:app`, desktop under **`desktop/`**) and a small shared **`protocol/`** JVM library (pairing wire types + CBOR) that both builds consume via Gradle composite `includeBuild`. Android Studio at the repo root still loads **`:app`** only.

| | |
|--|--|
| **Android** (`app/`) | Vault, Argon2, biometrics, QR scan to pair |
| **Desktop** (`desktop/`) | Compose UI + small Ktor server for pairing |

Everything stays on device / LAN. No cloud backend, no accounts.

---

License: [MIT](LICENSE). Layout: [docs/REPOSITORY_LAYOUT.md](docs/REPOSITORY_LAYOUT.md).

---

## What it does

- **AES-256-GCM** at rest; **Argon2id** for the master key (64 MiB, 10 iterations, parallelism 4).
- **Biometric unlock** through Android Keystore (new fingerprint enrollment invalidates the key).
- **Desktop pairing** over LAN: **X25519** per session, **HKDF-SHA256** for the session key, **8-character safety number** for MITM detection before the session is trusted. Subsequent traffic uses **AES-GCM** with direction-prefixed nonces; replay attempts are rejected.
- Toolbar **refresh** on desktop re-fetches vault metadata from the phone; the phone rate-limits how often that’s allowed.
- Vault build has **no INTERNET permission** — network is for pairing, and that’s LAN-only.
- Titles/addresses are stored encrypted per field so the list can render without decrypting whole items.
- **Rich site icons:** Android defaults to Google’s favicon helper; desktop defaults to direct/private fetch. Both can be toggled in settings.

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
| Weak passphrase | Argon2id makes offline guessing slow |
| Backup leaking data | `allowBackup=false`, extraction rules lock it down |
| Screenshots | `FLAG_SECURE` on the sensitive UI |
| LAN MITM while pairing | ECDH + manual comparison of the safety number on both devices |
| Replay after pairing | Nonces with direction byte; reuse throws |
| Keys/passwords in heap | Sensitive material uses off-heap buffers / bytes where we could; passwords aren’t carried as `String` in the hot path |
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
