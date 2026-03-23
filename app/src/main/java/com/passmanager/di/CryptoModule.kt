package com.passmanager.di

import com.passmanager.crypto.kdf.Argon2KdfProvider
import com.passmanager.crypto.kdf.KdfProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindKdfProvider(impl: Argon2KdfProvider): KdfProvider
}
