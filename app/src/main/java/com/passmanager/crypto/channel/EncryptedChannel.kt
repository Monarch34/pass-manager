package com.passmanager.crypto.channel

import com.passmanager.crypto.util.SensitiveByteArray
import com.passmanager.domain.exception.ReplayAttackException
import java.io.Closeable
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bidirectional AES-256-GCM encrypted channel with replay protection.
 *
 * Uses direction-prefixed monotonic nonce counters instead of random IVs,
 * guaranteeing nonce uniqueness across both communication directions
 * without coordination.
 *
 * Envelope format: `[version 1B][nonce 12B][ciphertext + GCM-tag]`
 *
 * Nonce layout (12 bytes):
 * ```
 * [0]      direction byte (0x00 = phone→desktop, 0x01 = desktop→phone)
 * [1..3]   reserved (0x00)
 * [4..11]  8-byte big-endian counter
 * ```
 */
/**
 * @param sessionKey Raw key bytes. Moved off JVM heap immediately via [SensitiveByteArray.directFrom]
 *   (which zeroes the passed-in array). Caller need not zero it separately.
 */
class EncryptedChannel(
    sessionKey: ByteArray,
    private val sendDirection: Direction
) : Closeable {
    // Off-heap: immune to JVM GC compaction and heap dumps
    private var sensitiveKey: SensitiveByteArray? = SensitiveByteArray.directFrom(sessionKey)

    enum class Direction(val byte: Byte) {
        PHONE_TO_DESKTOP(0x00),
        DESKTOP_TO_PHONE(0x01);

        fun opposite(): Direction = when (this) {
            PHONE_TO_DESKTOP -> DESKTOP_TO_PHONE
            DESKTOP_TO_PHONE -> PHONE_TO_DESKTOP
        }
    }

    private val lock = Any()
    @Volatile private var sendCounter: Long = 0
    @Volatile private var lastReceivedCounter: Long = -1

    fun seal(plaintext: ByteArray): ByteArray {
        synchronized(lock) {
            val sk = sensitiveKey ?: throw IllegalStateException("Channel destroyed")
            // Guard against counter overflow — 2^63-1 messages is unreachable in practice
            // but a wrapped counter would silently reuse nonces with the same key.
            if (sendCounter == Long.MAX_VALUE) {
                throw SecurityException("Nonce counter overflow — session must be re-paired")
            }
            val nonce = buildNonce(sendDirection.byte, sendCounter++)
            val key = sk.copyBytes()
            try {
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(TAG_BITS, nonce))
                val ciphertext = cipher.doFinal(plaintext)

                val envelope = ByteArray(1 + NONCE_LEN + ciphertext.size)
                envelope[0] = PROTOCOL_VERSION
                System.arraycopy(nonce, 0, envelope, 1, NONCE_LEN)
                System.arraycopy(ciphertext, 0, envelope, 1 + NONCE_LEN, ciphertext.size)
                return envelope
            } finally {
                key.fill(0)
            }
        }
    }

    fun open(envelope: ByteArray): ByteArray {
        synchronized(lock) {
            val sk = sensitiveKey ?: throw IllegalStateException("Channel destroyed")
            require(envelope.size > 1 + NONCE_LEN) { "Envelope too short" }
            require(envelope[0] == PROTOCOL_VERSION) {
                "Unsupported protocol version: ${envelope[0]}"
            }

            val nonce = ByteArray(NONCE_LEN)
            System.arraycopy(envelope, 1, nonce, 0, NONCE_LEN)
            val expectedDirection = sendDirection.opposite().byte
            require(nonce[0] == expectedDirection) {
                "Unexpected nonce direction: ${nonce[0]}"
            }

            val counter = extractCounter(nonce)
            if (counter <= lastReceivedCounter) {
                throw ReplayAttackException(
                    "Nonce counter $counter <= last seen $lastReceivedCounter"
                )
            }
            lastReceivedCounter = counter

            val ciphertext = ByteArray(envelope.size - 1 - NONCE_LEN)
            System.arraycopy(envelope, 1 + NONCE_LEN, ciphertext, 0, ciphertext.size)

            val key = sk.copyBytes()
            try {
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(TAG_BITS, nonce))
                return cipher.doFinal(ciphertext)
            } finally {
                key.fill(0)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            sensitiveKey?.close()
            sensitiveKey = null
            sendCounter = 0
            lastReceivedCounter = -1
        }
    }

    companion object {
        const val PROTOCOL_VERSION: Byte = 0x01
        const val NONCE_LEN = 12
        private const val TAG_BITS = 128
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val AES = "AES"

        fun buildNonce(directionByte: Byte, counter: Long): ByteArray {
            val nonce = ByteArray(NONCE_LEN)
            nonce[0] = directionByte
            ByteBuffer.wrap(nonce, 4, 8).putLong(counter)
            return nonce
        }

        fun extractCounter(nonce: ByteArray): Long =
            ByteBuffer.wrap(nonce, 4, 8).getLong()
    }
}

