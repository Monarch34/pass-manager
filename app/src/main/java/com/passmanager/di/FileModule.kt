package com.passmanager.di

import com.passmanager.data.file.ContentResolverVaultFilePort
import com.passmanager.domain.port.VaultFilePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileModule {

    @Binds
    @Singleton
    abstract fun bindVaultFilePort(impl: ContentResolverVaultFilePort): VaultFilePort
}
