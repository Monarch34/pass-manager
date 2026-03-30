package com.passmanager.di

import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.port.UnlockSessionRecorder
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.security.BiometricKeyManager
import com.passmanager.security.DesktopPairingSession
import com.passmanager.security.SessionManager
import com.passmanager.security.VaultLockManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindVaultKeyProvider(impl: VaultLockManager): VaultKeyProvider

    @Binds
    @Singleton
    abstract fun bindUnlockSessionRecorder(impl: SessionManager): UnlockSessionRecorder

    @Binds
    @Singleton
    abstract fun bindDesktopPairingPort(impl: DesktopPairingSession): DesktopPairingPort

    @Binds
    @Singleton
    abstract fun bindBiometricLockPort(impl: BiometricKeyManager): BiometricLockPort

    @Binds
    @Singleton
    abstract fun bindLockStateProvider(impl: VaultLockManager): LockStateProvider
}
