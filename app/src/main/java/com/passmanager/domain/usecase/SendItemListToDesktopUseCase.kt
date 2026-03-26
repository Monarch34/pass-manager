package com.passmanager.domain.usecase

import com.passmanager.protocol.ItemSummary
import com.passmanager.protocol.SecureResponse
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.security.DesktopPairingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Decrypts item headers (title + URL only, no secrets) and sends the list
 * to the desktop over the encrypted channel.
 */
class SendItemListToDesktopUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptItemHeaderUseCase: DecryptItemHeaderUseCase,
    private val session: DesktopPairingSession
) {
    suspend operator fun invoke() = withContext(Dispatchers.Default) {
        val headers = vaultRepository.observeHeaders().first()

        val summaries = headers.mapNotNull { header ->
            try {
                val decrypted = decryptItemHeaderUseCase(header)
                ItemSummary(
                    id = header.id,
                    title = decrypted.title ?: return@mapNotNull null,
                    url = decrypted.address ?: "",
                    category = header.category
                )
            } catch (_: Exception) {
                null
            }
        }

        session.sendSecure(SecureResponse.Items(items = summaries))
    }
}
