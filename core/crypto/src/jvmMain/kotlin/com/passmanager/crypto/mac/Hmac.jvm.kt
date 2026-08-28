package com.passmanager.crypto.mac

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA-256 from the JCA provider this JRE ships. */
internal actual fun platformHmacSha256(key: ByteArray, message: ByteArray): ByteArray =
    Mac.getInstance(Algorithm).apply { init(SecretKeySpec(key, Algorithm)) }.doFinal(message)

private const val Algorithm = "HmacSHA256"
