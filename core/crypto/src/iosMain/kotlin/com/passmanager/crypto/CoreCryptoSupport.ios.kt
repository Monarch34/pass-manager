package com.passmanager.crypto

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * Hands a C function a stable pointer to this array's bytes.
 *
 * Pinning keeps the array at one address for the duration of the call, so the garbage
 * collector cannot move it while C code is reading or writing through the pointer.
 *
 * The empty case needs its own answer. Every CommonCrypto entry point used here takes a
 * pointer and a length, and passing zero as the length means the pointer is never
 * dereferenced — but `addressOf(0)` on an empty array is an out-of-bounds index and throws
 * before the call happens. Empty inputs are ordinary here (a message with no associated
 * data, a plaintext of nothing), so a one-byte stand-in supplies an address that is valid
 * and never read.
 */
@OptIn(ExperimentalForeignApi::class)
internal inline fun <R> ByteArray.usePointer(block: (CPointer<ByteVar>) -> R): R {
    val storage = if (isEmpty()) EmptyPlaceholder else this
    return storage.usePinned { block(it.addressOf(0)) }
}

/** Only ever passed with a length of zero, so nothing reads or writes it. */
@PublishedApi
internal val EmptyPlaceholder = ByteArray(1)
