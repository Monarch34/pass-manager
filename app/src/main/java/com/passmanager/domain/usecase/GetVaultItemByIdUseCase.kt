package com.passmanager.domain.usecase

import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.repository.VaultRepository
import javax.inject.Inject

/** One-shot load of a full vault row (e.g. add/edit screen). */
class GetVaultItemByIdUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(id: String): VaultItem? = vaultRepository.getById(id)
}
