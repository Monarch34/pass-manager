package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData

data class VaultItem(
    val id: String,
    val encryptedData: EncryptedData,
    val keyVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String = "login"
)
