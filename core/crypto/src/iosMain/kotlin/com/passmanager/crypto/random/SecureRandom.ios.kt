package com.passmanager.crypto.random

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/**
 * Apple's CSPRNG, via Security.framework.
 *
 * **The status code is checked.** `SecRandomCopyBytes` reports failure through its return
 * value and not by throwing, so ignoring it means handing back a buffer that is still full
 * of zeros and calling it a key. That failure is silent, total, and indistinguishable from
 * success at every call site — a vault sealed under an all-zero key looks perfectly normal
 * until someone else opens it. It costs one comparison to make impossible.
 *
 * `usePinned` keeps the array at a fixed address for the duration of the call so the
 * garbage collector cannot move it out from under the C function writing into it.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformSecureRandomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    val status = out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    check(status == errSecSuccess) {
        "SecRandomCopyBytes could not produce $size bytes (OSStatus $status)"
    }
    return out
}
