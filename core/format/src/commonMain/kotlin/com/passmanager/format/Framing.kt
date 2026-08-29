package com.passmanager.format

/**
 * Big-endian integer reads and writes, bounds-checked.
 *
 * Every one of these runs on bytes an attacker supplied, before anything has been
 * authenticated. A read that runs off the end of the array must produce a typed "this file
 * is short" answer and not an `IndexOutOfBoundsException` escaping from inside a parser, so
 * the reader checks the remaining length itself rather than letting the array do it.
 */
internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xff

internal fun ByteArray.u16(offset: Int): Int =
    (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long =
    (u8(offset).toLong() shl 24) or
        (u8(offset + 1).toLong() shl 16) or
        (u8(offset + 2).toLong() shl 8) or
        u8(offset + 3).toLong()

internal fun ByteArray.putU8(offset: Int, value: Int) {
    this[offset] = value.toByte()
}

internal fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.putU32(offset: Int, value: Long) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

/** True when [count] bytes can be read at [offset] without running past the end. */
internal fun ByteArray.has(offset: Int, count: Int): Boolean =
    offset >= 0 && count >= 0 && offset.toLong() + count <= size.toLong()
