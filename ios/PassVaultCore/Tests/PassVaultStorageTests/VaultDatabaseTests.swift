import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class VaultDatabaseTests: XCTestCase {

    func testMigrationCreatesBothTables() throws {
        let store = try VaultStore.inMemory()
        let tables = try store.tableNames()
        XCTAssertTrue(tables.contains("vault_items"), "got \(tables)")
        XCTAssertTrue(tables.contains("vault_metadata"), "got \(tables)")
    }

    /// The column set is the interop contract with Android — same names, same
    /// order as Room generates from `VaultItemEntity`.
    func testItemsTableColumnsMatchAndroid() throws {
        let store = try VaultStore.inMemory()
        let columns = try store.columnNames(ofTable: "vault_items")
        XCTAssertEqual(columns, [
            "id",
            "encrypted_data",
            "data_iv",
            "key_version",
            "created_at",
            "updated_at",
            "category",
            "encrypted_title",
            "title_iv",
            "encrypted_address",
            "address_iv"
        ])
    }

    func testMetadataTableColumnsMatchAndroid() throws {
        let store = try VaultStore.inMemory()
        let columns = try store.columnNames(ofTable: "vault_metadata")
        XCTAssertEqual(columns, [
            "id",
            "current_key_version",
            "wrapped_vault_key",
            "wrapper_iv",
            "kdf_salt",
            "kdf_params_json",
            "biometric_enabled",
            "biometric_wrapped_key",
            "biometric_wrapper_iv"
        ])
    }

    func testIndicesExist() throws {
        let store = try VaultStore.inMemory()
        let indices = try store.indexNames()
        XCTAssertTrue(indices.contains("index_vault_items_updated_at"), "got \(indices)")
        XCTAssertTrue(indices.contains("index_vault_items_category"), "got \(indices)")
        XCTAssertTrue(indices.contains("index_vault_items_category_updated_at"), "got \(indices)")
    }

    /// Reopening an existing database must run no migration a second time and
    /// must not lose data. This is the failure mode that only shows up on the
    /// user's second launch, so it is worth a real file rather than `:memory:`.
    func testReopeningAnExistingDatabaseIsIdempotent() throws {
        let path = StorageTestSupport.temporaryDatabasePath()
        defer { StorageTestSupport.removeDatabase(at: path) }

        let first = try VaultStore.open(path: path)
        let row = try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "GitHub"),
            createdAt: 1000,
            updatedAt: 2000
        )
        try first.insert(row)
        XCTAssertEqual(try first.count(), 1)

        let second = try VaultStore.open(path: path)
        XCTAssertEqual(try second.count(), 1)
        XCTAssertEqual(try second.tableNames().filter { $0.hasPrefix("vault_") }.sorted(),
                       ["vault_items", "vault_metadata"])

        let reread = try second.item(id: "a")
        XCTAssertNotNil(reread)
        XCTAssertEqual(reread?.createdAt, 1000)
        XCTAssertEqual(reread?.updatedAt, 2000)
    }

    func testFreshDatabaseIsEmpty() throws {
        let store = try VaultStore.inMemory()
        XCTAssertEqual(try store.count(), 0)
        XCTAssertTrue(try store.isEmpty())
        XCTAssertNil(try store.metadata())
        XCTAssertEqual(try store.headers().count, 0)
    }
}
