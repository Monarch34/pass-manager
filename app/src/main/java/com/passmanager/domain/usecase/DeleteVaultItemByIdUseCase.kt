package com.passmanager.domain.usecase

import com.passmanager.domain.repository.VaultRepository
import javax.inject.Inject

/** Delete a single vault row by id. */
class DeleteVaultItemByIdUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(id: String) = vaultRepository.deleteById(id)
}
