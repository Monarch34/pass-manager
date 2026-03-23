package com.passmanager.desktop.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HkdfSha256 {

    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32

    fun derive(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = HASH_LEN
    ): ByteArray {
        require(length in 1..255 * HASH_LEN) { "Output length out of range: $length" }

        val prk = extract(salt, ikm)
        return try {
            expand(prk, info, length)
        } finally {
            prk.fill(0)
        }
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        val key = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val n = (length + HASH_LEN - 1) / HASH_LEN
        val okm = ByteArray(n * HASH_LEN)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(prk, ALGORITHM))

        var prev = ByteArray(0)
        for (i in 1..n) {
            mac.update(prev)
            mac.update(info)
            mac.update(i.toByte())
            prev = mac.doFinal()
            System.arraycopy(prev, 0, okm, (i - 1) * HASH_LEN, HASH_LEN)
        }

        return if (okm.size == length) okm else okm.copyOf(length)
    }
}
