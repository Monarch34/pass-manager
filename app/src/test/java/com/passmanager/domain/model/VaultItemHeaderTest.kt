package com.passmanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VaultItemHeaderTest {

    private fun header(
        id: String = "1",
        encTitle: ByteArray? = byteArrayOf(1, 2, 3),
        titleIv: ByteArray? = byteArrayOf(4, 5, 6),
        encAddr: ByteArray? = byteArrayOf(7, 8, 9),
        addrIv: ByteArray? = byteArrayOf(10, 11, 12),
        category: ItemCategory = ItemCategory.LOGIN,
        updatedAt: Long = 100L
    ) = VaultItemHeader(id, encTitle, titleIv, encAddr, addrIv, category, updatedAt)

    @Test
    fun `equal headers with same ByteArray content`() {
        val a = header(encTitle = byteArrayOf(1, 2, 3))
        val b = header(encTitle = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `unequal headers with different content`() {
        val a = header(encTitle = byteArrayOf(1, 2, 3))
        val b = header(encTitle = byteArrayOf(9, 9, 9))
        assertNotEquals(a, b)
    }

    @Test
    fun `null fields handled in equality`() {
        val a = header(encTitle = null, titleIv = null, encAddr = null, addrIv = null)
        val b = header(encTitle = null, titleIv = null, encAddr = null, addrIv = null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `null vs non-null are not equal`() {
        val a = header(encTitle = null)
        val b = header(encTitle = byteArrayOf(1, 2, 3))
        assertNotEquals(a, b)
    }

    @Test
    fun `different ids are not equal`() {
        val a = header(id = "1")
        val b = header(id = "2")
        assertNotEquals(a, b)
    }
}
