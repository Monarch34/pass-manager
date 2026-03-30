package com.passmanager.domain.usecase

import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observe a single vault row (e.g. view item reacts to external edits/sync). */
class ObserveVaultItemByIdUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
) {
    operator fun invoke(id: String): Flow<VaultItem?> = vaultRepository.observeById(id)
}
