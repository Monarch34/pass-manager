# PassManager

Offline password manager: vault and crypto live on the phone. The Windows desktop piece is there for pairing over your LAN and showing items/passwords on a big screen after you’ve verified the session — not a second place your secrets live by default.

There are **two apps** in this repo, and they’re **not** one Gradle multi-module tree. The Android app builds from the **repo root** (`:app`). The desktop app is **`desktop/`**, with its own `settings.gradle.kts` and `gradlew`. Same git repo, two separate builds.

| | |
|--|--|
| **Android** (`app/`) | Vault, Argon2, biometrics, QR scan to pair |
| **Desktop** (`desktop/`) | Compose UI + small Ktor server for pairing |

Everything stays on device / LAN. No cloud backend, no accounts.

---

## Building it

You need **JDK 17** for both. The Android side also needs the **SDK** — easiest path is [Android Studio](https://developer.android.com/studio) (or install [cmdline-tools](https://developer.android.com/studio#command-tools) if you like pain).

**Android:** open the **repo root** in Studio (the folder with `settings.gradle.kts`), or from a shell:

```bash
# copy local.properties.example → local.properties and set sdk.dir for CLI builds
./gradlew :app:assembleDebug          # macOS / Linux
.\gradlew.bat :app:assembleDebug      # Windows
```

**Desktop:**

```bash
cd desktop
./gradlew run                         # macOS / Linux
gradlew.bat run                       # Windows
```

You can also run the desktop project from the parent folder with `.\gradlew.bat -p desktop run` — same idea, root wrapper pointed at `desktop/`. Packaging an MSI is covered [below](#desktop-windows-msi) and in [docs/BUILD.md](docs/BUILD.md).

**Git:** commit sources, wrappers, Room schemas, tests, docs. Don’t commit `build/`, `.gradle/`, `local.properties`, keystores, or anything in `.gitignore` that looks like a secret.

License: [MIT](LICENSE).

**Docs:** [REPOSITORY_LAYOUT.md](docs/REPOSITORY_LAYOUT.md) for where files sit, [BUILD.md](docs/BUILD.md) for JDK headaches, release builds, and pairing oddities.

---

## What it does

- **AES-256-GCM** at rest; **Argon2id** for the master key (64 MiB, 10 iterations, parallelism 4).
- **Biometric unlock** through Android Keystore (new fingerprint enrollment invalidates the key).
- **Desktop pairing** over LAN: fresh **X25519** each session, **HKDF-SHA256** for the session key, **8-char safety number** so you can spot a MITM before you trust the link. Traffic after that is **AES-GCM** with direction prefixes on the nonces so replays blow up.
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
| LAN MITM while pairing | ECDH + compare safety number out-of-band (your eyes) |
| Replay after pairing | Nonces with direction byte; reuse throws |
| Keys/passwords in heap | Sensitive material uses off-heap buffers / bytes where we could; passwords aren’t carried as `String` in the hot path |
| Spamming “send password” | Cap per session + cooldown on the phone |
| Handshake spam | Rate limit on `/v1/pair/handshake` |
| Desktop listening everywhere | Binds to the chosen LAN address, not `0.0.0.0` |

**Lock states (Android):** cold start needs passphrase; after backgrounding you get warm lock (biometric or passphrase again); unlocked means the vault key is only in memory and never written to disk.

---

## Build commands (cheat sheet)

Android (from repo root): Android Studio + JDK 17 + SDK.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease    # see BUILD.md — keystore.properties or debug signing fallback
```

`minSdk` 26 · `targetSdk` 35 · root `settings.gradle.kts` only includes `:app`.

<a id="desktop-windows-msi"></a>

Desktop: JDK 17, `JAVA_HOME` sane. Second Gradle project — `cd desktop` or `gradlew -p desktop`.

```bash
cd desktop
./gradlew run
./gradlew packageMsi    # Windows installer; needs full JDK + WiX on the machine that builds it
```

---

## Stack (versions drift — check Gradle files for truth)

Kotlin 2.0.21, Compose / M3 on Android, Compose Desktop 1.7.3, Hilt, Room 2.6.1, Argon2kt, Bouncy Castle X25519, kotlinx.serialization, Ktor CIO + WebSockets, ML Kit barcode + CameraX.

---

## Sanity checks I run after touching crypto or lock flow

1. `:app:assembleDebug` is green.
2. Kill the app → reopen → passphrase only (cold).
3. Background → foreground → biometric or passphrase (warm).
4. Vault screen → screenshot blocked.
5. Copy password → wait out the clear-clipboard delay → paste is empty.
6. Add/remove fingerprint → biometric path updates as expected.
7. `adb backup com.passmanager` doesn’t hand you vault JSON.
8. Pair with desktop → safety strings match on both sides before trusting.
