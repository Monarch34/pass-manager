package com.passmanager.domain.port

interface VaultKeyProvider {
    fun requireUnlockedKey(): ByteArray
    fun unlock(vaultKey: ByteArray)
}
