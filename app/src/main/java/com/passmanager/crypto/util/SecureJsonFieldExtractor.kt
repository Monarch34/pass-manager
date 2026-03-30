package com.passmanager.crypto.util

/**
 * Extracts a JSON string field from raw UTF-8 JSON bytes without decoding the document
 * to a JVM [String]. All intermediate buffers are zeroed before release.
 *
 * This is intentionally byte-level so that callers can keep sensitive values (passwords,
 * passphrases) off the JVM heap as long as possible. [extractFieldBytes] operates on the
 * outer byte buffer in-place; the caller is responsible for zeroing the returned array.
 */
object SecureJsonFieldExtractor {

    /**
     * Scans [data] for a JSON key whose UTF-8 representation is `"[fieldName]"` and returns
     * the corresponding string value as a fresh [ByteArray], or `null` if the key is absent,
     * the value is not a JSON string, or the value exceeds [maxBytes].
     *
     * The returned array MUST be zeroed by the caller after use.
     */
    fun extractFieldBytes(data: ByteArray, fieldName: String, maxBytes: Int): ByteArray? {
        val fieldKey = "\"$fieldName\"".toByteArray(Charsets.UTF_8)
        val keyIndex = findBytes(data, fieldKey) ?: return null
        var i = keyIndex + fieldKey.size

        while (i < data.size && data[i].isJsonWs()) i++
        if (i >= data.size || data[i] != COLON) return null
        i++

        while (i < data.size && data[i].isJsonWs()) i++
        if (i >= data.size || data[i] != DQUOTE) return null
        i++

        val buf = ZeroableBuffer(maxBytes)
        try {
            while (i < data.size && !buf.overflow) {
                when {
                    data[i] == DQUOTE                          -> break
                    data[i] == BACKSLASH && i + 1 < data.size -> i = decodeEscape(data, i + 1, buf)
                    else                                       -> { buf.write(data[i]); i++ }
                }
            }
            return if (buf.overflow) null else buf.toByteArray()
        } finally {
            buf.zero()
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────────

    private fun findBytes(haystack: ByteArray, needle: ByteArray): Int? {
        if (needle.size > haystack.size) return null
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) { if (haystack[i + j] != needle[j]) continue@outer }
            return i
        }
        return null
    }

    private fun decodeEscape(data: ByteArray, pos: Int, buf: ZeroableBuffer): Int {
        return when (data[pos].toInt().toChar()) {
            '"'  -> { buf.write(0x22); pos + 1 }
            '\\' -> { buf.write(0x5C); pos + 1 }
            '/'  -> { buf.write(0x2F); pos + 1 }
            'n'  -> { buf.write(0x0A); pos + 1 }
            'r'  -> { buf.write(0x0D); pos + 1 }
            't'  -> { buf.write(0x09); pos + 1 }
            'b'  -> { buf.write(0x08); pos + 1 }
            'f'  -> { buf.write(0x0C); pos + 1 }
            'u'  -> {
                if (pos + 4 < data.size) {
                    val cp = hexDigit(data[pos + 1]) shl 12 or
                             (hexDigit(data[pos + 2]) shl 8)  or
                             (hexDigit(data[pos + 3]) shl 4)  or
                             hexDigit(data[pos + 4])
                    if (cp >= 0) writeUtf8(buf, cp)
                    pos + 5
                } else pos + 1
            }
            else -> { buf.write(data[pos]); pos + 1 }
        }
    }

    private fun writeUtf8(buf: ZeroableBuffer, cp: Int) {
        when {
            cp < 0x80  -> buf.write(cp)
            cp < 0x800 -> { buf.write(0xC0 or (cp shr 6)); buf.write(0x80 or (cp and 0x3F)) }
            else       -> {
                buf.write(0xE0 or (cp shr 12))
                buf.write(0x80 or ((cp shr 6) and 0x3F))
                buf.write(0x80 or (cp and 0x3F))
            }
        }
    }

    private fun hexDigit(b: Byte): Int = when (val c = b.toInt().toChar()) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else        -> -1
    }

    private fun Byte.isJsonWs(): Boolean =
        this == 0x20.toByte() || this == 0x09.toByte() ||
        this == 0x0A.toByte() || this == 0x0D.toByte()

    private val DQUOTE   = '"'.code.toByte()
    private val BACKSLASH = '\\'.code.toByte()
    private val COLON    = ':'.code.toByte()

    // ── ZeroableBuffer ────────────────────────────────────────────────────────────

    /** A fixed-capacity byte buffer that zeroes its backing array when [zero] is called. */
    private class ZeroableBuffer(capacity: Int) {
        private val buf = ByteArray(capacity)
        private var size = 0
        var overflow = false
            private set

        fun write(b: Int)  { if (size < buf.size) buf[size++] = b.toByte() else overflow = true }
        fun write(b: Byte) = write(b.toInt())
        fun toByteArray()  = buf.copyOf(size)
        fun zero()         { buf.fill(0); size = 0 }
    }
}
