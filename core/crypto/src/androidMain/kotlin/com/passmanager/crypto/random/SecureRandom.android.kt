package com.passmanager.crypto.random

import java.security.SecureRandom

/**
 * Android's CSPRNG.
 *
 * Identical in shape to the JVM actual, and deliberately kept as its own source set rather
 * than folded into a shared `jvmCommon`: the two have the same code today and different
 * reasons for it. Android's default provider is Conscrypt, seeded from the kernel and
 * updated through Play system updates; the desktop JVM's is whatever that JRE ships. If
 * either platform ever needs a different instance — a hardware-backed provider here, a
 * non-blocking choice there — the seam already exists and no shared file has to be split
 * under pressure.
 *
 * `SecureRandom()` and not `getInstanceStrong()`, for the same reason as the JVM: the
 * strong instance can block, and this is called on a path a user is waiting on.
 */
internal actual fun platformSecureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also(SecureRandom()::nextBytes)
