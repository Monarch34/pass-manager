package com.passmanager.di

import com.passmanager.data.db.VaultDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Used to touch [VaultDatabase] from a background thread in [com.passmanager.app.PassManagerApp]
 * so the first frame of Compose is less likely to block on Room opening the DB file.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VaultDatabaseEntryPoint {
    fun vaultDatabase(): VaultDatabase
}
