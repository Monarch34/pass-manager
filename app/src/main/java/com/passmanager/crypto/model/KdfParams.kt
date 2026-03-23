package com.passmanager.crypto.model

import kotlinx.serialization.Serializable

@Serializable
data class KdfParams(
    val memory: Int = 65536,  // 64 MiB (OWASP recommendation)
    val iterations: Int = 10, // OWASP minimum for offline-attack resistance (was 4)
    val parallelism: Int = 4,
    val hashLength: Int = 32
)
