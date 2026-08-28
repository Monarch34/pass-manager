package com.passmanager.crypto.random

import java.security.SecureRandom

/**
 * The JVM's own CSPRNG.
 *
 * `SecureRandom()` rather than `getInstanceStrong()`: on Linux the strong instance is
 * configured to `NativePRNGBlocking`, which reads `/dev/random` and blocks when the kernel
 * judges its entropy estimate low. For a desktop app that draws a salt while the user
 * waits, blocking is a hang with no explanation. The default instance is seeded from the
 * same kernel pool and does not block once seeded, which is the correct trade for key
 * material generated interactively.
 */
internal actual fun platformSecureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also(SecureRandom()::nextBytes)
