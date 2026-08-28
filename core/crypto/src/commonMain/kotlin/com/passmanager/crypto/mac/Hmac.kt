package com.passmanager.crypto.mac

/**
 * HMAC-SHA-256 from the platform's own implementation.
 *
 * Internal, and deliberately not part of this module's surface. HMAC is a building block,
 * not an answer: a caller reaching for a raw MAC is usually about to invent a key
 * derivation or a key wrap, and both already exist here with their domain separation and
 * their nonce handling decided. The only caller is `hkdfSha256`.
 *
 * The key must not be empty. HKDF never passes an empty one — an absent salt becomes a
 * block of zeros before it reaches here — and the JCA rejects empty keys outright, so
 * allowing it would mean a construction that works on Apple and throws on Android.
 */
internal expect fun platformHmacSha256(key: ByteArray, message: ByteArray): ByteArray
