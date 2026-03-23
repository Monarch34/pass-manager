package com.passmanager.crypto.ecdh

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.Closeable
import java.security.SecureRandom

/**
 * Ephemeral X25519 Diffie-Hellman key exchange.
 *
 * Uses BouncyCastle's lightweight API for portability across all Android API
 * levels (minSdk 26) and Desktop JVM. Each instance generates a fresh keypair
 * and must be [close]d after use to zero the private key material.
 */
class X25519KeyExchange(random: SecureRandom = SecureRandom()) : Closeable {

    private var privateKeyBytes: ByteArray?

    /** Raw 32-byte X25519 public key, safe to transmit. */
    val publicKeyBytes: ByteArray

    init {
        val privKey = X25519PrivateKeyParameters(random)
        privateKeyBytes = privKey.encoded.copyOf()
        publicKeyBytes = privKey.generatePublicKey().encoded.copyOf()
    }

    /**
     * Computes the X25519 shared secret with [peerPublicBytes].
     * Caller must zero the returned array when done.
     */
    fun deriveSharedSecret(peerPublicBytes: ByteArray): ByteArray {
        val privBytes = privateKeyBytes
            ?: throw IllegalStateException("Key exchange already destroyed")
        require(peerPublicBytes.size == KEY_LENGTH) {
            "Peer public key must be $KEY_LENGTH bytes"
        }

        val privKey = X25519PrivateKeyParameters(privBytes, 0)
        val peerPubKey = X25519PublicKeyParameters(peerPublicBytes, 0)

        val agreement = X25519Agreement()
        agreement.init(privKey)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peerPubKey, sharedSecret, 0)
        return sharedSecret
    }

    override fun close() {
        privateKeyBytes?.fill(0)
        privateKeyBytes = null
    }

    companion object {
        const val KEY_LENGTH = 32
    }
}
