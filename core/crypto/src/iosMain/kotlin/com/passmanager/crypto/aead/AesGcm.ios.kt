package com.passmanager.crypto.aead

import com.passmanager.crypto.constantTimeEquals
import com.passmanager.crypto.usePointer
import com.passmanager.crypto.wipe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

/**
 * AES-256-GCM on Apple, assembled from CommonCrypto's AES and this module's [GHash].
 *
 * Apple exposes no complete AES-GCM that Kotlin can call, so this is the composition GCM is
 * defined as: counter-mode encryption under AES, authenticated by GHASH, with the tag
 * masked by AES applied to the initial counter. Every AES block here is Apple's; the
 * counter arithmetic, the authenticator and the framing are this file's.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray,
): ByteArray {
    val initialCounter = initialCounter(nonce)
    val sealed = ByteArray(plaintext.size + AesGcm.TagSize)

    val keystream = keystream(key, initialCounter, plaintext.size)
    for (i in plaintext.indices) {
        sealed[i] = (plaintext[i].toInt() xor keystream[i].toInt()).toByte()
    }
    keystream.wipe()

    val tag = tag(key, initialCounter, associatedData, sealed, plaintext.size)
    tag.copyInto(sealed, plaintext.size)
    return sealed
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    associatedData: ByteArray,
): ByteArray? {
    val ciphertextLength = sealed.size - AesGcm.TagSize
    val initialCounter = initialCounter(nonce)

    // The tag is checked before a single byte is decrypted. Releasing plaintext that has
    // not been authenticated — even to the caller, even briefly — is what turns a decryption
    // routine into an oracle an attacker can query.
    val expected = tag(key, initialCounter, associatedData, sealed, ciphertextLength)
    val received = sealed.copyOfRange(ciphertextLength, sealed.size)
    val authentic = constantTimeEquals(expected, received)
    expected.wipe()
    if (!authentic) return null

    val keystream = keystream(key, initialCounter, ciphertextLength)
    val plaintext = ByteArray(ciphertextLength)
    for (i in 0 until ciphertextLength) {
        plaintext[i] = (sealed[i].toInt() xor keystream[i].toInt()).toByte()
    }
    keystream.wipe()
    return plaintext
}

/** J0 for a 96-bit nonce: the nonce, then the counter starting at one. */
private fun initialCounter(nonce: ByteArray): ByteArray {
    val block = ByteArray(BlockSize)
    nonce.copyInto(block)
    block[BlockSize - 1] = 1
    return block
}

/**
 * The keystream for `length` bytes, as AES applied to successive counter blocks.
 *
 * The counters are built here and handed to CommonCrypto as plain ECB rather than using its
 * CTR mode, because the two do not increment the same way: GCM advances only the last 32
 * bits and wraps within them, while `kCCModeCTR` advances the whole 128-bit block. The two
 * agree until the 32-bit counter overflows, which takes a 64 GiB message — so the
 * difference would never show up in testing and would be a silent interoperability failure
 * if it ever did. Doing the arithmetic here means there is nothing to disagree about.
 *
 * One `CCCrypt` call covers every block, so the per-call overhead is paid once.
 */
@OptIn(ExperimentalForeignApi::class)
private fun keystream(key: ByteArray, initialCounter: ByteArray, length: Int): ByteArray {
    if (length == 0) return ByteArray(0)
    val blocks = (length + BlockSize - 1) / BlockSize
    val counters = ByteArray(blocks * BlockSize)
    val counter = initialCounter.copyOf()
    for (block in 0 until blocks) {
        incrementCounter(counter)
        counter.copyInto(counters, block * BlockSize)
    }
    counter.wipe()
    val keystream = encryptBlocks(key, counters)
    counters.wipe()
    return keystream
}

/** GCM's inc32: the last four bytes only, wrapping within themselves. */
private fun incrementCounter(block: ByteArray) {
    for (i in BlockSize - 1 downTo BlockSize - 4) {
        val incremented = (block[i].toInt() and 0xff) + 1
        block[i] = incremented.toByte()
        if (incremented <= 0xff) return
    }
}

/**
 * GHASH over the associated data and the ciphertext, masked by AES applied to the initial
 * counter block. The hash subkey is AES applied to a block of zeros.
 */
@OptIn(ExperimentalForeignApi::class)
private fun tag(
    key: ByteArray,
    initialCounter: ByteArray,
    associatedData: ByteArray,
    ciphertext: ByteArray,
    ciphertextLength: Int,
): ByteArray {
    val subkey = encryptBlocks(key, ByteArray(BlockSize))
    val hash = GHash(subkey)
    subkey.wipe()

    hash.update(associatedData)
    hash.update(ciphertext, 0, ciphertextLength)
    hash.updateLengths(associatedData.size, ciphertextLength)

    val tag = hash.digest()
    val mask = encryptBlocks(key, initialCounter)
    for (i in tag.indices) {
        tag[i] = (tag[i].toInt() xor mask[i].toInt()).toByte()
    }
    mask.wipe()
    return tag
}

/**
 * AES over whole blocks with no chaining and no padding — the raw block cipher, which is
 * all GCM needs from it. ECB is unsafe as a mode and is not being used as one here: every
 * block encrypted through this function is a distinct counter value.
 */
@OptIn(ExperimentalForeignApi::class)
private fun encryptBlocks(key: ByteArray, input: ByteArray): ByteArray {
    val output = ByteArray(input.size)
    memScoped {
        val produced = alloc<size_tVar>()
        val status = key.usePointer { keyBytes ->
            input.usePointer { inputBytes ->
                output.usePointer { outputBytes ->
                    CCCrypt(
                        kCCEncrypt,
                        kCCAlgorithmAES,
                        kCCOptionECBMode,
                        keyBytes, key.size.convert(),
                        null,
                        inputBytes, input.size.convert(),
                        outputBytes, output.size.convert(),
                        produced.ptr,
                    )
                }
            }
        }
        // Checked rather than assumed, for the same reason the random generator checks its
        // status: the failure mode is a buffer of zeros that every caller would treat as
        // ciphertext.
        check(status == kCCSuccess) { "CCCrypt(AES-ECB) failed with status $status" }
        check(produced.value.toInt() == output.size) {
            "CCCrypt(AES-ECB) wrote ${produced.value} bytes, expected ${output.size}"
        }
    }
    return output
}

private const val BlockSize = 16
