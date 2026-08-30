package com.passmanager.kit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Bytes crossing between Kotlin and Swift, in bulk.
 *
 * Swift can index a `KotlinByteArray` one element at a time, and each of those is a message
 * send across the Objective-C bridge. That was defended in the Swift code as acceptable
 * because it happened "on unlock, on save and on attach — not in a loop". An export walks
 * every attachment in the vault, and an import walks every attachment in the file: those are
 * loops, and at five megabytes an attachment the element-at-a-time version is millions of
 * calls per file.
 *
 * `NSData` is the one shape both sides can hand over whole. Kotlin pins the array so the
 * garbage collector cannot move it, and the copy becomes a single `memcpy` in each
 * direction.
 *
 * ### Why this lives in the framework module and not in `core:`
 *
 * The core modules must not learn that an Apple platform exists — that is the boundary the
 * whole layout is built on, and `Foundation` in `core:vault` would end it. How bytes reach
 * Swift is a packaging decision, and this module is where packaging decisions live.
 */
@OptIn(ExperimentalForeignApi::class)
object Bytes {

    /**
     * A copy Swift owns.
     *
     * `NSData.create(bytes:length:)` copies rather than taking the pinned pointer, which is
     * required: the pin ends when this function returns, and an `NSData` pointing at memory
     * the collector is free to move again is a use-after-free waiting for a compaction.
     */
    fun toData(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

    /** A copy Kotlin owns. */
    fun fromData(data: NSData): ByteArray {
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        return out
    }
}
