package com.passmanager.di

import android.content.Context
import androidx.room.Room
import com.passmanager.data.db.VaultDatabase
import com.passmanager.data.db.dao.VaultItemDao
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.repository.MetadataRepositoryImpl
import com.passmanager.data.repository.VaultRepositoryImpl
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault.db"
        )
            .addMigrations(
                VaultDatabase.MIGRATION_1_2,
                VaultDatabase.MIGRATION_2_3,
                VaultDatabase.MIGRATION_3_4,
                VaultDatabase.MIGRATION_4_5,
                VaultDatabase.MIGRATION_5_6
            )
            .build()

    @Provides
    @Singleton
    fun provideVaultItemDao(db: VaultDatabase): VaultItemDao = db.vaultItemDao()

    @Provides
    @Singleton
    fun provideVaultMetadataDao(db: VaultDatabase): VaultMetadataDao = db.vaultMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindMetadataRepository(impl: MetadataRepositoryImpl): MetadataRepository
}
