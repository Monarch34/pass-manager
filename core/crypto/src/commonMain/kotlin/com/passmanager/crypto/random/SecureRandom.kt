package com.passmanager.crypto.random

/**
 * Cryptographically secure random bytes.
 *
 * This is the first and smallest instance of the rule the whole crypto module follows:
 * **the composition is shared, the primitive is not.** Argument checking, sizing and every
 * caller-visible guarantee live here in common code, and the actual entropy comes from the
 * generator the platform already maintains and patches — `SecureRandom` on the JVM and
 * Android, `SecRandomCopyBytes` on Apple. Nothing here bundles its own CSPRNG.
 *
 * The alternative — one portable generator compiled for every target — would be less code
 * and strictly worse: it would put a password manager's entropy on an implementation this
 * project maintains alone, on three platforms, instead of on three that are audited by
 * their vendors and updated without us.
 *
 * Callers own the returned array and should zero it once the value is no longer needed.
 */
fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "requested $size random bytes; size must be positive" }
    require(size <= MAX_REQUEST) {
        "requested $size random bytes; the largest single request is $MAX_REQUEST"
    }
    return platformSecureRandomBytes(size)
}

/**
 * An upper bound on a single request, so a caller cannot turn an unchecked length — a
 * corrupted header field, say — into an allocation the size of the heap. Every legitimate
 * caller asks for a key, a salt or a nonce; none of those approach this.
 */
private const val MAX_REQUEST = 1 shl 20

/**
 * The platform's generator. Never called directly: [secureRandomBytes] is the only entry
 * point, so the bounds above hold on every target rather than on whichever one the caller
 * happened to test on.
 */
internal expect fun platformSecureRandomBytes(size: Int): ByteArray
