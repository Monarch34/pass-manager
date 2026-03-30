package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val MAX_CONCURRENT_HEADER_DECRYPT = 4

/**
 * Decrypts vault list header rows (fast path via [DecryptItemHeaderUseCase], or legacy
 * full-blob decrypt + encrypted header column backfill). Intended for [VaultListViewModel] only.
 */
class ProcessVaultListHeadersUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val decryptItemHeaderUseCase: DecryptItemHeaderUseCase,
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider,
) {

    data class DecryptedHeaderRow(
        val id: String,
        val title: String,
        val address: String,
        val updatedAt: Long
    )

    data class VaultListHeaderProcessOutcome(
        val rows: List<DecryptedHeaderRow>,
        val hadDecryptFailure: Boolean
    )

    suspend operator fun invoke(stale: List<VaultItemHeader>): VaultListHeaderProcessOutcome {
        if (stale.isEmpty()) return VaultListHeaderProcessOutcome(emptyList(), false)
        return withContext(Dispatchers.Default) {
            data class HeaderResult(
                val id: String,
                val title: String,
                val address: String,
                val updatedAt: Long
            )

            val decryptSemaphore = Semaphore(MAX_CONCURRENT_HEADER_DECRYPT)
            val hadDecryptFailure = AtomicBoolean(false)
            val rows = coroutineScope {
                stale.map { header ->
                    async {
                        decryptSemaphore.withPermit {
                            try {
                                if (header.encryptedTitle != null) {
                                    val r = decryptItemHeaderUseCase(header)
                                    HeaderResult(
                                        id = header.id,
                                        title = r.title ?: "",
                                        address = r.address ?: "",
                                        updatedAt = header.updatedAt
                                    )
                                } else {
                                    val fullItem = vaultRepository.getById(header.id)
                                    if (fullItem == null) {
                                        hadDecryptFailure.set(true)
                                        null
                                    } else {
                                        val payload = decryptItemUseCase(fullItem)
                                        val subtitle = payload.listSubtitle

                                        val vaultKey = vaultKeyProvider.requireUnlockedKey()
                                        try {
                                            val titleBytes = payload.title.toByteArray(Charsets.UTF_8)
                                            val encTitle = cipher.encrypt(titleBytes, vaultKey)
                                            titleBytes.fill(0)

                                            val encAddress = if (subtitle.isNotEmpty()) {
                                                val addrBytes = subtitle.toByteArray(Charsets.UTF_8)
                                                cipher.encrypt(addrBytes, vaultKey).also { addrBytes.fill(0) }
                                            } else null

                                            vaultRepository.updateHeaderColumns(
                                                id = header.id,
                                                headerEncryption = HeaderEncryption(encTitle, encAddress)
                                            )

                                            HeaderResult(
                                                id = header.id,
                                                title = payload.title,
                                                address = subtitle,
                                                updatedAt = header.updatedAt
                                            )
                                        } finally {
                                            vaultKey.fill(0)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                hadDecryptFailure.set(true)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            VaultListHeaderProcessOutcome(
                rows = rows.map {
                    DecryptedHeaderRow(it.id, it.title, it.address, it.updatedAt)
                },
                hadDecryptFailure = hadDecryptFailure.get()
            )
        }
    }
}
