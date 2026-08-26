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

    /**
     * The device-binding columns must arrive without disturbing a single existing byte.
     *
     * Every blob in this row is the wrapped vault key or an IV: if the migration rewrote any of
     * them the vault would stop opening, with the correct passphrase, and no way back. The row is
     * also asserted to come out as `wrap_version = 1` with a NULL `pepper_iv` — an installed
     * update must never silently bind an existing vault to the device, because that is
     * irreversible and the user has not been given the chance to make a backup yet.
     */
    @Test
    fun migrate8To9_addsDeviceBindingColumnsWithoutTouchingExistingBytes() {
        val wrappedKey = "0102030405060708090A0B0C0D0E0F10"
        val wrapperIv = "1112131415161718191A1B1C"
        val kdfSalt = "2122232425262728292A2B2C2D2E2F30"
        val biometricKey = "3132333435363738"
        val biometricIv = "393A3B3C3D3E3F40414243444546"
        val kdfParams = """{"memory":65536,"iterations":3,"parallelism":4,"hashLength":32}"""

        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                """INSERT INTO vault_metadata
                   (id, current_key_version, wrapped_vault_key, wrapper_iv, kdf_salt,
                    kdf_params_json, biometric_enabled, biometric_wrapped_key, biometric_wrapper_iv)
                   VALUES (1, 3, X'$wrappedKey', X'$wrapperIv', X'$kdfSalt',
                           '$kdfParams', 1, X'$biometricKey', X'$biometricIv')"""
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, VaultDatabase.MIGRATION_8_9)

        db.query(
            """SELECT current_key_version, hex(wrapped_vault_key), hex(wrapper_iv), hex(kdf_salt),
                      kdf_params_json, biometric_enabled, hex(biometric_wrapped_key),
                      hex(biometric_wrapper_iv), wrap_version, pepper_iv
               FROM vault_metadata WHERE id = 1"""
        ).use { c ->
            assertTrue("expected the metadata row to survive the migration", c.moveToFirst())
            assertEquals(3, c.getInt(0))
            assertEquals(wrappedKey, c.getString(1))
            assertEquals(wrapperIv, c.getString(2))
            assertEquals(kdfSalt, c.getString(3))
            assertEquals(kdfParams, c.getString(4))
            assertEquals(1, c.getInt(5))
            assertEquals(biometricKey, c.getString(6))
            assertEquals(biometricIv, c.getString(7))
            assertEquals("an existing vault must stay passphrase-only", 1, c.getInt(8))
            assertTrue("pepper_iv must start out NULL", c.isNull(9))
        }
        db.close()
    }

    /** A vault item row must be equally untouched: this migration only adds metadata columns. */
    @Test
    fun migrate8To9_leavesVaultItemsAlone() {
        val encryptedData = "AABBCCDDEEFF0011"
        val dataIv = "223344556677889900AABBCC"

        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                """INSERT INTO vault_items
                   (id, encrypted_data, data_iv, key_version, created_at, updated_at, category)
                   VALUES ('item-1', X'$encryptedData', X'$dataIv', 1, 1000, 2000, 'login')"""
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, VaultDatabase.MIGRATION_8_9)

        db.query(
            "SELECT hex(encrypted_data), hex(data_iv), created_at, updated_at, category " +
                "FROM vault_items WHERE id = 'item-1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(encryptedData, c.getString(0))
            assertEquals(dataIv, c.getString(1))
            assertEquals(1000L, c.getLong(2))
            assertEquals(2000L, c.getLong(3))
            assertEquals("login", c.getString(4))
        }
        db.close()
    }

    /**
     * The whole ladder in one go. Each step is covered on its own above, but only the full chain
     * proves an install that has been sitting on an ancient version can still reach the current
     * schema — which is the version people actually upgrade from.
     */
    @Test
    fun migrate2To9_walksTheWholeChain() {
        helper.createDatabase(dbName, 2).close()

        val db = helper.runMigrationsAndValidate(
            dbName,
            9,
            true,
            VaultDatabase.MIGRATION_2_3,
            VaultDatabase.MIGRATION_3_4,
            VaultDatabase.MIGRATION_4_5,
            VaultDatabase.MIGRATION_5_6,
            VaultDatabase.MIGRATION_6_7,
            VaultDatabase.MIGRATION_7_8,
            VaultDatabase.MIGRATION_8_9
        )

        val metadataColumns = db.query("PRAGMA table_info(vault_metadata)").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(1))
            }
        }
        assertTrue(metadataColumns.contains("wrap_version"))
        assertTrue(metadataColumns.contains("pepper_iv"))
        db.close()
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
