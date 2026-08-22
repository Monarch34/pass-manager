package com.passmanager.desktop.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class X25519KeyExchangeTest {

    @Test
    fun `shared secret is symmetric`() {
        val alice = X25519KeyExchange()
        val bob = X25519KeyExchange()

        val secretAlice = alice.deriveSharedSecret(bob.publicKeyBytes)
        val secretBob = bob.deriveSharedSecret(alice.publicKeyBytes)

        assertContentEquals(secretAlice, secretBob)

        alice.close()
        bob.close()
    }

    @Test
    fun `close zeros private key`() {
        val kx = X25519KeyExchange()
        kx.close()

        assertFailsWith<IllegalStateException> {
            kx.deriveSharedSecret(ByteArray(32))
        }
    }

    @Test
    fun `reject wrong-size peer key`() {
        val kx = X25519KeyExchange()

        assertFailsWith<IllegalArgumentException> {
            kx.deriveSharedSecret(ByteArray(16))
        }

        kx.close()
    }
}
