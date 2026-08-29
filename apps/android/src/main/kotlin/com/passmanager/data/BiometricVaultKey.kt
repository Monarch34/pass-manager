package com.passmanager.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The vault key, sealed by a Keystore key that only a fingerprint or a face can use.
 *
 * The mechanism differs from the one on Apple and the difference is worth stating, because
 * the security property is the same and the shape is not. Apple's Keychain stores arbitrary
 * bytes and gates them on biometry directly. The Android Keystore stores *keys*, and the
 * hardware never lets their material out — so the vault key cannot be put inside it. Instead
 * a key is created inside the Keystore that cannot be used without authentication, and the
 * vault key is encrypted with it. The ciphertext then sits in ordinary application storage,
 * where it is inert: without the Keystore key, which is bound to this device and to the
 * current enrolment, it is 60 bytes of noise.
 *
 * ### The three properties doing the work
 *
 * `setUserAuthenticationRequired(true)` with a validity of zero means the key cannot be used
 * at all except through a `CryptoObject` the system hands back after a successful
 * authentication. The check is the Keystore's, not this code's — there is no branch here for
 * an attacker to step over.
 *
 * `setInvalidatedByBiometricEnrollment(true)` destroys the key the moment a fingerprint or
 * face is added. That is the counterpart to `.biometryCurrentSet` on Apple, and it exists
 * for the same reason: somebody who knows the device PIN can enrol their own finger, and
 * without this that would hand them the vault.
 *
 * `setUnlockedDeviceRequired(true)` **only from API 31**. On 28 to 30 several manufacturers'
 * Keystore implementations report a locked device in a way that is indistinguishable from
 * permanent invalidation, and the cost of reading it wrong is a vault nobody can open again.
 * This is inherited from version 1, where it was learned the expensive way.
 */
class BiometricVaultKey(private val context: Context) {

    /** Inert without the Keystore key, so it needs no protection of its own. */
    private val blob = File(context.filesDir, "biometric.bin")

    enum class Availability { Ready, NotEnrolled, NoHardware, Unavailable }

    fun availability(): Availability =
        when (BiometricManager.from(context).canAuthenticate(Strong)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.NoHardware
            else -> Availability.Unavailable
        }

    fun isEnabled(): Boolean = blob.exists()

    /**
     * A cipher for storing the vault key, and the key it needs, created fresh.
     *
     * The old key is deleted first. Reusing one would keep whatever enrolment it was bound
     * to, so turning the feature off and on again would not re-bind it to the fingerprints
     * enrolled now.
     */
    fun cipherForStoring(): Cipher {
        deleteKey()
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        return cipher
    }

    /**
     * A cipher for reading the vault key back, or null if there is nothing to read or the
     * key no longer exists.
     *
     * A `KeyPermanentlyInvalidatedException` here means enrolment changed. The stored blob is
     * removed rather than left behind: it can never be decrypted again, and leaving it would
     * make the application offer a door that cannot open.
     */
    fun cipherForLoading(): Result<Cipher> = runCatching {
        val stored = blob.readBytes()
        check(stored.size > IvSize) { "the stored key is truncated" }
        val cipher = Cipher.getInstance(Transformation)
        val key = loadKey() ?: error("no biometric key")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, stored, 0, IvSize))
        cipher
    }.onFailure { if (it is KeyPermanentlyInvalidatedException) remove() }

    /** Writes the sealed vault key. The cipher must be one the system has authenticated. */
    fun store(cipher: Cipher, vaultKey: ByteArray) {
        val sealed = cipher.doFinal(vaultKey)
        // The Keystore generates the nonce and refuses a caller-supplied one, so it is read
        // back off the cipher rather than chosen here.
        blob.writeBytes(cipher.iv + sealed)
    }

    fun load(cipher: Cipher): ByteArray {
        val stored = blob.readBytes()
        return cipher.doFinal(stored, IvSize, stored.size - IvSize)
    }

    fun remove() {
        blob.delete()
        deleteKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(Provider).apply { load(null) }

    private fun loadKey(): SecretKey? = keyStore().getKey(Alias, null) as? SecretKey

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(Alias) }
    }

    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            Alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Zero seconds means "authenticate for every single use", which is what forces
            // every call through a CryptoObject.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setUnlockedDeviceRequired(true)
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val Provider = "AndroidKeyStore"
        const val Alias = "passmanager.biometric.v2"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagBits = 128

        /**
         * Strong biometrics only, and deliberately not `DEVICE_CREDENTIAL`. The device PIN
         * is not this vault's passphrase; accepting it would let anyone who knows the PIN
         * open a vault they have no passphrase for. The fallback is the passphrase.
         */
        const val Strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}
