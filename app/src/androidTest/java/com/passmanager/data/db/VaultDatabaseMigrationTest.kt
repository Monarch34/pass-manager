package com.passmanager.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDatabaseMigrationTest {

    private val dbName = "vault-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7_succeedsAndKeepsVaultItemIndexes() {
        helper.createDatabase(dbName, 6).close()

        val db = helper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            VaultDatabase.MIGRATION_6_7
        )

        val indexNames = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'vault_items' ORDER BY name"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getString(0))
                }
            }
        }

        assertTrue(indexNames.contains("index_vault_items_category"))
        assertTrue(indexNames.contains("index_vault_items_category_updated_at"))
        assertTrue(indexNames.contains("index_vault_items_updated_at"))
        db.close()
    }
}
