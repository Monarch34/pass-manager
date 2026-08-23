package com.passmanager.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passmanager.crypto.kdf.Argon2KdfProvider
import com.passmanager.crypto.model.KdfParams
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Measures what the KDF actually costs on this device, so cost parameters are chosen from
 * numbers rather than from assumptions about which knob dominates.
 *
 * Not an assertion-bearing test: it prints a table and always passes. Run it explicitly when
 * revisiting [KdfParams] defaults.
 */
@RunWith(AndroidJUnit4::class)
class KdfCostBenchmark {

    private val provider = Argon2KdfProvider()
    private val passphrase = "correct-horse-battery".toByteArray()
    private val salt = ByteArray(16) { it.toByte() }

    private fun timeOf(params: KdfParams): Long {
        // One untimed pass first: the JNI library loads and warms on first use, and folding that
        // into the first measurement would make whichever config ran first look the slowest.
        provider.deriveKey(passphrase, salt, params).fill(0)
        val runs = 3
        val total = measureTimeMillis {
            repeat(runs) { provider.deriveKey(passphrase, salt, params).fill(0) }
        }
        return total / runs
    }

    @Test
    fun report_argon2_cost_across_parameters() {
        val configs = listOf(
            "m=64MiB t=10 p=4  (eski varsayilan)" to KdfParams(65536, 10, 4),
            "m=64MiB t=3  p=4  (yeni varsayilan)" to KdfParams(65536, 3, 4),
            "m=64MiB t=1  p=4" to KdfParams(65536, 1, 4),
            "m=19MiB t=2  p=1  (OWASP)" to KdfParams(19456, 2, 1),
            "m=46MiB t=1  p=1  (OWASP)" to KdfParams(47104, 1, 1)
        )
        println("=== ARGON2 MALIYETI (cihaz uzerinde, 3 kosunun ortalamasi) ===")
        for ((label, params) in configs) {
            println("  %-36s %5d ms".format(label, timeOf(params)))
        }
    }
}
