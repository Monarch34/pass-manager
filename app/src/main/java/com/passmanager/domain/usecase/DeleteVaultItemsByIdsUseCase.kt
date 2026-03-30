package com.passmanager.domain.usecase

import com.passmanager.domain.repository.VaultRepository
import javax.inject.Inject

/** Batch delete vault rows (e.g. vault list selection). */
class DeleteVaultItemsByIdsUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(ids: List<String>) = vaultRepository.deleteByIds(ids)
}
