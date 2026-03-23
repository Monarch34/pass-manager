package com.passmanager.desktop.crypto

import java.io.Closeable
import java.nio.ByteBuffer

class SensitiveByteArray private constructor(
    private val directBuffer: ByteBuffer?,
    private var heapArray: ByteArray?
) : Closeable {

    val size: Int
        get() = directBuffer?.capacity() ?: heapArray?.size
            ?: throw IllegalStateException("Already closed")

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
        fun wrap(bytes: ByteArray) = SensitiveByteArray(null, bytes)

        fun directFrom(bytes: ByteArray): SensitiveByteArray {
            val buf = ByteBuffer.allocateDirect(bytes.size)
            buf.put(bytes)
            bytes.fill(0)
            return SensitiveByteArray(buf, null)
        }
    }
}
