package com.passmanager.domain.usecase

import com.passmanager.protocol.SecureResponse
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.port.DesktopPairingPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.passmanager.domain.exception.DesktopRateLimitException

class SendPasswordToDesktopUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptPasswordBytesUseCase: DecryptPasswordBytesUseCase,
    private val session: DesktopPairingPort
) {
    suspend operator fun invoke(itemId: String): String = withContext(Dispatchers.Default) {
        if (!session.canSendPassword()) {
            session.sendSecure(SecureResponse.RateLimited("Password request rate limited"))
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
