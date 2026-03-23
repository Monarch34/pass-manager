package com.passmanager.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.data.db.VaultDatabase
import com.passmanager.domain.model.VaultMetadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataRepositoryImplTest {

    private lateinit var database: VaultDatabase
    private lateinit var repository: MetadataRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
        repository = MetadataRepositoryImpl(database.vaultMetadataDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun isVaultSetup_returnsFalseInitially() = runTest {
        assertFalse(repository.isVaultSetup())
    }

    @Test
    fun get_returnsNullInitially() = runTest {
        assertNull(repository.get())
    }

    @Test
    fun saveAndGet_returnsSavedMetadata() = runTest {
        val metadata = VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
            kdfSalt = ByteArray(16),
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null
        )
        repository.save(metadata)
        val retrieved = repository.get()
        assertEquals(1, retrieved?.currentKeyVersion)
        assertFalse(retrieved?.biometricEnabled == true)
    }

    @Test
    fun isVaultSetup_returnsTrueAfterSave() = runTest {
        val metadata = VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
            kdfSalt = ByteArray(16),
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null
        )
        repository.save(metadata)
        assertTrue(repository.isVaultSetup())
    }

    @Test
    fun update_modifiesExistingMetadata() = runTest {
        val metadata = VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
            kdfSalt = ByteArray(16),
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null
        )
        repository.save(metadata)
        val updated = metadata.copy(biometricEnabled = true)
        repository.update(updated)
        val retrieved = repository.get()
        assertTrue(retrieved?.biometricEnabled == true)
    }
}
