package com.passmanager.crypto.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.exception.DeviceKeyLostException
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.port.PepperPort
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device-bound "pepper" key: a single AES-256-GCM key in the Android Keystore that seals the
 * outer layer of a v2 vault's wrapped key.
 *
 * Deliberately **separate from the biometric key** and unlike it in every property that matters:
 *
 * - `setUserAuthenticationRequired(false)`. This key must work on the passphrase path, before any
 *   biometric exists and on devices that have none. Requiring auth here would make the passphrase
 *   unlock depend on biometrics, which is exactly backwards.
 * - `setInvalidatedByBiometricEnrollment` is not set, because it only applies to auth-bound keys.
 *   Enrolling a new fingerprint must not touch this key — that invalidation is survivable for the
 *   biometric key (the passphrase still works) but here it would be total, unrecoverable loss.
 * - `setUnlockedDeviceRequired` **only on API 31+**. On 28-30 several OEM Keystore
 *   implementations report a locked device in ways that are indistinguishable from permanent
 *   invalidation, and the cost of guessing wrong is a vault nobody can open again.
 *
 * StrongBox is attempted on API 28+ and falls back to the TEE, which is the ordinary outcome on
 * most devices. `setRandomizedEncryptionRequired` is left at its default of true, so the Keystore
 * generates every IV and refuses a caller-supplied one — never relax it.
 */
@Singleton
class PepperKeyManager @Inject constructor() : PepperPort {

    companion object {
        const val KEY_ALIAS = "passmanager_pepper_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }

    override fun isKeyPresent(): Boolean = try {
        keyStore.containsAlias(KEY_ALIAS)
    } catch (e: GeneralSecurityException) {
        // An unreadable Keystore is not proof the key is gone; callers treat false as "no device
        // binding available yet", never as "the vault is lost".
        false
    }

    override fun ensureKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) return
            generateKey()
        } catch (e: GeneralSecurityException) {
            throw DeviceKeyUnavailableException(e)
        } catch (e: ProviderException) {
            throw DeviceKeyUnavailableException(e)
        }
    }

    override fun deleteKey() {
        try {
            val store = keyStore
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        } catch (e: GeneralSecurityException) {
            // Reset is best-effort by design: the vault rows are already gone by the time this
            // runs, so a key that outlives them is inert, and throwing here would strand the user
            // on the recovery screen with nothing left to recover.
        }
    }

    override fun seal(plaintext: ByteArray): EncryptedData = classify {
        val cipher = Cipher.getInstance(ALGORITHM)
        // No GCMParameterSpec: the Keystore picks the IV and hands it back on the cipher.
        cipher.init(Cipher.ENCRYPT_MODE, requireKey())
        EncryptedData(ciphertext = cipher.doFinal(plaintext), iv = cipher.iv)
    }

    override fun open(sealed: EncryptedData): ByteArray = classify {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            requireKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, sealed.iv)
        )
        cipher.doFinal(sealed.ciphertext)
    }

    private fun requireKey(): SecretKey {
        val entry = try {
            keyStore.getEntry(KEY_ALIAS, null)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw DeviceKeyLostException(e)
        } catch (e: GeneralSecurityException) {
            throw DeviceKeyUnavailableException(e)
        }
        val secretKey = (entry as? KeyStore.SecretKeyEntry)?.secretKey
        // A missing alias is the one state that is genuinely unrecoverable: nothing regenerates the
        // same key, so every vault sealed under it stays sealed.
        return secretKey ?: throw DeviceKeyLostException()
    }

    /**
     * Maps Keystore failures onto the two outcomes the UI can act on. The default is deliberately
     * "unavailable, try again": only the two provably permanent states earn the loss screen,
     * because that screen's only exit is wiping the vault.
     */
    private inline fun <T> classify(block: () -> T): T = try {
        block()
    } catch (e: AEADBadTagException) {
        // Not a key problem at all: the key worked and said these bytes are not what it sealed.
        // The unwrapper needs to see this to tell a wrong passphrase from a lost device key.
        throw e
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw DeviceKeyLostException(e)
    } catch (e: DeviceKeyLostException) {
        throw e
    } catch (e: DeviceKeyUnavailableException) {
        throw e
    } catch (e: GeneralSecurityException) {
        throw DeviceKeyUnavailableException(e)
    } catch (e: ProviderException) {
        // GeneralSecurityException's runtime sibling — how the AndroidKeyStore provider reports a
        // keymaster that is busy, wedged, or momentarily refusing work.
        throw DeviceKeyUnavailableException(e)
    } catch (e: IllegalStateException) {
        throw DeviceKeyUnavailableException(e)
    }

    private fun generateKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                buildKey(strongBoxBacked = true)
                return
            } catch (e: StrongBoxUnavailableException) {
                deleteKey()
            } catch (e: ProviderException) {
                // Some OEMs report a missing StrongBox as a plain ProviderException rather than
                // the documented subclass. Retrying on the TEE costs nothing and is the difference
                // between a working vault and onboarding that cannot complete on that device.
                deleteKey()
            }
        }
        buildKey(strongBoxBacked = false)
    }

    private fun buildKey(strongBoxBacked: Boolean) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .apply {
                if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setUnlockedDeviceRequired(true)
                }
            }
            .build()

        generator.init(spec)
        generator.generateKey()
    }
}
