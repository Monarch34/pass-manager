package com.passmanager.crypto.kdf

import com.passmanager.crypto.hash.Blake2b
import com.passmanager.crypto.wipe

/**
 * The cost of turning a passphrase into a key.
 *
 * These are not tuning knobs so much as the entire defence for a vault that an attacker has
 * a copy of. Once the file is stolen, the only thing between the passphrase and the
 * contents is how long one guess takes and how much memory it needs, and both are set here.
 *
 * They are stored per-vault rather than compiled in, because a vault created today has to
 * stay openable after the defaults rise, and because a device too small for the current
 * default has to be able to choose a lower one and record what it chose.
 */
data class Argon2Parameters(
    /** Memory cost in kibibytes. Rounded down internally to a multiple of `4 * parallelism`. */
    val memoryKib: Int,
    /** Passes over the memory. */
    val iterations: Int,
    /** Lanes. Computed sequentially here, so this changes the result but not the speed. */
    val parallelism: Int,
) {
    init {
        require(parallelism in 1..MaxParallelism) {
            "parallelism $parallelism is outside 1..$MaxParallelism"
        }
        require(iterations >= 1) { "iterations $iterations must be at least 1" }
        require(memoryKib >= 8 * parallelism) {
            "memory $memoryKib KiB is below the 8 * parallelism = ${8 * parallelism} KiB floor"
        }
    }

    companion object {
        private const val MaxParallelism = (1 shl 24) - 1

        /**
         * RFC 9106 section 4's second recommended option — 64 MiB, three passes — at one
         * lane.
         *
         * The RFC pairs those costs with four lanes. Lanes only buy anything when they run
         * in parallel, and this implementation is sequential, so four lanes here would cost
         * a user exactly what one does while letting an attacker with real threads finish
         * four times sooner. One lane spends the same wall-clock on a defence that an
         * attacker cannot parallelise away.
         *
         * 64 MiB is also chosen against the device rather than the desk: it must succeed on
         * the smallest phone the app supports, while it is being asked for on the unlock
         * path with the user waiting.
         */
        val Default = Argon2Parameters(memoryKib = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/**
 * Argon2id, version 1.3, as specified in RFC 9106.
 *
 * Argon2 is the one piece of cryptography in this module written here rather than taken
 * from the platform, and the reason is simply that no platform provides it: there is no
 * Argon2 in the JCA, none in CommonCrypto, and none in CryptoKit. The alternatives were
 * three different implementations — a JNI library on Android, a pure-Java one on the
 * desktop, vendored C on Apple — that would have to agree byte for byte forever, or one
 * shared implementation that agrees with itself by construction. A vault that a phone
 * writes and a desktop opens makes that agreement load-bearing, so it is worth owning.
 *
 * Writing a key derivation function is a much smaller risk than writing a cipher. Argon2's
 * output is fully determined by the specification and pinned by published test vectors, so
 * a mistake shows up as a failing vector rather than as a weakness; there are no keyed
 * table lookups and no data-dependent branches to leak through a cache; and the addressing
 * that *is* data-dependent is data-dependent by design, exactly as the reference
 * implementation has it.
 *
 * @param password the secret. Not erased here — the caller owns it and may still need it.
 * @param salt at least 8 bytes, 16 recommended. Unique per vault, and not secret.
 * @param secret an optional key ("pepper") mixed into the hash. Held somewhere the vault
 *   file is not, such as the platform keystore, so that a stolen file alone cannot be
 *   attacked offline.
 * @param associatedData optional non-secret data bound into the hash.
 */
fun argon2id(
    password: ByteArray,
    salt: ByteArray,
    parameters: Argon2Parameters,
    tagLength: Int = 32,
    secret: ByteArray = EmptyBytes,
    associatedData: ByteArray = EmptyBytes,
): ByteArray {
    require(salt.size >= MinSaltSize) {
        "salt is ${salt.size} bytes; Argon2 requires at least $MinSaltSize"
    }
    require(tagLength >= MinTagSize) {
        "tag length $tagLength is below the $MinTagSize byte minimum"
    }
    val argon2 = Argon2(parameters, tagLength)
    return try {
        argon2.derive(password, salt, secret, associatedData)
    } finally {
        argon2.erase()
    }
}

private const val MinSaltSize = 8
private const val MinTagSize = 4
private val EmptyBytes = ByteArray(0)

/** 1 KiB, expressed as the 128 64-bit words the compression function works on. */
private const val WordsPerBlock = 128

/** Argon2 divides each lane into four segments so that lanes can synchronise between them. */
private const val SyncPoints = 4

/** Pseudo-random values produced by one address block, for data-independent addressing. */
private const val AddressesPerBlock = 128

/** The type constant for Argon2id, mixed into the initial hash and the address blocks. */
private const val TypeArgon2id = 2L

private const val Version13 = 0x13

private class Argon2(
    private val parameters: Argon2Parameters,
    private val tagLength: Int,
) {
    private val lanes = parameters.parallelism

    /**
     * The requested memory rounded down to a whole number of segments. RFC 9106 calls this
     * m'; the *requested* value, not this one, goes into the initial hash.
     */
    private val blocks = (parameters.memoryKib / (SyncPoints * lanes)) * (SyncPoints * lanes)
    private val segmentLength = blocks / (SyncPoints * lanes)
    private val laneLength = segmentLength * SyncPoints

    private val memory: LongArray

    init {
        // A 64-bit word count that does not fit in an Int would surface as a bare
        // NegativeArraySizeException from the allocation below, which says nothing about
        // which parameter was too large.
        val words = blocks.toLong() * WordsPerBlock
        require(words <= Int.MAX_VALUE) {
            "memory ${parameters.memoryKib} KiB exceeds the largest array this can address"
        }
        memory = LongArray(words.toInt())
    }

    /** Scratch for the compression function, reused so that filling a block allocates nothing. */
    private val r = LongArray(WordsPerBlock)
    private val z = LongArray(WordsPerBlock)

    /** Scratch for data-independent addressing. `zero` must stay zero. */
    private val zero = LongArray(WordsPerBlock)
    private val addressInput = LongArray(WordsPerBlock)
    private val addresses = LongArray(WordsPerBlock)

    /**
     * Erases every buffer that held password-derived material. The memory array is the
     * bulk of it, and it stays resident for as long as the process lives otherwise.
     */
    fun erase() {
        memory.fill(0)
        r.fill(0)
        z.fill(0)
        addresses.fill(0)
        addressInput.fill(0)
    }

    fun derive(
        password: ByteArray,
        salt: ByteArray,
        secret: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val h0 = initialHash(password, salt, secret, associatedData)
        seedFirstBlocks(h0)
        h0.wipe()

        for (pass in 0 until parameters.iterations) {
            for (slice in 0 until SyncPoints) {
                // Lanes are independent within a slice, which is what makes Argon2
                // parallelisable. Running them in order here produces the same bytes a
                // threaded implementation would; it just takes as long as all of them.
                for (lane in 0 until lanes) {
                    fillSegment(pass, slice, lane)
                }
            }
        }

        val last = LongArray(WordsPerBlock)
        memory.copyInto(last, 0, (laneLength - 1) * WordsPerBlock, laneLength * WordsPerBlock)
        for (lane in 1 until lanes) {
            val offset = (lane * laneLength + laneLength - 1) * WordsPerBlock
            for (i in 0 until WordsPerBlock) last[i] = last[i] xor memory[offset + i]
        }

        val bytes = ByteArray(WordsPerBlock * 8)
        for (i in 0 until WordsPerBlock) writeLongLe(bytes, i * 8, last[i])
        last.fill(0)
        val tag = variableLengthHash(tagLength, bytes)
        bytes.wipe()
        return tag
    }

    /**
     * RFC 9106 section 3.2 step 1: H0, the 64-byte hash binding every parameter and every
     * input. The memory cost written in here is the value the caller asked for, not the
     * rounded-down [blocks] — a vault written by an implementation that rounds differently
     * would otherwise be unopenable by this one.
     */
    private fun initialHash(
        password: ByteArray,
        salt: ByteArray,
        secret: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = Blake2b(64)
        .updateLe32(lanes)
        .updateLe32(tagLength)
        .updateLe32(parameters.memoryKib)
        .updateLe32(parameters.iterations)
        .updateLe32(Version13)
        .updateLe32(TypeArgon2id.toInt())
        .updateLengthPrefixed(password)
        .updateLengthPrefixed(salt)
        .updateLengthPrefixed(secret)
        .updateLengthPrefixed(associatedData)
        .digest()

    /** The two blocks at the head of every lane, from which all the others are derived. */
    private fun seedFirstBlocks(h0: ByteArray) {
        val input = ByteArray(h0.size + 8)
        h0.copyInto(input)
        for (lane in 0 until lanes) {
            for (index in 0 until 2) {
                writeIntLe(input, h0.size, index)
                writeIntLe(input, h0.size + 4, lane)
                val block = variableLengthHash(WordsPerBlock * 8, input)
                readBlock(block, (lane * laneLength + index) * WordsPerBlock)
                block.wipe()
            }
        }
        input.wipe()
    }

    private fun readBlock(bytes: ByteArray, destination: Int) {
        for (i in 0 until WordsPerBlock) {
            memory[destination + i] = readLongLe(bytes, i * 8)
        }
    }

    private fun fillSegment(pass: Int, slice: Int, lane: Int) {
        // Argon2id is Argon2i for the first half of the first pass and Argon2d thereafter:
        // data-independent addressing while the memory an attacker could trade away is
        // still being built, data-dependent once it is.
        val dataIndependent = pass == 0 && slice < SyncPoints / 2
        if (dataIndependent) {
            addressInput.fill(0)
            addresses.fill(0)
            addressInput[0] = pass.toLong()
            addressInput[1] = lane.toLong()
            addressInput[2] = slice.toLong()
            addressInput[3] = blocks.toLong()
            addressInput[4] = parameters.iterations.toLong()
            addressInput[5] = TypeArgon2id
        }

        // The first two blocks of each lane were seeded, not computed.
        val start = if (pass == 0 && slice == 0) 2 else 0
        if (start == 2 && dataIndependent) nextAddresses()

        var current = lane * laneLength + slice * segmentLength + start
        var previous = if (current % laneLength == 0) current + laneLength - 1 else current - 1

        for (index in start until segmentLength) {
            // Wrapping into a new lane: the predecessor is the block just written, not the
            // one at the end of the previous lane.
            if (current % laneLength == 1) previous = current - 1

            val pseudoRandom = if (dataIndependent) {
                if (index % AddressesPerBlock == 0) nextAddresses()
                addresses[index % AddressesPerBlock]
            } else {
                memory[previous * WordsPerBlock]
            }

            // Until the first segment of the first pass is done there is nothing finished
            // in any other lane to point at.
            val referenceLane = if (pass == 0 && slice == 0) {
                lane
            } else {
                ((pseudoRandom ushr 32) % lanes.toLong()).toInt()
            }
            val referenceIndex =
                referenceIndex(pass, slice, index, pseudoRandom, referenceLane == lane)

            compress(
                destination = current * WordsPerBlock,
                x = previous * WordsPerBlock,
                y = (referenceLane * laneLength + referenceIndex) * WordsPerBlock,
                // Version 1.3 mixes each later pass into what is already there instead of
                // overwriting it. Version 1.0 overwrote, and that is the difference the
                // version number in the initial hash records.
                xorIntoDestination = pass != 0,
            )
            current++
            previous++
        }
    }

    /**
     * RFC 9106 section 3.3. Two applications of the compression function to a counter block
     * yield 128 pseudo-random values, enough for 128 block positions.
     */
    private fun nextAddresses() {
        addressInput[6]++
        compressArrays(addresses, zero, addressInput)
        compressArrays(addresses, zero, addresses)
    }

    /**
     * Maps a pseudo-random value onto a block that is finished and therefore legal to
     * reference, following the reference implementation's `index_alpha`.
     *
     * The squaring is not decoration: it biases the choice towards recent blocks, which is
     * what forces an attacker who discards memory to recompute long chains rather than
     * short ones.
     */
    private fun referenceIndex(
        pass: Int,
        slice: Int,
        index: Int,
        pseudoRandom: Long,
        sameLane: Boolean,
    ): Int {
        val areaSize: Int = if (pass == 0) {
            when {
                slice == 0 -> index - 1
                sameLane -> slice * segmentLength + index - 1
                // A block at the start of a segment cannot use the newest block of another
                // lane, because that lane may not have written it yet.
                else -> slice * segmentLength + if (index == 0) -1 else 0
            }
        } else {
            val finished = laneLength - segmentLength
            if (sameLane) finished + index - 1 else finished + if (index == 0) -1 else 0
        }

        var relative = pseudoRandom and 0xffff_ffffL
        relative = (relative * relative) ushr 32
        relative = areaSize - 1L - ((areaSize.toLong() * relative) ushr 32)

        val startPosition = if (pass == 0 || slice == SyncPoints - 1) {
            0L
        } else {
            ((slice + 1) * segmentLength).toLong()
        }
        return ((startPosition + relative) % laneLength).toInt()
    }

    private fun compress(destination: Int, x: Int, y: Int, xorIntoDestination: Boolean) {
        for (i in 0 until WordsPerBlock) r[i] = memory[x + i] xor memory[y + i]
        permute()
        if (xorIntoDestination) {
            for (i in 0 until WordsPerBlock) {
                memory[destination + i] = memory[destination + i] xor r[i] xor z[i]
            }
        } else {
            for (i in 0 until WordsPerBlock) memory[destination + i] = r[i] xor z[i]
        }
    }

    /**
     * The same compression function over standalone blocks, for address generation.
     * `destination` may alias `y`: both inputs are copied into scratch before anything is
     * written back.
     */
    private fun compressArrays(destination: LongArray, x: LongArray, y: LongArray) {
        for (i in 0 until WordsPerBlock) r[i] = x[i] xor y[i]
        permute()
        for (i in 0 until WordsPerBlock) destination[i] = r[i] xor z[i]
    }

    /** Reads scratch `r`, leaves the permuted copy in scratch `z`. */
    private fun permute() {
        r.copyInto(z)
        // Eight rows of sixteen consecutive words, then eight columns of eight word-pairs.
        for (i in 0 until 8) round(16 * i, stride = 2)
        for (i in 0 until 8) round(2 * i, stride = 16)
    }

    private fun round(base: Int, stride: Int) {
        val i0 = base
        val i1 = base + 1
        val i2 = base + stride
        val i3 = i2 + 1
        val i4 = base + 2 * stride
        val i5 = i4 + 1
        val i6 = base + 3 * stride
        val i7 = i6 + 1
        val i8 = base + 4 * stride
        val i9 = i8 + 1
        val i10 = base + 5 * stride
        val i11 = i10 + 1
        val i12 = base + 6 * stride
        val i13 = i12 + 1
        val i14 = base + 7 * stride
        val i15 = i14 + 1

        mix(i0, i4, i8, i12)
        mix(i1, i5, i9, i13)
        mix(i2, i6, i10, i14)
        mix(i3, i7, i11, i15)
        mix(i0, i5, i10, i15)
        mix(i1, i6, i11, i12)
        mix(i2, i7, i8, i13)
        mix(i3, i4, i9, i14)
    }

    /**
     * BLAKE2b's mixing function with the multiplication Argon2 adds. The extra
     * `2 * low32(a) * low32(b)` is what makes each step depend on a 64-bit multiply, so
     * that hardware which is cheap at XOR and shifts gains far less than it otherwise would.
     */
    private fun mix(a: Int, b: Int, c: Int, d: Int) {
        var va = z[a]
        var vb = z[b]
        var vc = z[c]
        var vd = z[d]

        va += vb + 2L * (va and 0xffff_ffffL) * (vb and 0xffff_ffffL)
        vd = (vd xor va).rotateRight(32)
        vc += vd + 2L * (vc and 0xffff_ffffL) * (vd and 0xffff_ffffL)
        vb = (vb xor vc).rotateRight(24)
        va += vb + 2L * (va and 0xffff_ffffL) * (vb and 0xffff_ffffL)
        vd = (vd xor va).rotateRight(16)
        vc += vd + 2L * (vc and 0xffff_ffffL) * (vd and 0xffff_ffffL)
        vb = (vb xor vc).rotateRight(63)

        z[a] = va
        z[b] = vb
        z[c] = vc
        z[d] = vd
    }
}

/**
 * RFC 9106 section 3.3's H', which stretches BLAKE2b past its 64-byte ceiling by chaining
 * digests and keeping the first half of each. Argon2 needs it for 1 KiB blocks and for tags
 * longer than 64 bytes.
 */
private fun variableLengthHash(outputLength: Int, input: ByteArray): ByteArray {
    if (outputLength <= Blake2b.MaxDigestSize) {
        return Blake2b(outputLength).updateLe32(outputLength).update(input).digest()
    }
    val out = ByteArray(outputLength)
    var v = Blake2b(Blake2b.MaxDigestSize).updateLe32(outputLength).update(input).digest()
    v.copyInto(out, 0, 0, 32)
    var written = 32
    while (outputLength - written > Blake2b.MaxDigestSize) {
        v = Blake2b(Blake2b.MaxDigestSize).update(v).digest()
        v.copyInto(out, written, 0, 32)
        written += 32
    }
    Blake2b(outputLength - written).update(v).digest().copyInto(out, written)
    v.wipe()
    return out
}

private fun Blake2b.updateLe32(value: Int): Blake2b {
    val bytes = ByteArray(4)
    writeIntLe(bytes, 0, value)
    return update(bytes)
}

private fun Blake2b.updateLengthPrefixed(value: ByteArray): Blake2b =
    updateLe32(value.size).update(value)

private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
    for (i in 0 until 4) target[offset + i] = (value ushr (8 * i)).toByte()
}

private fun writeLongLe(target: ByteArray, offset: Int, value: Long) {
    for (i in 0 until 8) target[offset + i] = (value ushr (8 * i)).toByte()
}

private fun readLongLe(source: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 7 downTo 0) value = (value shl 8) or (source[offset + i].toLong() and 0xff)
    return value
}
