package com.passmanager.domain.usecase

import android.content.Context
import com.passmanager.R
import com.passmanager.protocol.SecureResponse
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.security.DesktopPairingSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Decrypts a single password from the vault and sends it to the desktop
 * through the encrypted pairing channel.
 *
 * W1 mitigation: respects [DesktopPairingSession] rate limits.
 * W2 mitigation: uses [DecryptPasswordBytesUseCase] to extract password as ByteArray.
 */
class SendPasswordToDesktopUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val decryptPasswordBytesUseCase: DecryptPasswordBytesUseCase,
    private val session: DesktopPairingSession
) {
    /**
     * @return the title of the item whose password was sent, for notification display
     */
    suspend operator fun invoke(itemId: String): String = withContext(Dispatchers.Default) {
        if (!session.canSendPassword()) {
            session.sendSecure(
                SecureResponse.RateLimited(context.getString(R.string.desktop_password_rate_limited))
            )
            throw DesktopRateLimitException()
        }

        val item = vaultRepository.getById(itemId)
            ?: throw IllegalArgumentException("Item not found: $itemId")

        val result = decryptPasswordBytesUseCase(item)
        val passwordCopy = result.passwordBytes.copyOf()
        result.passwordBytes.fill(0)
        try {
            session.sendSecure(SecureResponse.Password(itemId = itemId, password = passwordCopy))
            session.recordPasswordSent(result.title)
            result.title
        } finally {
            passwordCopy.fill(0)
        }
    }
}

class DesktopRateLimitException : Exception("Password request rate limited")
