package com.passmanager.crypto.channel

import com.passmanager.protocol.ItemSummary
import com.passmanager.protocol.SecureMessageCbor
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureMessageCborTest {

    @Test
    fun `password response round trip keeps raw bytes`() {
        val secret = byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x92.toByte(), 0xAA.toByte())
        val original = SecureResponse.Password(itemId = "item-1", password = secret)
        val encoded = SecureMessageCbor.encodeResponse(original)
        val decoded = SecureMessageCbor.decodeResponse(encoded)
        assertTrue(decoded is SecureResponse.Password)
        val pw = decoded as SecureResponse.Password
        assertEquals("item-1", pw.itemId)
        assertArrayEquals(secret, pw.password)
    }

    @Test
    fun `request round trip`() {
        val req = SecureRequest.GetPassword("abc")
        val bytes = SecureMessageCbor.encodeRequest(req)
        val back = SecureMessageCbor.decodeRequest(bytes)
        assertTrue(back is SecureRequest.GetPassword)
        assertEquals("abc", (back as SecureRequest.GetPassword).itemId)
    }

    @Test
    fun `items response round trip`() {
        val items = listOf(
            ItemSummary(id = "1", title = "Test", url = "https://x.com", category = "login")
        )
        val original = SecureResponse.Items(items)
        val decoded = SecureMessageCbor.decodeResponse(SecureMessageCbor.encodeResponse(original))
        assertTrue(decoded is SecureResponse.Items)
        assertEquals(1, (decoded as SecureResponse.Items).items.size)
        assertEquals("Test", decoded.items[0].title)
    }
}
