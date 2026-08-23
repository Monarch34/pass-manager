package com.passmanager.crypto.model

import kotlinx.serialization.Serializable

/**
 * Argon2id cost parameters, persisted per vault in `vault_metadata.kdf_params_json`.
 *
 * The defaults below apply to NEWLY created vaults only. An existing vault is always
 * unlocked with the parameters stored in its own row, so lowering a default here never
 * re-derives an old vault's key at the wrong cost. `MIGRATION_7_8` guarantees that by
 * back-filling rows that were written before the parameters were stored explicitly.
 *
 * Cost rationale: OWASP's Password Storage Cheat Sheet lists m=47104/t=1/p=1,
 * m=19456/t=2/p=1 and m=12288/t=3/p=1 as acceptable Argon2id configurations. Argon2's
 * resistance comes mostly from memory, so 64 MiB — comfortably above all three references —
 * stays untouched. The iteration count is what was out of line: at t=10 a click-to-list
 * unlock measured 2.35 s on an API 36 emulator, which is unusable in an app opened dozens
 * of times a day. t=3 matches the highest-iteration OWASP reference on top of 5x its
 * memory, and costs roughly a third of what t=10 did.
 */
@Serializable
data class KdfParams(
    val memory: Int = 65536,        // 64 MiB
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val hashLength: Int = 32
) {
    init {
        require(memory in MIN_MEMORY..MAX_MEMORY) { "memory out of bounds: $memory" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations out of bounds: $iterations" }
        require(parallelism in MIN_PARALLELISM..MAX_PARALLELISM) { "parallelism out of bounds: $parallelism" }
        require(hashLength in MIN_HASH_LEN..MAX_HASH_LEN) { "hashLength out of bounds: $hashLength" }
    }

    companion object {
        const val MIN_MEMORY = 8192          // 8 MiB floor
        const val MAX_MEMORY = 1_048_576     // 1 GiB ceiling
        const val MIN_ITERATIONS = 1
        const val MAX_ITERATIONS = 100
        const val MIN_PARALLELISM = 1
        const val MAX_PARALLELISM = 16
        const val MIN_HASH_LEN = 16
        const val MAX_HASH_LEN = 64
    }
}
