package com.passmanager.domain.usecase

import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.port.PepperPort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import javax.inject.Inject

/**
 * Destroys the vault: every item, its metadata, and both device keys.
 *
 * This exists for exactly one situation — a device-bound vault whose Keystore key is permanently
 * gone. Nothing can open that vault again, and importing a `.pmvault` backup needs an unlocked
 * vault to import *into*, so without a way to start over the user would be stuck holding a valid
 * backup they cannot restore. This is that way out, and it is why the recovery screen demands
 * typed confirmation before calling it.
 *
 * Order matters: rows first, keys last. If the process dies midway the leftover keys are inert
 * (nothing references them), whereas deleting keys first would leave rows that look openable and
 * are not.
 */
class ResetVaultUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val metadataRepository: MetadataRepository,
    private val biometricLockPort: BiometricLockPort,
    private val pepper: PepperPort,
    private val lockStateProvider: LockStateProvider
) {
    suspend operator fun invoke() {
        // Only reachable from the lock screen today, so the key is already null - but the
        // guarantee that an erased vault leaves no key in memory belongs here, not in a
        // navigation graph that a later screen could route around.
        lockStateProvider.lock()
        vaultRepository.deleteAll()
        metadataRepository.delete()
        // Clears the biometric wrapped key and its Keystore alias. Tolerates an already-empty
        // vault: by this point the metadata row is gone, so it is a no-op rather than a failure.
        runCatching { biometricLockPort.disable() }
        pepper.deleteKey()
    }
}
