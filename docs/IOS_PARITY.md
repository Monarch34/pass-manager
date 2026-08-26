# iOS parity contract

Behavioral contract for the iOS app (`ios/`). Where this file names an Android source
file, that file is the reference implementation; where behavior must differ per
platform (Keychain vs Keystore), this file states the intended equivalence, not the API.
The `.pmvault` interchange format lives in [FORMAT.md](FORMAT.md) and is normative for
both platforms.

## Key architecture (identical on both platforms)

Two keys, one indirection — reference `SetupVaultUseCase.kt` / `UnlockWithPassphraseUseCase.kt`:

1. **Vault key**: 32 random bytes generated at vault creation (CSPRNG). Encrypts every
   item. Never touches disk in the clear; lives in memory only while unlocked; zeroed
   on lock.
2. **KEK**: derived from the master passphrase with Argon2id
   (defaults m=65536 KiB, t=3, p=4, out=32; random 16-byte salt stored beside the
   wrapped key). Its only job is wrapping the vault key with AES-256-GCM.
3. Wrong passphrase is detected by the GCM tag failing on unwrap — there is no stored
   verifier and no boolean check to bypass.
4. Changing the passphrase rewraps the vault key (fresh salt, current default KDF cost);
   items are not re-encrypted. Biometric unlock is disabled by a passphrase change and
   must be re-enrolled.
5. **Device layer (platform-specific, never exported):**
   - Android: Keystore "pepper" outer wrap (Track A2).
   - iOS: the wrapped vault key blob is stored as a Keychain item with
     `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` (device-bound, requires a device
     passcode, excluded from backups). Face ID unlock = a second Keychain item holding
     the raw vault key behind `SecAccessControl` with `.biometryCurrentSet`
     (invalidated when biometrics change — mirrors `setInvalidatedByBiometricEnrollment`).
   - Threat-model equivalence matters, API parity does not. Nothing from this layer may
     ever appear in a `.pmvault` file.

## Storage row design (three envelopes — reference `VaultItemEntity.kt`)

Each item row stores three independent AES-256-GCM envelopes under the vault key, each
with its own random 12-byte IV:

| column | plaintext |
|---|---|
| `encrypted_data` + `data_iv` | full payload JSON (schema in FORMAT.md) |
| `encrypted_title` + `title_iv` | title string only |
| `encrypted_address` + `address_iv` | list-subtitle string (nullable) |
| `category` | PLAIN text (`login/card/note/identity/bank`) — deliberate tradeoff |
| `key_version` | int, plain |
| `created_at`, `updated_at` | epoch millis, plain |

The list screen queries ONLY the header columns (never the big payload), decrypts them
off the main thread into an in-memory cache keyed by `(id, updatedAt)`, and search runs
over that decrypted cache — never over SQL (ciphertext cannot be LIKE'd; any on-disk
search index would leak).

## Lock states (reference `VaultLockManager.kt`)

- **ColdLocked** (process start): passphrase only.
- **Unlocked**: vault key in memory.
- **WarmLocked** (after backgrounding + timeout, default 300 s, configurable
  60/300/900/1800): biometric or passphrase.
- Locking = zeroing the in-memory key. Nothing on disk changes.
- iOS: use `scenePhase` background transitions + the same timeout options.

## Screens (v1 scope)

Onboarding (create vault: passphrase + confirm, strength meter, min 8 chars) →
Lock (passphrase + Face ID button when enrolled) →
Vault list (search field, category filter chips, item rows: 40pt category tile +
title + category label, count line) →
Add/Edit (category picker + per-category fields; validation blocks saving on:
title required, login/bank password required, card number 16 digits, expiry MM/YY,
bank password 6-12 chars with complexity — reference `domain/validation/`. A weak
card CVC is surfaced as a *warning only* and never blocks the save, matching
`AddEditItemSaveValidator`; a card that saves on one platform must save on the
other) →
View item (masked password with reveal + copy; clipboard clears after 15 s — iOS:
`UIPasteboard` `expirationDate` + `localOnly`) →
Generator (length 8-64 slider default 16, four character-class toggles at least one on,
entropy line, strength bar) →
Settings (auto-lock timeout, change passphrase, Face ID toggle, export/import).

**Out of v1 scope:** desktop pairing (QR/X25519/WebSocket bridge) and site icons.

## Visual identity

Source of truth for every color: `protocol/src/main/kotlin/com/passmanager/protocol/design/Palette.kt`
and `LogoPalette.kt` (read them; do not invent values). Key anchors: dark background
0xFF0C0F14, surface 0xFF141820, brand teal family (LogoPalette TEAL_DARK 0xFF1A6D68 /
TEAL_LIGHT 0xFF21837D), fixed mint plate 0xFFCCFBF1 behind the shield mark. One accent
family (teal); category tints are data identity, not decoration. The shield mark's
canonical geometry: `app/src/main/res/drawable/ic_vault_shield.xml` (280x335 viewport);
plate = rounded square, 25% corner radius, shield at 58.3% width / 69.8% height of the
plate. Dark and light themes both required; follow the system setting.

## Generator rules (reference `PasswordGeneratorViewModel.kt`)

Length 8-64 (default 16); classes: upper/lower/digits/symbols; at least one class always
on; result must contain at least one char from each enabled class.

Entropy is `round(length * log2(poolSize))` bits, where **poolSize is the size of the
character set actually drawn from** — 26 + 26 + 10 + 26 for the four classes, the symbol
set being the 26 characters in `GeneratePasswordUseCase.SYMBOLS`. Two historical Android
bugs are being corrected rather than mirrored: the symbol class was counted as 32 while
26 characters were drawn, and the result was truncated instead of rounded. Both inflated
the number, which is the wrong direction for a security claim. Both platforms adopt the
corrected formula together; neither should replicate the old one for the sake of matching.

## Definition of parity

A user who exports from Android and imports on iOS (or vice versa) sees identical items,
identical field values, identical timestamps; a screenshot of the vault list on either
platform reads as the same product.
