import Foundation
import GRDB
import PassVaultCore

public enum VaultStoreError: Error, Equatable {
    case metadataNotFound
    case kdfParamsNotDecodable
}

/// The persistence repository — the iOS counterpart of Android's
/// `VaultRepositoryImpl` + `VaultItemDao` + `MetadataRepositoryImpl`.
///
/// Raw SQL rather than GRDB's record protocols, for the same reason the schema is
/// raw SQL: what the vault list does and does not read off disk is a security
/// property, and it should be legible in one glance rather than inferred from a
/// query builder.
public final class VaultStore {

    /// The vault list's projection. Note what is absent: `encrypted_data`.
    ///
    /// Kept as a public constant so a test can assert the payload column never
    /// creeps back in — that is the sort of regression that costs nothing at
    /// review time and everything at runtime on a large vault.
    public static let headersSQL = """
        SELECT id, encrypted_title, title_iv, encrypted_address, address_iv, category, updated_at \
        FROM vault_items ORDER BY updated_at DESC
        """

    private let dbWriter: DatabaseWriter

    public init(dbWriter: DatabaseWriter) {
        self.dbWriter = dbWriter
    }

    /// Open (creating and migrating if needed) the vault at `path`.
    public static func open(path: String) throws -> VaultStore {
        return VaultStore(dbWriter: try VaultDatabase.open(path: path))
    }

    /// A throwaway migrated vault held entirely in memory.
    public static func inMemory() throws -> VaultStore {
        return VaultStore(dbWriter: try VaultDatabase.openInMemory())
    }

    // MARK: - Items

    /// Header projection for the list screen, newest first.
    public func headers() throws -> [VaultItemHeaderRow] {
        return try dbWriter.read { db in
            let rows = try Row.fetchAll(db, sql: VaultStore.headersSQL)
            return rows.map { VaultItemHeaderRow(row: $0) }
        }
    }

    /// Full row, payload included. Only for opening one item.
    public func item(id: String) throws -> VaultItemRow? {
        return try dbWriter.read { db -> VaultItemRow? in
            let row = try Row.fetchOne(
                db,
                sql: "SELECT * FROM vault_items WHERE id = ?",
                arguments: [id]
            )
            if let row = row {
                return VaultItemRow(row: row)
            }
            return nil
        }
    }

    /// Insert a new row.
    ///
    /// `createdAt` and `updatedAt` come from the row and are stored independently.
    /// Android's `VaultRepositoryImpl.insert` takes only `createdAt` and forces
    /// `updatedAt = createdAt`, which is fine for a brand new item typed by the
    /// user but wrong for an import: `docs/FORMAT.md` requires a `.pmvault` insert
    /// to preserve BOTH timestamps from the file, and collapsing them would
    /// silently rewrite every imported item's history.
    public func insert(_ row: VaultItemRow) throws {
        try dbWriter.write { db in
            try VaultStore.insert(row, in: db)
        }
    }

    /// Update everything except `id` and `created_at`.
    ///
    /// `created_at` is deliberately not in the SET list: an edit must never move
    /// an item's creation date.
    public func update(_ row: VaultItemRow) throws {
        try dbWriter.write { db in
            try VaultStore.update(row, in: db)
        }
    }

    /// Rewrite only the two header envelopes.
    public func updateHeaderColumns(
        id: String,
        encryptedTitle: Data,
        titleIv: Data,
        encryptedAddress: Data?,
        addressIv: Data?
    ) throws {
        try dbWriter.write { db in
            try db.execute(
                sql: """
                    UPDATE vault_items \
                    SET encrypted_title = ?, title_iv = ?, encrypted_address = ?, address_iv = ? \
                    WHERE id = ?
                    """,
                arguments: [encryptedTitle, titleIv, encryptedAddress, addressIv, id]
            )
        }
    }

    public func delete(id: String) throws {
        try dbWriter.write { db in
            try db.execute(sql: "DELETE FROM vault_items WHERE id = ?", arguments: [id])
        }
    }

    public func delete(ids: [String]) throws {
        if ids.isEmpty {
            return
        }
        // One statement per id inside a single transaction, rather than building
        // an `IN (?, ?, …)` list. A batch delete is a handful of selected rows, so
        // the loop costs nothing and keeps the argument binding trivial.
        try dbWriter.write { db in
            for id in ids {
                try db.execute(sql: "DELETE FROM vault_items WHERE id = ?", arguments: [id])
            }
        }
    }

    public func count() throws -> Int {
        return try dbWriter.read { db in
            let value = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM vault_items")
            return value ?? 0
        }
    }

    public func isEmpty() throws -> Bool {
        return try count() == 0
    }

    /// `id -> updatedAt` for every stored item. This is what import merge planning
    /// needs, and it reads no ciphertext at all.
    public func updatedAtById() throws -> [String: Int64] {
        return try dbWriter.read { db in
            let rows = try Row.fetchAll(db, sql: "SELECT id, updated_at FROM vault_items")
            var result: [String: Int64] = [:]
            for row in rows {
                let id: String = row["id"]
                let updatedAt: Int64 = row["updated_at"]
                result[id] = updatedAt
            }
            return result
        }
    }

    // MARK: - Metadata

    public func metadata() throws -> StoredVaultMetadata? {
        return try dbWriter.read { db -> StoredVaultMetadata? in
            let row = try Row.fetchOne(
                db,
                sql: "SELECT * FROM vault_metadata WHERE id = ?",
                arguments: [VaultDatabase.metadataRowId]
            )
            guard let row = row else {
                return nil
            }
            let json: String = row["kdf_params_json"]
            guard let params = try? JSONDecoder().decode(KdfParams.self, from: Data(json.utf8)) else {
                throw VaultStoreError.kdfParamsNotDecodable
            }
            // Bound to explicitly typed locals first: `Row`'s subscript is
            // overloaded on optional and non-optional results, and letting it be
            // resolved through an initialiser's parameter list is needlessly
            // fragile.
            let currentKeyVersion: Int = row["current_key_version"]
            let wrappedVaultKey: Data = row["wrapped_vault_key"]
            let wrapperIv: Data = row["wrapper_iv"]
            let kdfSalt: Data = row["kdf_salt"]
            let biometricEnabled: Int = row["biometric_enabled"]
            let biometricWrappedKey: Data? = row["biometric_wrapped_key"]
            let biometricWrapperIv: Data? = row["biometric_wrapper_iv"]
            return StoredVaultMetadata(
                currentKeyVersion: currentKeyVersion,
                wrappedVaultKey: wrappedVaultKey,
                wrapperIv: wrapperIv,
                kdfSalt: kdfSalt,
                kdfParams: params,
                biometricEnabled: biometricEnabled != 0,
                biometricWrappedKey: biometricWrappedKey,
                biometricWrapperIv: biometricWrapperIv
            )
        }
    }

    /// Insert or replace the single metadata row.
    public func saveMetadata(_ metadata: StoredVaultMetadata) throws {
        let paramsData = try JSONEncoder().encode(metadata.kdfParams)
        let paramsJson = String(decoding: paramsData, as: UTF8.self)
        try dbWriter.write { db in
            try db.execute(
                sql: """
                    INSERT OR REPLACE INTO vault_metadata \
                    (id, current_key_version, wrapped_vault_key, wrapper_iv, kdf_salt, \
                    kdf_params_json, biometric_enabled, biometric_wrapped_key, biometric_wrapper_iv) \
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                arguments: [
                    VaultDatabase.metadataRowId,
                    metadata.currentKeyVersion,
                    metadata.wrappedVaultKey,
                    metadata.wrapperIv,
                    metadata.kdfSalt,
                    paramsJson,
                    metadata.biometricEnabled ? 1 : 0,
                    metadata.biometricWrappedKey,
                    metadata.biometricWrapperIv
                ]
            )
        }
    }

    // MARK: - Import

    /// Apply a merge plan in ONE transaction.
    ///
    /// Either the whole import lands or none of it does — a half-applied import
    /// would leave the user unable to tell what actually happened, which is the
    /// worst possible outcome for a password vault.
    ///
    /// Never deletes anything, per `docs/FORMAT.md`.
    @discardableResult
    public func applyImport(_ plan: ImportPlan, vaultKey: Data, keyVersion: Int) throws -> ImportOutcome {
        return try dbWriter.write { db in
            for insert in plan.inserts {
                let row = try ItemCrypto.makeRow(
                    payload: insert.item.payload,
                    vaultKey: vaultKey,
                    keyVersion: keyVersion,
                    createdAt: insert.item.createdAt,
                    updatedAt: insert.effectiveUpdatedAt
                )
                try VaultStore.insert(row, in: db)
            }
            for overwrite in plan.overwrites {
                let row = try ItemCrypto.makeRow(
                    payload: overwrite.item.payload,
                    vaultKey: vaultKey,
                    keyVersion: keyVersion,
                    // created_at is not written by an update, so this value is
                    // only a placeholder to satisfy the row type.
                    createdAt: overwrite.item.createdAt,
                    updatedAt: overwrite.effectiveUpdatedAt
                )
                try VaultStore.update(row, in: db)
            }
            return ImportOutcome(
                inserted: plan.inserts.count,
                overwritten: plan.overwrites.count,
                skipped: plan.skipped.count
            )
        }
    }

    // MARK: - Schema introspection

    /// Present so a test can assert the migration actually produced the schema
    /// `docs/IOS_PARITY.md` describes, rather than merely running without error.
    public func tableNames() throws -> [String] {
        return try dbWriter.read { db in
            return try String.fetchAll(
                db,
                sql: "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
            )
        }
    }

    public func indexNames() throws -> [String] {
        return try dbWriter.read { db in
            return try String.fetchAll(
                db,
                sql: "SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%' "
                    + "ORDER BY name"
            )
        }
    }

    /// Column names of one of this schema's tables, in declaration order.
    ///
    /// PRAGMA cannot take a bound parameter, so the table name is interpolated —
    /// which is why it is restricted to the two names this schema defines instead
    /// of accepting arbitrary text.
    public func columnNames(ofTable table: String) throws -> [String] {
        guard table == VaultDatabase.itemsTable || table == VaultDatabase.metadataTable else {
            return []
        }
        return try dbWriter.read { db in
            let rows = try Row.fetchAll(db, sql: "PRAGMA table_info(\(table))")
            return rows.map { row in
                let name: String = row["name"]
                return name
            }
        }
    }

    // MARK: - Shared statements

    private static func insert(_ row: VaultItemRow, in db: Database) throws {
        try db.execute(
            sql: """
                INSERT INTO vault_items \
                (id, encrypted_data, data_iv, key_version, created_at, updated_at, category, \
                encrypted_title, title_iv, encrypted_address, address_iv) \
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            arguments: [
                row.id,
                row.encryptedData,
                row.dataIv,
                row.keyVersion,
                row.createdAt,
                row.updatedAt,
                row.category,
                row.encryptedTitle,
                row.titleIv,
                row.encryptedAddress,
                row.addressIv
            ]
        )
    }

    private static func update(_ row: VaultItemRow, in db: Database) throws {
        try db.execute(
            sql: """
                UPDATE vault_items SET \
                encrypted_data = ?, data_iv = ?, key_version = ?, updated_at = ?, category = ?, \
                encrypted_title = ?, title_iv = ?, encrypted_address = ?, address_iv = ? \
                WHERE id = ?
                """,
            arguments: [
                row.encryptedData,
                row.dataIv,
                row.keyVersion,
                row.updatedAt,
                row.category,
                row.encryptedTitle,
                row.titleIv,
                row.encryptedAddress,
                row.addressIv,
                row.id
            ]
        )
    }
}
