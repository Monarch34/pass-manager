package com.passmanager.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    /**
     * The single highest-consequence migration in the app: a vault whose row is not back-filled is
     * unlocked with today's cheaper defaults instead of the cost it was created with, which fails
     * the AEAD tag and locks the user out permanently with no recovery path. The correct passphrase
     * would simply stop working, so this is verified rather than assumed.
     */
    @Test
    fun migrate7To8_backfillsKdfParamsThatWereWrittenAsEmptyJson() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                """INSERT INTO vault_metadata
                   (id, current_key_version, wrapped_vault_key, wrapper_iv, kdf_salt,
                    kdf_params_json, biometric_enabled, biometric_wrapped_key, biometric_wrapper_iv)
                   VALUES (1, 1, X'00', X'00', X'00', '{}', 0, NULL, NULL)"""
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, VaultDatabase.MIGRATION_7_8)

        val stored = db.query("SELECT kdf_params_json FROM vault_metadata WHERE id = 1").use { c ->
            assertTrue("expected the metadata row to survive the migration", c.moveToFirst())
            c.getString(0)
        }
        assertEquals(
            """{"memory":65536,"iterations":10,"parallelism":4,"hashLength":32}""",
            stored
        )
    }

    /** A row that already states its cost must be left exactly as it is. */
    @Test
    fun migrate7To8_leavesRowsThatAlreadyCarryTheirParameters() {
        val explicit = """{"memory":19456,"iterations":2,"parallelism":1,"hashLength":32}"""
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                """INSERT INTO vault_metadata
                   (id, current_key_version, wrapped_vault_key, wrapper_iv, kdf_salt,
                    kdf_params_json, biometric_enabled, biometric_wrapped_key, biometric_wrapper_iv)
                   VALUES (1, 1, X'00', X'00', X'00', '$explicit', 0, NULL, NULL)"""
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, VaultDatabase.MIGRATION_7_8)

        val stored = db.query("SELECT kdf_params_json FROM vault_metadata WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            c.getString(0)
        }
        assertEquals(explicit, stored)
    }
}
