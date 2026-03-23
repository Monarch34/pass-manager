# PassManager

A privacy-first, offline password manager for Android with a companion desktop app for secure password autofill over your local network.

**All vault data is encrypted on-device. No cloud. No accounts.**

---

## Features

- **AES-256-GCM** encryption for all vault data at rest
- **Argon2id** key derivation (64 MiB memory, 10 iterations, parallelism 4)
- **Biometric unlock** via Android Keystore (key invalidated on new enrollment)
- **Desktop pairing** — securely send passwords from phone to PC over LAN
  - Ephemeral X25519 ECDH key exchange (new keypair per session)
  - HKDF-SHA256 session key derivation
  - Safety number for out-of-band MITM verification
  - AES-256-GCM with direction-prefixed monotonic nonce counters (replay protection)
- **No INTERNET permission** for the vault — LAN only, only during pairing
- Per-field encrypted title/address columns for fast header rendering

---

## Architecture

```
crypto/         AES-GCM cipher, Argon2id KDF, X25519 ECDH, HKDF-SHA256
data/           Room database (v6, migrations 1→6), repositories
domain/         Use cases, domain models
security/       VaultLockManager, DesktopPairingSession, SessionManager
di/             Hilt modules
ui/             Compose screens + ViewModels
navigation/     Nav graph + NavigationViewModel

desktop/
  crypto/       Same crypto primitives (JVM / Bouncy Castle)
  server/       Ktor HTTP + WebSocket pairing server (binds to LAN IP)
  ui/           Compose Desktop screens (Windows MSI target)
```

---

## Desktop Pairing Protocol

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

### Known limitation — transport security

HTTP/WS transport is **unencrypted** (no TLS). The session key is never transmitted; it is independently derived on both sides via ECDH. A local network attacker could substitute the desktop public key before ECDH runs.

**Mitigation:** The safety number is the guard against this. Always verify the 8-char code matches on both screens before entering the 6-digit pairing code.

---

## Security Model

| Threat | Mitigation |
|--------|-----------|
| Physical device theft | Vault encrypted with Argon2id-derived key; biometric or passphrase required |
| Weak passphrase | Argon2id (64 MiB, 10 iterations) makes offline brute force expensive |
| Cloud backup exfiltration | `allowBackup=false` + data extraction rules exclude all app data |
| Screenshot / screen recording | `FLAG_SECURE` in MainActivity |
| LAN MITM on pairing | Ephemeral ECDH + safety number visual verification + MITM detection on desktop |
| Replay attacks | Monotonic nonce counters with direction byte; `ReplayAttackException` on reuse |
| Session key in JVM heap | `SensitiveByteArray` backed by `DirectByteBuffer` (off-heap, GC-immune) |
| Password `String` in JVM heap | W2 mitigation: password extracted as `ByteArray`, never as `String`; zeroed after use |
| Too many password requests | Max 20 per session; 10s cooldown per request |
| Pairing endpoint DoS | Per-IP rate limit on `/v1/pair/handshake` (3s cooldown) |
| Server exposed on all NICs | Desktop server binds only to detected LAN IP, not `0.0.0.0` |

### Lock states (Android)

| State | Description |
|-------|-------------|
| `ColdLocked` | Process start — passphrase required |
| `WarmLocked` | App backgrounded after passphrase unlock — biometric OR passphrase |
| `Unlocked` | Vault key in memory only; never persisted |

---

## Build

### Android

Requirements: Android Studio (latest stable), JDK 17, Android SDK

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config in app/build.gradle.kts)
./gradlew assembleRelease
```

minSdk: 26 (Android 8.0) · targetSdk: 35

### Desktop (Windows MSI)

Requirements: JDK 17, `JAVA_HOME` set

```bash
cd desktop

# Run without packaging
./gradlew run

# Build Windows MSI installer
./gradlew packageMsi
```

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Language | Kotlin 2.0.21 |
| Android UI | Jetpack Compose + Material3 (BOM 2024.09.03) |
| Desktop UI | Compose Desktop 1.7.3 |
| DI | Hilt 2.51.1 |
| Database | Room 2.6.1 |
| KDF | Argon2kt 1.6.0 (Argon2id) |
| ECDH | Bouncy Castle 1.78.1 (X25519) |
| Biometric | androidx.biometric 1.1.0 |
| Serialization | kotlinx.serialization 1.7.3 (JSON + CBOR) |
| Network | Ktor 2.3.12 (CIO engine, WebSockets) |
| QR scanning | ML Kit Barcode 17.3.0 + CameraX 1.3.4 |

---

## Verification Checklist

1. `./gradlew assembleDebug` must succeed
2. Force-stop → reopen → only passphrase shown (cold lock)
3. Background → reopen → biometric option shown (warm lock)
4. Screenshot on vault screen → blocked by `FLAG_SECURE`
5. Copy password → wait 30s → paste elsewhere → empty
6. Enroll new fingerprint → reopen → biometric gone (key invalidated)
7. `adb backup com.passmanager` → empty backup (`allowBackup=false`)
8. Pair with desktop → verify safety numbers match on both screens
