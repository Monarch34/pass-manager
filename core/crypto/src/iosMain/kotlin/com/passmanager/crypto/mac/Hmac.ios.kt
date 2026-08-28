package com.passmanager.crypto.mac

import com.passmanager.crypto.usePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

/**
 * HMAC-SHA-256 from CommonCrypto.
 *
 * CryptoKit would be the modern choice on Apple and is unreachable from here: it is a
 * Swift-only framework, and Kotlin/Native can bind Objective-C and C but not Swift. Taking
 * it would mean the implementation lived in the iOS application, which is precisely where
 * shared cryptography must not live — the simulator tests that prove this construction
 * works on Apple run without any application at all.
 *
 * `CCHmac` returns nothing and cannot fail: it takes an algorithm the compiler has already
 * checked and writes a fixed-size digest.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformHmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    key.usePointer { keyBytes ->
        message.usePointer { messageBytes ->
            digest.usePointer { out ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyBytes, key.size.convert(),
                    messageBytes, message.size.convert(),
                    out,
                )
            }
        }
    }
    return digest
}
