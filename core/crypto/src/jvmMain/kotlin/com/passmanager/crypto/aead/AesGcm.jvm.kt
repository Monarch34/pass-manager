package com.passmanager.crypto.aead

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM from the JCA provider this JRE ships.
 *
 * No provider is named and none is installed. Asking for `"AES/GCM/NoPadding"` and taking
 * whatever the runtime supplies means the implementation is the one the JRE vendor patches,
 * and on any current desktop JRE that is a constant-time, hardware-accelerated one. Pinning
 * a bundled provider instead would freeze this code to whatever version shipped with the
 * application and put the project in the business of tracking cipher advisories.
 */
internal actual fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray,
): ByteArray = cipher(Cipher.ENCRYPT_MODE, key, nonce, associatedData).doFinal(plaintext)

internal actual fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    associatedData: ByteArray,
): ByteArray? = try {
    cipher(Cipher.DECRYPT_MODE, key, nonce, associatedData).doFinal(sealed)
} catch (_: BadPaddingException) {
    // AEADBadTagException extends this, and it is what a failed tag arrives as. Nothing
    // broader is caught: a missing algorithm or an invalid key length is a broken build,
    // not a wrong passphrase, and must not be reported as one.
    null
}

private fun cipher(
    mode: Int,
    key: ByteArray,
    nonce: ByteArray,
    associatedData: ByteArray,
): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
    init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(AesGcm.TagSize * 8, nonce))
    updateAAD(associatedData)
}
