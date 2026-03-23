package com.passmanager.crypto.ecdh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class X25519KeyExchangeTest {

    @Test
    fun `public key is 32 bytes`() {
        X25519KeyExchange().use { kx ->
            assertEquals(32, kx.publicKeyBytes.size)
        }
    }

    @Test
    fun `two exchanges produce different public keys`() {
        X25519KeyExchange().use { a ->
            X25519KeyExchange().use { b ->
                assertNotEquals(
                    a.publicKeyBytes.toList(),
                    b.publicKeyBytes.toList()
                )
            }
        }
    }

    @Test
    fun `shared secret is symmetric`() {
        X25519KeyExchange().use { alice ->
            X25519KeyExchange().use { bob ->
                val secretAB = alice.deriveSharedSecret(bob.publicKeyBytes)
                val secretBA = bob.deriveSharedSecret(alice.publicKeyBytes)
                assertArrayEquals(secretAB, secretBA)
                assertEquals(32, secretAB.size)
                secretAB.fill(0)
                secretBA.fill(0)
            }
        }
    }

    @Test
    fun `different peers produce different shared secrets`() {
        X25519KeyExchange().use { alice ->
            X25519KeyExchange().use { bob ->
                X25519KeyExchange().use { charlie ->
                    val ab = alice.deriveSharedSecret(bob.publicKeyBytes)
                    val ac = alice.deriveSharedSecret(charlie.publicKeyBytes)
                    assertNotEquals(ab.toList(), ac.toList())
                    ab.fill(0)
                    ac.fill(0)
                }
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `deriveSharedSecret after close throws`() {
        val kx = X25519KeyExchange()
        val pub = kx.publicKeyBytes.copyOf()
        kx.close()
        X25519KeyExchange().use { other ->
            kx.deriveSharedSecret(other.publicKeyBytes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject invalid peer public key size`() {
        X25519KeyExchange().use { kx ->
            kx.deriveSharedSecret(ByteArray(16))
        }
    }
}
