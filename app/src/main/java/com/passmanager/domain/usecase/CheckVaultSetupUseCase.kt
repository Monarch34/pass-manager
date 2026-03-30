package com.passmanager.domain.usecase

import com.passmanager.domain.repository.MetadataRepository
import javax.inject.Inject

/** Returns true if the vault has been initialized (metadata row exists). */
class CheckVaultSetupUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository
) {
    suspend operator fun invoke(): Boolean = metadataRepository.isVaultSetup()
}
