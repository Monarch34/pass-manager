package com.passmanager.crypto.aead

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM from Android's default provider, which is Conscrypt.
 *
 * The same code as the desktop actual and, as with the random generator, deliberately its
 * own file rather than a shared JVM source set: the code is identical, the reasoning is
 * not. Conscrypt is a different implementation from any desktop JRE's, it is updated
 * through Play system updates rather than with the application, and it is the provider this
 * project wants to keep winning. Bundling a general-purpose crypto library and registering
 * it as a JCA provider would silently take AES-GCM away from Conscrypt for the whole
 * process — that is a decision, and it should have to be made in this file rather than
 * happen as a side effect of adding a dependency somewhere else.
 *
 * No provider is named here for the same reason: `"AES/GCM/NoPadding"` resolves to
 * Conscrypt today, and to whatever replaces it, without this file changing.
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
    // AEADBadTagException extends this. Anything broader would report a broken build as a
    // wrong passphrase.
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
