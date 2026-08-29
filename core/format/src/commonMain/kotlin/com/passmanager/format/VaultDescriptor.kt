package com.passmanager.format

import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.crypto.key.VaultKeys

/**
 * The 31 bytes at the front of a vault: everything a reader needs before it has a key, and
 * nothing else.
 *
 * ### Why fixed-width rather than the JSON header version 1 used
 *
 * This is the only region an attacker can edit freely, and the only region parsed before
 * any authentication tag has been checked. Fixed offsets make that parse four integer reads
 * instead of a schema, put the version at a known offset so a future container revision can
 * change everything after it while every shipped reader still says something true, and make
 * the associated data a constant byte range rather than "the first 6 + N bytes".
 *
 * It also removes a hazard this project has already been bitten by: version 1's iOS writer
 * was not byte-stable across Xcode releases, because Foundation's `.sortedKeys` changed its
 * collation between 16.2 and 16.4 and the header's key order changed with it. Associated
 * data that is a raw slice of a fixed layout cannot be affected by any serialiser.
 *
 * ### The bounds are the denial-of-service gate
 *
 * They are not decoration and they are not `core:crypto`'s job. [Argon2Parameters] bounds
 * only what Argon2 itself requires — parallelism, at least one pass, and memory at least
 * `8 * parallelism` — because it is a general implementation and a caller may legitimately
 * ask for a great deal of memory. Nothing in it stops a *file* asking for four gibibytes,
 * and a reader that constructed parameters straight from a header would allocate that
 * before one byte had been authenticated.
 *
 * So the ceiling lives here, where the untrusted bytes are, and [parse] performs no
 * derivation at all: it returns a validated descriptor, and deriving a key is a separate
 * step that can only be reached with one in hand.
 */
class VaultDescriptor internal constructor(
    /** Framing and cryptographic rules. A reader refuses one it does not know. */
    val container: Int,
    /** What the body means. Gates nothing; it is what a reader reports and a fixture pins. */
    val schema: Int,
    /**
     * The lowest reader allowed to open this file.
     *
     * The load-bearing one. Without it the only safe rule is "refuse any schema I do not
     * know", which would mean the desktop client could never add a field — nothing forces a
     * phone to update, and there is no sync to update it through. With it, a field that may
     * safely be ignored ships without moving this number, and a field that must not be
     * ignored moves it and older readers step aside cleanly.
     */
    val minSchema: Int,
    val kdf: Argon2Parameters,
    val salt: ByteArray,
) {

    /**
     * The exact bytes this descriptor occupies, which are also the body's associated data.
     * Returned as a copy so a caller cannot edit the array a tag was computed over.
     */
    fun encode(): ByteArray {
        val out = ByteArray(Size)
        Magic.copyInto(out)
        out.putU8(4, container)
        out.putU16(5, schema)
        out.putU16(7, minSchema)
        out.putU8(9, kdf.iterations)
        out.putU8(10, kdf.parallelism)
        out.putU32(11, kdf.memoryKib.toLong())
        salt.copyInto(out, 15)
        return out
    }

    companion object {
        /** Never changes, across every future version. */
        val Magic = byteArrayOf('P'.code.toByte(), 'M'.code.toByte(), 'V'.code.toByte(), '2'.code.toByte())

        const val Size = 31

        /** The framing and crypto rules this code implements. */
        const val Container = 1

        /** The body schema this code writes, and the highest it understands. */
        const val Schema = 1

        // ── The gate ────────────────────────────────────────────────────────────
        // A floor as well as a ceiling: a file demanding one kibibyte of memory would
        // derive instantly, which is the same attack from the other end — a vault whose
        // recorded cost is a fiction.
        const val MinMemoryKib = 8_192
        const val MaxMemoryKib = 262_144
        const val MinIterations = 1
        const val MaxIterations = 16
        const val MinParallelism = 1

        /**
         * Headroom, not compatibility. Every vault this project writes uses one lane,
         * because lanes are computed sequentially here and so cost the user exactly what
         * one does while letting an attacker with real threads finish sooner. Accepting a
         * range costs nothing — Argon2 divides its memory among lanes rather than
         * multiplying it — and leaves room for a future writer that does run them in
         * parallel.
         */
        const val MaxParallelism = 8

        /**
         * Reads and validates. Performs no key derivation, so the bounds above cannot be
         * bypassed by a caller that forgets to check them.
         */
        internal fun parse(bytes: ByteArray): DescriptorParse {
            // The magic is checked before the length. A short arbitrary file is not a
            // truncated vault, and saying so would send someone looking for corruption in a
            // file that was never a vault at all.
            if (!bytes.has(0, Magic.size)) return DescriptorParse.NotAVault
            for (i in Magic.indices) {
                if (bytes[i] != Magic[i]) return DescriptorParse.NotAVault
            }
            if (!bytes.has(0, Size)) {
                return DescriptorParse.Damaged("file is shorter than a descriptor", bytes.size)
            }

            val container = bytes.u8(4)
            val schema = bytes.u16(5)
            val minSchema = bytes.u16(7)
            // Checked before anything else is trusted: an unknown container means the rest
            // of this layout is not known to mean what it appears to mean.
            if (container != Container || minSchema > Schema) {
                return DescriptorParse.Unsupported(container, schema, minSchema)
            }

            val iterations = bytes.u8(9)
            val parallelism = bytes.u8(10)
            val memoryKib = bytes.u32(11)

            if (iterations !in MinIterations..MaxIterations) {
                return DescriptorParse.Damaged("iterations $iterations is outside $MinIterations..$MaxIterations", 9)
            }
            if (parallelism !in MinParallelism..MaxParallelism) {
                return DescriptorParse.Damaged("parallelism $parallelism is outside $MinParallelism..$MaxParallelism", 10)
            }
            if (memoryKib < MinMemoryKib || memoryKib > MaxMemoryKib) {
                return DescriptorParse.Damaged("memory $memoryKib KiB is outside $MinMemoryKib..$MaxMemoryKib", 11)
            }

            return DescriptorParse.Parsed(
                VaultDescriptor(
                    container = container,
                    schema = schema,
                    minSchema = minSchema,
                    kdf = Argon2Parameters(
                        memoryKib = memoryKib.toInt(),
                        iterations = iterations,
                        parallelism = parallelism,
                    ),
                    salt = bytes.copyOfRange(15, 15 + VaultKeys.SaltSize),
                )
            )
        }
    }
}

internal sealed interface DescriptorParse {
    data class Parsed(val descriptor: VaultDescriptor) : DescriptorParse
    data class Unsupported(val container: Int, val schema: Int, val minSchema: Int) : DescriptorParse
    data class Damaged(val what: String, val offset: Int) : DescriptorParse
    data object NotAVault : DescriptorParse
}
