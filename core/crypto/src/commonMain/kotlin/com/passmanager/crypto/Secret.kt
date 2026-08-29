package com.passmanager.crypto

import com.passmanager.crypto.random.secureRandomBytes

/**
 * Bytes that must not be printed, logged, compared carelessly, or left lying in memory.
 *
 * A raw `ByteArray` is the wrong type for a key or a password, not because it cannot hold
 * one but because it makes every mistake easy and silent. It prints its identity but not its
 * risk, it compares in variable time, nothing marks who is responsible for erasing it, and
 * an accidental `"$key"` in a log line is indistinguishable from any other interpolation.
 * This type makes each of those either impossible or deliberate.
 *
 * ### What it changes
 *
 * - **It cannot be printed.** [toString] reports a length and never a byte, so a secret
 *   caught up in a log statement, an exception message or a debugger's object view discloses
 *   nothing. This is the single most common way key material escapes.
 * - **Reading is a visible event.** The bytes are only reachable inside [reveal], which is
 *   deliberately named: a reviewer scanning a diff can find every point where a secret is
 *   exposed by searching for one word.
 * - **Comparison is constant-time.** `==` on two secrets does not leak where they first
 *   differ, so a secret can never be compared the wrong way by accident.
 * - **Erasure has an owner.** [destroy] wipes the bytes, and the type implements
 *   `AutoCloseable`, so `use { }` erases on the way out of a scope including when the block
 *   throws.
 *
 * ### What it does not change
 *
 * It cannot erase what it never held. A passphrase typed into a platform text field is
 * already an immutable `String` before this type sees it, and neither the JVM nor Swift
 * offers a way to erase that. A moving garbage collector may also have copied the array
 * before [destroy] runs. The honest claim is narrower than "secrets are safe in memory": it
 * is that *this project* stops multiplying copies and erases the ones it makes.
 *
 * ### Ownership
 *
 * Nothing in this module destroys a secret it was passed. A function that receives a
 * `Secret` borrows it; whoever created it erases it. [adopt] transfers ownership of an array
 * and the caller must not touch it afterwards; [copyOf] takes a copy and leaves the original
 * the caller's problem.
 */
class Secret private constructor(private val bytes: ByteArray) : AutoCloseable {

    private var destroyed = false

    /** Not secret: a key's length is fixed by its algorithm and a ciphertext reveals it. */
    val size: Int get() = bytes.size

    val isDestroyed: Boolean get() = destroyed

    /**
     * Runs [block] with the raw bytes.
     *
     * The array passed in is the live one, not a copy, so that revealing a 5 MB attachment
     * does not double its footprint. It must not be retained beyond the block and must not be
     * modified. Named for grep: every place a secret becomes readable says so.
     */
    fun <R> reveal(block: (ByteArray) -> R): R {
        check(!destroyed) { "this secret was destroyed" }
        return block(bytes)
    }

    /**
     * A copy the caller owns and must erase. For the cases where a borrowed array genuinely
     * cannot work — a platform API that keeps a reference, for instance.
     */
    fun copyBytes(): ByteArray = reveal { it.copyOf() }

    /** Overwrites the bytes. Idempotent, and the only thing [close] does. */
    fun destroy() {
        if (!destroyed) {
            bytes.wipe()
            destroyed = true
        }
    }

    override fun close() = destroy()

    override fun toString(): String =
        if (destroyed) "Secret(destroyed)" else "Secret(${bytes.size} bytes)"

    /**
     * Constant-time, and deliberately false once either side has been destroyed: two wiped
     * secrets are both all-zero and comparing equal would say something untrue about the
     * values they used to hold.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return !destroyed
        if (other !is Secret) return false
        if (destroyed || other.destroyed) return false
        return constantTimeEquals(bytes, other.bytes)
    }

    /**
     * The length, and nothing derived from the contents.
     *
     * A hash of the bytes would be a lossy copy of the secret sitting in whatever hash table
     * it was put in, surviving [destroy] and visible in a heap dump. Every secret of the same
     * length therefore collides here, which is correct: secrets are not hash keys, and a
     * design that makes them one should be uncomfortable.
     */
    override fun hashCode(): Int = bytes.size

    companion object {
        /** Fresh entropy from the platform generator. */
        fun random(size: Int): Secret = Secret(secureRandomBytes(size))

        /**
         * Takes ownership of [bytes]. The caller must not read, write or retain the array
         * afterwards — it is now this secret's, and erasing it here would otherwise erase it
         * under whoever still held it.
         */
        fun adopt(bytes: ByteArray): Secret = Secret(bytes)

        /** Copies [bytes], leaving the original the caller's to erase. */
        fun copyOf(bytes: ByteArray): Secret = Secret(bytes.copyOf())

        /**
         * A passphrase, encoded as UTF-8.
         *
         * The `String` handed in cannot be erased on any platform this project targets, and
         * this function does not pretend otherwise. What it does is stop the exposure
         * spreading: everything derived from here on is erasable. Keep the string's lifetime
         * as short as the platform's text field allows.
         */
        fun copyOfUtf8(text: String): Secret = Secret(text.encodeToByteArray())
    }
}

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
 * Prefer [Secret] for anything that lives longer than a few statements. This exists for the
 * raw arrays that appear in between.
 */
fun ByteArray.wipe() {
    fill(0)
}
