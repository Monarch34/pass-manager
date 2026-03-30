package com.passmanager.crypto.model

import kotlinx.serialization.Serializable

@Serializable
data class KdfParams(
    val memory: Int = 65536,
    val iterations: Int = 10,
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
