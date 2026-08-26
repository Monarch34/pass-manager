import Foundation
import GRDB

/// Schema and migrations for the on-device vault database.
///
/// The schema is the iOS half of `docs/IOS_PARITY.md` "Storage row design", and
/// deliberately mirrors what Room generates for Android's `VaultItemEntity` and
/// `VaultMetadataEntity` — same table names, same column names, same types, same
/// indices. Nothing here should drift from Android without a matching change on
/// that side, because both are read by humans comparing the two apps.
///
/// Written as raw SQL rather than GRDB's query builder so the DDL can be diffed
/// against Room's generated schema by eye.
public enum VaultDatabase {

    /// Migration identifiers, in order. Never rename or reorder an entry that has
    /// shipped — GRDB records the identifier, not the position.
    public enum Migration {
        public static let v1 = "v1"
    }

    public static let itemsTable = "vault_items"
    public static let metadataTable = "vault_metadata"
    /// `vault_metadata` holds exactly one row, pinned to this primary key.
    public static let metadataRowId = 1

    public static func makeMigrator() -> DatabaseMigrator {
        var migrator = DatabaseMigrator()

        migrator.registerMigration(Migration.v1) { db in
            try db.execute(sql: """
                CREATE TABLE vault_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    encrypted_data BLOB NOT NULL,
                    data_iv BLOB NOT NULL,
                    key_version INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    category TEXT NOT NULL DEFAULT 'login',
                    encrypted_title BLOB,
                    title_iv BLOB,
                    encrypted_address BLOB,
                    address_iv BLOB
                )
                """)

            // The list screen orders by updated_at and filters by category, so
            // those are the two access paths worth indexing; the composite covers
            // "one category, newest first" without a sort.
            try db.execute(sql:
                "CREATE INDEX index_vault_items_updated_at ON vault_items (updated_at)")
            try db.execute(sql:
                "CREATE INDEX index_vault_items_category ON vault_items (category)")
            try db.execute(sql:
                "CREATE INDEX index_vault_items_category_updated_at "
                + "ON vault_items (category, updated_at)")

            try db.execute(sql: """
                CREATE TABLE vault_metadata (
                    id INTEGER NOT NULL PRIMARY KEY,
                    current_key_version INTEGER NOT NULL,
                    wrapped_vault_key BLOB NOT NULL,
                    wrapper_iv BLOB NOT NULL,
                    kdf_salt BLOB NOT NULL,
                    kdf_params_json TEXT NOT NULL,
                    biometric_enabled INTEGER NOT NULL,
                    biometric_wrapped_key BLOB,
                    biometric_wrapper_iv BLOB
                )
                """)
        }

        return migrator
    }

    /// Open (creating if needed) and migrate the database at `path`.
    public static func open(path: String) throws -> DatabaseQueue {
        let queue = try DatabaseQueue(path: path)
        try makeMigrator().migrate(queue)
        return queue
    }

    /// An in-memory, already-migrated database. Used by tests, and by any preview
    /// that wants a throwaway vault.
    public static func openInMemory() throws -> DatabaseQueue {
        return try open(path: ":memory:")
    }
}
