package com.passmanager.crypto.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KdfParamsTest {

    @Test
    fun `default params construct successfully`() {
        val params = KdfParams()
        assertEquals(65536, params.memory)
        assertEquals(10, params.iterations)
        assertEquals(4, params.parallelism)
        assertEquals(32, params.hashLength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `memory below minimum throws`() {
        KdfParams(memory = KdfParams.MIN_MEMORY - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `memory above maximum throws`() {
        KdfParams(memory = KdfParams.MAX_MEMORY + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `iterations below minimum throws`() {
        KdfParams(iterations = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parallelism above maximum throws`() {
        KdfParams(parallelism = KdfParams.MAX_PARALLELISM + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hashLength below minimum throws`() {
        KdfParams(hashLength = KdfParams.MIN_HASH_LEN - 1)
    }

    @Test
    fun `boundary values are accepted`() {
        KdfParams(memory = KdfParams.MIN_MEMORY)
        KdfParams(memory = KdfParams.MAX_MEMORY)
        KdfParams(iterations = KdfParams.MIN_ITERATIONS)
        KdfParams(iterations = KdfParams.MAX_ITERATIONS)
        KdfParams(parallelism = KdfParams.MIN_PARALLELISM)
        KdfParams(parallelism = KdfParams.MAX_PARALLELISM)
        KdfParams(hashLength = KdfParams.MIN_HASH_LEN)
        KdfParams(hashLength = KdfParams.MAX_HASH_LEN)
    }
}
