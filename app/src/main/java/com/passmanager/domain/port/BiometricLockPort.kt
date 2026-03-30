package com.passmanager.domain.port

import kotlinx.coroutines.flow.Flow
import javax.crypto.Cipher

interface BiometricLockPort {
    suspend fun isHardwareAvailable(): Boolean
    fun observeIsEnrolled(): Flow<Boolean>
    suspend fun isAvailable(): Boolean
    fun prepareEnrollment(): Cipher
    suspend fun completeEnrollment(authenticatedCipher: Cipher)
    suspend fun disable()
    suspend fun disableIfEnabled()
    suspend fun createAuthCipher(): Cipher
    suspend fun unlock(authenticatedCipher: Cipher)
}
