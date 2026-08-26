package com.passmanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.passmanager.data.db.dao.VaultItemDao
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.db.entity.VaultItemEntity
import com.passmanager.data.db.entity.VaultMetadataEntity

@Database(
    entities = [VaultItemEntity::class, VaultMetadataEntity::class],
    version = 9,
    exportSchema = true
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun vaultMetadataDao(): VaultMetadataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_updated_at ON vault_items (updated_at)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vault_metadata ADD COLUMN desktop_wrapped_pairing_secret BLOB"
                )
                db.execSQL("ALTER TABLE vault_metadata ADD COLUMN desktop_pairing_iv BLOB")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vault_items ADD COLUMN category TEXT NOT NULL DEFAULT 'login'"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_items ADD COLUMN encrypted_title BLOB")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN title_iv BLOB")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN encrypted_address BLOB")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN address_iv BLOB")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE vault_metadata_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        current_key_version INTEGER NOT NULL,
                        wrapped_vault_key BLOB NOT NULL,
                        wrapper_iv BLOB NOT NULL,
                        kdf_salt BLOB NOT NULL,
                        kdf_params_json TEXT NOT NULL,
                        biometric_enabled INTEGER NOT NULL,
                        biometric_wrapped_key BLOB,
                        biometric_wrapper_iv BLOB
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO vault_metadata_new
                        SELECT id, current_key_version, wrapped_vault_key, wrapper_iv,
                               kdf_salt, kdf_params_json, biometric_enabled,
                               biometric_wrapped_key, biometric_wrapper_iv
                        FROM vault_metadata""".trimIndent()
                )
                db.execSQL("DROP TABLE vault_metadata")
                db.execSQL("ALTER TABLE vault_metadata_new RENAME TO vault_metadata")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_category ON vault_items (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_category_updated_at ON vault_items (category, updated_at)")
            }
        }

        /**
         * Data-only migration: no schema change, so 8.json keeps 7.json's identityHash.
         *
         * Rows written before this version stored `"{}"` in kdf_params_json, because every
         * KdfParams field is defaulted and the encoder used to drop defaults. Such a row
         * carries no cost parameters, so unlocking it re-derived the key from whatever the
         * defaults were at that moment — and the very next commit lowers the iteration
         * default, which would have made those vaults underivable.
         *
         * The values below are therefore written as literals, frozen at what the defaults
         * were when these rows were created (memory=65536, iterations=10, parallelism=4,
         * hashLength=32). Never replace them with KdfParams constants: migration history
         * must stay fixed even as the defaults move on.
         */
        /**
         * Data-only migration: back-fills rows written before the KDF parameters were stored
         * explicitly. The literal below is the cost those vaults were actually created with and is
         * deliberately NOT read from [KdfParams] — that default has since been lowered, and a
         * migration must describe history, not whatever the code says today.
         *
         * The predicate keys on the absence of "iterations" rather than an exact '{}' match: any
         * row that cannot state its own iteration count would otherwise fall through to the current
         * default and derive the vault key at the wrong cost, which locks the vault out for good.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """UPDATE vault_metadata
                        SET kdf_params_json =
                            '{"memory":65536,"iterations":10,"parallelism":4,"hashLength":32}'
                        WHERE kdf_params_json IS NULL
                           OR TRIM(kdf_params_json) = ''
                           OR kdf_params_json NOT LIKE '%"iterations"%'""".trimIndent()
                )
            }
        }

        /**
         * Adds the device-binding columns and nothing else.
         *
         * Every existing row stays byte-identical: `wrap_version` defaults to 1, which is what
         * those rows already are, and `pepper_iv` stays NULL because no Keystore layer has been
         * applied to them. Upgrading a vault is a deliberate, user-confirmed act that happens
         * later through the app — never as a side effect of installing a new version, which would
         * bind a vault to the device before its owner had a backup.
         *
         * The SQL DEFAULT must be mirrored by `@ColumnInfo(defaultValue = "1")` on the entity or
         * Room's schema validation rejects the migrated table.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vault_metadata ADD COLUMN wrap_version INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL("ALTER TABLE vault_metadata ADD COLUMN pepper_iv BLOB")
            }
        }
    }
}
