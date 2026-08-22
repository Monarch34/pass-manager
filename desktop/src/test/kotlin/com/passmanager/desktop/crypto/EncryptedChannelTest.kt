package com.passmanager.desktop.crypto

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class EncryptedChannelTest {

    private fun makeSessionKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `seal and open round-trip`() {
        val key = makeSessionKey()
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val plaintext = "Hello, desktop!".toByteArray()
        val envelope = sender.seal(plaintext)
        val decrypted = receiver.open(envelope)

        assertContentEquals(plaintext, decrypted)

        sender.close()
        receiver.close()
    }

    @Test
    fun `multiple messages maintain counter ordering`() {
        val key = makeSessionKey()
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        for (i in 0..9) {
            val msg = "message $i".toByteArray()
            val envelope = sender.seal(msg)
            val decrypted = receiver.open(envelope)
            assertContentEquals(msg, decrypted)
        }

        sender.close()
        receiver.close()
    }

    @Test
    fun `replay same envelope throws`() {
        val key = makeSessionKey()
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val envelope = sender.seal("test".toByteArray())
        receiver.open(envelope)

        assertFailsWith<ReplayAttackException> {
            receiver.open(envelope)
        }

        sender.close()
        receiver.close()
    }

    @Test
    fun `out-of-order envelope throws`() {
        val key = makeSessionKey()
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val env0 = sender.seal("msg0".toByteArray())
        val env1 = sender.seal("msg1".toByteArray())

        receiver.open(env1)

        assertFailsWith<ReplayAttackException> {
            receiver.open(env0)
        }

        sender.close()
        receiver.close()
    }

    @Test
    fun `different directions produce different envelopes`() {
        val key = makeSessionKey()
        val channelA = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val channelB = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val msg = "same message".toByteArray()
        val envA = channelA.seal(msg)
        val envB = channelB.seal(msg)

        assertNotEquals(envA.toList(), envB.toList())

        channelA.close()
        channelB.close()
    }

    @Test
    fun `envelope starts with protocol version byte`() {
        val key = makeSessionKey()
        val channel = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)

        val envelope = channel.seal("test".toByteArray())
        assertEquals(EncryptedChannel.PROTOCOL_VERSION, envelope[0])

        channel.close()
    }

    @Test
    fun `seal after close throws`() {
        val key = makeSessionKey()
        val channel = EncryptedChannel(key, EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        channel.close()

        assertFailsWith<IllegalStateException> {
            channel.seal("test".toByteArray())
        }
    }

    @Test
    fun `nonce direction byte is correct`() {
        val nonceP2D = EncryptedChannel.buildNonce(EncryptedChannel.Direction.PHONE_TO_DESKTOP.byte, 0)
        val nonceD2P = EncryptedChannel.buildNonce(EncryptedChannel.Direction.DESKTOP_TO_PHONE.byte, 0)

        assertEquals(0x00.toByte(), nonceP2D[0])
        assertEquals(0x01.toByte(), nonceD2P[0])
    }

    @Test
    fun `nonce counter extraction round-trips`() {
        for (counter in listOf(0L, 1L, 255L, 65536L, Long.MAX_VALUE / 2)) {
            val nonce = EncryptedChannel.buildNonce(0x00, counter)
            val extracted = EncryptedChannel.extractCounter(nonce)
            assertEquals(counter, extracted)
        }
    }

    @Test
    fun `bidirectional communication`() {
        val key = makeSessionKey()
        val phone = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val desktop = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val env1 = phone.seal("request".toByteArray())
        val dec1 = desktop.open(env1)
        assertContentEquals("request".toByteArray(), dec1)

        val env2 = desktop.seal("response".toByteArray())
        val dec2 = phone.open(env2)
        assertContentEquals("response".toByteArray(), dec2)

        phone.close()
        desktop.close()
    }

    @Test
    fun `wrong protocol version rejected`() {
        val key = makeSessionKey()
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.DESKTOP_TO_PHONE)

        val envelope = sender.seal("test".toByteArray())
        envelope[0] = 0xFF.toByte()

        assertFailsWith<IllegalArgumentException> {
            receiver.open(envelope)
        }

        sender.close()
        receiver.close()
    }

    @Test
    fun `wrong direction byte rejected`() {
        val key = makeSessionKey()
        // Both channels send as PHONE_TO_DESKTOP
        val sender = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)
        val receiver = EncryptedChannel(key.copyOf(), EncryptedChannel.Direction.PHONE_TO_DESKTOP)

        val envelope = sender.seal("test".toByteArray())

        // Receiver expects opposite direction (DESKTOP_TO_PHONE = 0x01), but envelope has 0x00
        assertFailsWith<IllegalArgumentException> {
            receiver.open(envelope)
        }

        sender.close()
        receiver.close()
    }
}
