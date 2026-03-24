package com.passmanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.passmanager.data.db.dao.VaultItemDao
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.db.entity.VaultItemEntity
import com.passmanager.data.db.entity.VaultMetadataEntity

// Room 2.6+ natively handles ByteArray (BLOB) and ByteArray? (nullable BLOB)
// without TypeConverters.
@Database(
    entities = [VaultItemEntity::class, VaultMetadataEntity::class],
    version = 7,
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

        /** Add category and composite indexes to vault_items for faster filtering. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_category ON vault_items (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_category_updated_at ON vault_items (category, updated_at)")
            }
        }

        /**
         * Drop desktop_wrapped_pairing_secret and desktop_pairing_iv columns.
         * SQLite < 3.35.0 (API < 34) doesn't support ALTER TABLE DROP COLUMN,
         * so we recreate the table.
         */
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
    }
}
