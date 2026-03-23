package com.passmanager.crypto.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveByteArrayTest {

    @Test
    fun `wrap preserves data`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val sensitive = SensitiveByteArray.wrap(data)
        assertEquals(5, sensitive.size)
        assertArrayEquals(data, sensitive.copyBytes())
        sensitive.close()
    }

    @Test
    fun `close zeros heap array`() {
        val data = byteArrayOf(10, 20, 30)
        val sensitive = SensitiveByteArray.wrap(data)
        sensitive.close()
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    fun `directFrom zeros source array`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val sensitive = SensitiveByteArray.directFrom(source)
        assertTrue("Source should be zeroed", source.all { it == 0.toByte() })
        val copy = sensitive.copyBytes()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), copy)
        sensitive.close()
    }

    @Test
    fun `directFrom close zeros buffer`() {
        val source = byteArrayOf(5, 6, 7, 8)
        val sensitive = SensitiveByteArray.directFrom(source)
        sensitive.close()
        val copy = try {
            sensitive.copyBytes()
        } catch (e: IllegalStateException) {
            null
        }
        // After close on direct buffer, copyBytes may still work (buffer exists)
        // but the content should be zeros
        if (copy != null) {
            assertTrue(copy.all { it == 0.toByte() })
        }
    }

    @Test
    fun `use block auto-closes`() {
        val data = byteArrayOf(99, 88, 77)
        SensitiveByteArray.wrap(data).use { sensitive ->
            assertEquals(3, sensitive.size)
        }
        assertTrue(data.all { it == 0.toByte() })
    }
}
