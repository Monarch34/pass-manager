package com.passmanager.domain.usecase

import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Vault list: observe header rows without loading full encrypted blobs per item. */
class ObserveVaultHeadersUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
) {
    operator fun invoke(): Flow<List<VaultItemHeader>> = vaultRepository.observeHeaders()
}
