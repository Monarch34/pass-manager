# .pmvault v1 — encrypted vault export format

This file is the single normative specification of the PassManager export container.
Both the Android exporter/importer and the iOS importer/exporter implement exactly this
document; neither platform's source code is authoritative over it. The format is
deliberately free of anything platform-specific: no Keystore, Keychain or device-binding
material ever appears in a `.pmvault` file — it is derived from the raw vault items only,
which is what makes it the migration path between devices and platforms.

## Container layout

```
offset  size  field
0       4     magic: ASCII "PMVT" (constant across all future versions)
4       2     headerLen: unsigned 16-bit, big-endian
6       N     header: UTF-8 JSON, exactly headerLen bytes
6+N     12    iv: AES-GCM nonce for the body
6+N+12  rest  body: AES-256-GCM ciphertext || 16-byte tag
```

The magic never changes; the header's `version` field alone governs parsing. A reader
that sees an unknown `version` MUST reject the file cleanly ("unsupported version"),
never attempt a best-effort parse.

## Header

```json
{"version":1,"salt":"<base64, exactly 16 bytes>","kdf":{"memory":65536,"iterations":3,"parallelism":4,"hashLength":32}}
```

- `salt`: base64 (RFC 4648, with padding) of 16 random bytes, freshly generated per export.
- `kdf`: Argon2id cost parameters. Writers always emit the pinned defaults above;
  readers honor whatever the header carries — after validating the bounds below.

### Mandatory pre-KDF validation (DoS gate)

A reader MUST validate the header BEFORE running Argon2id, and reject the file if any
check fails. Without this, a crafted header can demand a 1 GiB derivation and OOM-kill
the app before any authenticity check runs.

- `version == 1`
- `memory >= 8192` and `memory <= 262144` (KiB: 8 MiB floor, 256 MiB ceiling)
- `memory >= 8 * parallelism` — Argon2's own structural minimum, stated so that every
  reader failure stays inside this document's typed error set instead of escaping from
  inside the library. At the bounds above the rule is unreachable (8 × 8 = 64 KiB sits
  far below the 8192 KiB floor); implement it anyway, because it becomes live the day
  the floor moves.
- `iterations <= 16` and `>= 1`
- `parallelism <= 8` and `>= 1`
- `hashLength == 32`
- decoded salt length == 16
- `headerLen <= 4096`

## Key derivation

`key = Argon2id(version 1.3, password = UTF-8 bytes of the export passphrase,
salt = decoded salt, m = memory KiB, t = iterations, p = parallelism, outLen = 32)`

Note for iOS: libsodium's `crypto_pwhash` supports p=1 only; because this format pins
p=4, implementations must use the reference `phc-winner-argon2` C library (vendored),
not libsodium's wrapper.

## Body encryption

AES-256-GCM, 12-byte IV (random per export), 128-bit tag appended to the ciphertext
(the layout produced by both CryptoKit/swift-crypto `AES.GCM.seal` and JCA
`AES/GCM/NoPadding`).

**AAD is mandatory:** the additional authenticated data is the exact concatenation
`magic || headerLen || header` — the first `6 + headerLen` bytes of the file, verbatim.
Any tampering with the magic, length or header therefore fails the tag check.

Because GCM cannot distinguish a wrong passphrase from a corrupted file, the user-facing
failure message is: "passphrase is wrong or the file is corrupted" (TR: "parola yanlış
veya dosya bozuk"). Do not claim to know which.

## Body plaintext

UTF-8 JSON:

```json
{
  "version": 1,
  "exportedAt": 1787000000000,
  "items": [
    {
      "id": "uuid-string",
      "category": "login",
      "createdAt": 1787000000000,
      "updatedAt": 1787000000000,
      "payload": { "type": "login", "id": "uuid-string", "title": "GitHub",
                   "notes": "", "username": "user", "address": "https://github.com",
                   "password": "secret" }
    }
  ]
}
```

- Timestamps are Unix epoch milliseconds (UTC).
- `category` duplicates `payload.type` for cheap scanning; `payload.type` is authoritative.
- `payload` objects use exactly the schema PassManager stores at rest (the Android
  `PayloadJson` schema): a `"type"` discriminator plus the per-type fields below.
  Absent optional fields mean empty string / empty list; writers omit defaults.

### Payload schemas by type

Common to all types: `id` (string), `title` (string), `notes` (string).

| type       | additional fields |
|------------|-------------------|
| `login`    | `username`, `address`, `password` |
| `card`     | `cardholderName`, `cardNumber`, `cardCvc`, `cardExpiry` |
| `bank`     | `accountNumber`, `bankName`, `password`, `previousPasswords` (array of strings) |
| `note`     | — |
| `identity` | `firstName`, `lastName`, `email`, `phone`, `address`, `company` |

## Import merge semantics (both platforms)

- Match by `id`. Unknown id → insert, preserving `createdAt`/`updatedAt` from the file.
- Known id → the newer `updatedAt` wins; the loser is left untouched. Never delete.
- On read, clamp `updatedAt` to `min(fileValue, now)` before comparing, so a file
  carrying a forged far-future timestamp cannot permanently shadow local edits.
- Before applying, show the user a summary — how many inserts, how many overwrites
  (with the overwritten titles) — and offer an "add only" mode that skips overwrites.

## Resolved reader decisions

Cases the rules above leave open. Both platforms MUST decide them the same way or an
export written by one becomes unreadable — or worse, silently different — on the other.

- **Body `version`.** The body carries its own `version` field. Treat a value other
  than 1 exactly like an unsupported header version: reject cleanly.
- **Equal `updatedAt`.** "Newer wins" means strictly newer. On a tie the local row
  stays, so re-importing your own export is a no-op.
- **`createdAt` on overwrite.** An overwrite is an edit of an existing item: keep the
  local `createdAt` and take only the payload and `updatedAt` from the file. On insert,
  take the file's `createdAt` verbatim (only `updatedAt` is clamped).
- **Summary titles.** The overwrite list shows the *incoming* file's title, which needs
  no extra decryption and is normally identical to the local one anyway.
- **Trailing bytes.** Anything after the ciphertext is part of the ciphertext, so it
  fails the tag check. Readers do not trim or tolerate a tail.
- **Unknown payload fields.** Readers ignore fields they do not know (forward
  compatibility); writers never emit fields absent from the schema above.

## Writer requirements

- Fresh 16-byte salt and fresh 12-byte IV per export; never reuse either.
- The export passphrase is independent of the master passphrase; UI enforces a
  strength floor and warns against reusing the master passphrase.
- Zero the plaintext body buffer after encryption / after import parsing.
