package com.passmanager.domain.usecase

import com.passmanager.protocol.ItemSummary
import com.passmanager.protocol.SecureResponse
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.port.DesktopPairingPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SendItemListToDesktopUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptItemHeaderUseCase: DecryptItemHeaderUseCase,
    private val session: DesktopPairingPort
) {
    suspend operator fun invoke() = withContext(Dispatchers.Default) {
        val headers = vaultRepository.getHeaders()

        val summaries = mutableListOf<ItemSummary>()
        var failedCount = 0

        for (header in headers) {
            try {
                val decrypted = decryptItemHeaderUseCase(header)
                val title = decrypted.title
                if (title != null) {
                    summaries.add(
                        ItemSummary(
                            id = header.id,
                            title = title,
                            url = decrypted.address ?: "",
                            category = header.category.dbKey
                        )
                    )
                } else {
                    failedCount++
                }
            } catch (_: Exception) {
                failedCount++
            }
        }

        session.sendSecure(SecureResponse.Items(items = summaries, failedCount = failedCount))
    }
}
