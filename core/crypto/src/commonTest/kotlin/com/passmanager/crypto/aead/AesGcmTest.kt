package com.passmanager.crypto.aead

import com.passmanager.crypto.Secret
import com.passmanager.crypto.hex
import com.passmanager.crypto.random.secureRandomBytes
import com.passmanager.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * AES-256-GCM against the standard test cases, on every target.
 *
 * On the JVM and Android these check that the platform provider is being driven correctly —
 * the right tag length, the associated data actually fed in, the nonce not reinterpreted.
 * On Apple they check considerably more, because there the mode is assembled here out of
 * Apple's AES and this module's GHASH, and these vectors are the only thing standing
 * between that assembly and a vault a phone writes but a desktop cannot read.
 *
 * That difference is also why the same tests run everywhere rather than being written per
 * platform: two platforms agreeing with the specification is what makes them agree with
 * each other.
 */
class AesGcmTest {

    private val zeroKey = Secret.of(
        hex("0000000000000000000000000000000000000000000000000000000000000000")
    )
    private val vectorKey = Secret.of(
        hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    )

    /** Test case 13: no plaintext and no associated data — the tag alone. */
    @Test
    fun `vector 13 - an empty message`() {
        assertEquals(
            "530f8afbc74536b9a963b4f1c4cb738b",
            AesGcm.seal(zeroKey, hex("000000000000000000000000"), Secret.of(ByteArray(0))).toHex(),
        )
    }

    /** Test case 14: exactly one block of plaintext. */
    @Test
    fun `vector 14 - exactly one block`() {
        assertEquals(
            "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919",
            AesGcm.seal(zeroKey, hex("000000000000000000000000"), Secret.of(hex(SixteenZeroBytes))).toHex(),
        )
    }

    /**
     * Test case 16: sixty bytes of plaintext, so the final counter block is partial, and
     * twenty bytes of associated data, which is not a whole number of blocks either. Both
     * of those are zero-padded before they reach the authenticator, and each is padded
     * separately — the one place a plausible implementation gets a correct-looking answer
     * for the wrong reason.
     */
    @Test
    fun `vector 16 - a partial final block with associated data`() {
        assertEquals(
            VectorCiphertext + "76fc6ece0f4e1768cddf8853bb2d551b",
            AesGcm.seal(
                vectorKey,
                hex(VectorNonce),
                Secret.of(hex(VectorPlaintext)),
                hex("feedfacedeadbeeffeedfacedeadbeefabaddad2"),
            ).toHex(),
        )
    }

    /**
     * Associated data is authenticated and not encrypted, which is the entire reason it
     * exists: a container can keep its version and its derivation parameters readable
     * before anything is decrypted, and still detect an edit.
     *
     * Stated as a property rather than a second published vector, because the property is
     * what the format module will depend on: same key, same nonce, same plaintext, and the
     * ciphertext must be byte-identical while the tag must not be.
     */
    @Test
    fun `associated data changes the tag and nothing else`() {
        val withAad = AesGcm.seal(
            vectorKey,
            hex(VectorNonce),
            Secret.of(hex(VectorPlaintext)),
            hex("feedfacedeadbeeffeedfacedeadbeefabaddad2"),
        ).toHex()
        val withoutAad = AesGcm.seal(vectorKey, hex(VectorNonce), Secret.of(hex(VectorPlaintext))).toHex()

        val ciphertextLength = VectorCiphertext.length
        assertEquals(withoutAad.substring(0, ciphertextLength), withAad.substring(0, ciphertextLength))
        assertNotEquals(withoutAad.substring(ciphertextLength), withAad.substring(ciphertextLength))
    }

    /**
     * Lengths either side of every block boundary, for both the message and the associated
     * data. Counter-block arithmetic and GHASH's zero padding are the two places an
     * Apple-only bug can hide, and both are boundary conditions: a partial final block, an
     * exactly-full one, and an associated-data length that is not a multiple of sixteen.
     */
    @Test
    fun `round trips at every boundary length`() {
        val key = Secret.random(AesGcm.KeySize)
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        for (messageSize in intArrayOf(0, 1, 15, 16, 17, 31, 32, 33, 127, 128, 129, 1000)) {
            for (aadSize in intArrayOf(0, 1, 16, 17, 64)) {
                val plaintext = ByteArray(messageSize) { (it * 7 and 0xff).toByte() }
                val aad = ByteArray(aadSize) { (it * 13 and 0xff).toByte() }
                val sealed = AesGcm.seal(key, nonce, Secret.of(plaintext), aad)
                assertEquals(messageSize + AesGcm.TagSize, sealed.size)
                assertEquals(
                    plaintext.toHex(),
                    assertNotNull(
                        AesGcm.open(key, nonce, sealed, aad),
                        "message $messageSize, associated data $aadSize",
                    ).toByteArray().toHex(),
                )
            }
        }
    }

    /**
     * Every way of altering a sealed message must fail, including altering the associated
     * data that was never encrypted. If associated data were authenticated in name only, a
     * round trip would still succeed and only this test would notice.
     */
    @Test
    fun `rejects anything that was altered`() {
        val key = Secret.random(AesGcm.KeySize)
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        val aad = "version=2".encodeToByteArray()
        val sealed = AesGcm.seal(key, nonce, Secret.ofUtf8("the secret"), aad)

        for (index in sealed.indices) {
            val altered = sealed.copyOf()
            altered[index] = (altered[index].toInt() xor 1).toByte()
            assertNull(AesGcm.open(key, nonce, altered, aad), "flipped a bit at byte $index")
        }
        assertNull(AesGcm.open(key, nonce, sealed, "version=3".encodeToByteArray()), "associated data")
        assertNull(AesGcm.open(key, secureRandomBytes(AesGcm.NonceSize), sealed, aad), "nonce")
        assertNull(AesGcm.open(Secret.random(AesGcm.KeySize), nonce, sealed, aad), "key")
    }

    /** Shorter than a tag is malformed, not merely unauthentic, and must not throw. */
    @Test
    fun `rejects input too short to contain a tag`() {
        val key = Secret.random(AesGcm.KeySize)
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        for (size in 0 until AesGcm.TagSize) {
            assertNull(AesGcm.open(key, nonce, ByteArray(size)), "$size bytes")
        }
    }

    /**
     * Sizes are fixed by this module rather than chosen by the caller, so that a container
     * can never negotiate its way down to a 4-byte tag or a 16-byte key.
     */
    @Test
    fun `rejects key and nonce sizes other than the fixed ones`() {
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        assertFailsWith<IllegalArgumentException> {
            AesGcm.seal(Secret.random(16), nonce, Secret.random(1))
        }
        assertFailsWith<IllegalArgumentException> {
            AesGcm.seal(Secret.random(AesGcm.KeySize), secureRandomBytes(16), Secret.random(1))
        }
    }

    private companion object {
        const val SixteenZeroBytes = "00000000000000000000000000000000"
        const val VectorNonce = "cafebabefacedbaddecaf888"
        const val VectorPlaintext =
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39"
        const val VectorCiphertext =
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662"
    }
}
