package com.passmanager.crypto

/**
 * Compares two byte arrays without letting the comparison's duration reveal where they
 * first differ.
 *
 * The ordinary comparison returns as soon as it finds a mismatching byte, so how long it
 * ran tells an attacker how many leading bytes were correct. Given a guess and a timer,
 * that turns finding a 16-byte authentication tag from 2^128 work into 16 * 256 work: guess
 * the first byte until one guess is measurably slower, keep it, move on. This loop always
 * reads every byte and always performs the same number of operations.
 *
 * The lengths are compared normally and returned on early. Length is not a secret here:
 * ciphertext and tag sizes are visible in the file itself.
 */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var difference = 0
    for (i in a.indices) {
        difference = difference or (a[i].toInt() xor b[i].toInt())
    }
    return difference == 0
}

/**
 * Overwrites an array holding key material once it is no longer needed.
 *
 * This shortens the window in which a key sits in readable memory; it does not close it.
 * A moving garbage collector may already have copied the array elsewhere, and neither the
 * JVM nor Kotlin/Native offers a way to find or erase those copies. It is worth doing
 * anyway — the window is the whole lifetime of the process otherwise, and that is the
 * window a heap dump or a swapped-out page samples from.
 *
 * Named rather than written inline as `fill(0)` so that the reason and the limit above have
 * somewhere to live.
 */
fun ByteArray.wipe() {
    fill(0)
}
