package com.passmanager.domain.usecase

import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.repository.MetadataRepository
import javax.inject.Inject

/**
 * Moves an existing passphrase-only vault to device-bound wrapping.
 *
 * No passphrase and no Argon2 run: the inner ciphertext is re-sealed exactly as it sits on disk
 * (see [VaultKeyWrapper.sealExisting]), so the only thing that changes is a layer *around* bytes
 * that are already correct. That keeps the operation atomic in practice — one metadata write —
 * instead of a re-derive-and-rewrite window where a crash could leave a vault nobody can open.
 *
 * This is irreversible by design: once the key is sealed under the Keystore, losing the device or
 * the key means the vault only comes back from a `.pmvault` backup. The UI must therefore gate
 * the call on a completed export.
 *
 * @throws com.passmanager.domain.exception.DeviceKeyUnavailableException the Keystore refused;
 *   the vault is untouched and the user can retry.
 */
class UpgradeVaultToDeviceBoundUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val keyWrapper: VaultKeyWrapper,
    private val lockStateProvider: LockStateProvider
) {
    /** @return true if the vault was upgraded, false if it already carried a device layer. */
    suspend operator fun invoke(): Boolean {
        // Not cryptographically required — the sealing never touches the vault key — but an
        // unlocked vault proves someone is present and has just proven they know the passphrase,
        // which is the right bar for an irreversible change.
        check(lockStateProvider.lockState.value is LockState.Unlocked) {
            "Vault must be unlocked to enable device binding"
        }
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        if (metadata.isDeviceBound) return false

        val wrapped = keyWrapper.sealExisting(metadata)
        metadataRepository.update(
            metadata.copy(
                wrappedVaultKey = wrapped.onDisk,
                wrapVersion = wrapped.wrapVersion,
                pepperIv = wrapped.pepperIv
            )
        )
        return true
    }
}
