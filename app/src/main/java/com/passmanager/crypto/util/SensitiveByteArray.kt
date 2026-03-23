package com.passmanager.crypto.util

import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Zeroing wrapper for sensitive byte data. Implements [Closeable] so it can be
 * used with Kotlin's `use {}` to guarantee zeroing via try/finally.
 *
 * For long-lived keys (e.g. session key), prefer [directFrom] which stores data
 * in a DirectByteBuffer outside the JVM heap — immune to GC compaction/copying.
 */
class SensitiveByteArray private constructor(
    private val directBuffer: ByteBuffer?,
    private var heapArray: ByteArray?
) : Closeable {

    val size: Int
        get() = directBuffer?.capacity() ?: heapArray?.size
            ?: throw IllegalStateException("Already closed")

    /**
     * Returns a heap copy of the underlying bytes. Caller is responsible for
     * zeroing the returned array when done.
     */
    fun copyBytes(): ByteArray {
        directBuffer?.let { buf ->
            val copy = ByteArray(buf.capacity())
            synchronized(buf) {
                buf.position(0)
                buf.get(copy)
                buf.position(0)
            }
            return copy
        }
        return heapArray?.copyOf() ?: throw IllegalStateException("Already closed")
    }

    /**
     * Provides direct read access to the underlying heap array (no copy).
     * Only available for heap-backed instances. Returns null for direct-backed.
     */
    fun unsafeHeapArray(): ByteArray? = heapArray

    override fun close() {
        directBuffer?.let { buf ->
            synchronized(buf) {
                val zeros = ByteArray(buf.capacity())
                buf.position(0)
                buf.put(zeros)
            }
        }
        heapArray?.fill(0)
        heapArray = null
    }

    companion object {
        /** Wrap an existing heap array. [close] will zero it in place. */
        fun wrap(bytes: ByteArray) = SensitiveByteArray(null, bytes)

        /**
         * Move [bytes] into a DirectByteBuffer (off-heap) and zero the source.
         * The direct buffer is immune to JVM garbage-collector compaction.
         */
        fun directFrom(bytes: ByteArray): SensitiveByteArray {
            val buf = ByteBuffer.allocateDirect(bytes.size)
            buf.put(bytes)
            bytes.fill(0)
            return SensitiveByteArray(buf, null)
        }
    }
}
