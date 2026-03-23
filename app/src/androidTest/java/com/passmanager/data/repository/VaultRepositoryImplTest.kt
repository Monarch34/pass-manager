package com.passmanager.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.data.db.VaultDatabase
import com.passmanager.domain.model.VaultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultRepositoryImplTest {

    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
        repository = VaultRepositoryImpl(database.vaultItemDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun observeAll_returnsEmptyInitially() = runTest {
        val items = repository.observeAll().first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun insertAndObserve_returnsInsertedItem() = runTest {
        repository.insert(
            id = "1",
            encryptedData = EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 1000L
        )
        val items = repository.observeAll().first()
        assertEquals(1, items.size)
        assertEquals("1", items[0].id)
        assertEquals(1000L, items[0].createdAt)
    }

    @Test
    fun getById_returnsNullWhenNotFound() = runTest {
        val item = repository.getById("nonexistent")
        assertNull(item)
    }

    @Test
    fun getById_returnsItemWhenExists() = runTest {
        repository.insert(
            id = "2",
            encryptedData = EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 2000L
        )
        val item = repository.getById("2")
        assertEquals("2", item?.id)
    }

    @Test
    fun deleteById_removesItem() = runTest {
        repository.insert(
            id = "3",
            encryptedData = EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 3000L
        )
        repository.deleteById("3")
        val item = repository.getById("3")
        assertNull(item)
    }

    @Test
    fun isVaultEmpty_returnsTrueWhenEmpty() = runTest {
        assertTrue(repository.isVaultEmpty())
    }

    @Test
    fun isVaultEmpty_returnsFalseWhenHasItems() = runTest {
        repository.insert(
            id = "4",
            encryptedData = EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 4000L
        )
        assertEquals(false, repository.isVaultEmpty())
    }
}
