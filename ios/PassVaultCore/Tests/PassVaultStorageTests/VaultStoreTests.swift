import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class VaultStoreTests: XCTestCase {

    // MARK: - CRUD

    func testInsertGetRoundTrip() throws {
        let store = try VaultStore.inMemory()
        let payload = StorageTestSupport.login(id: "a", title: "GitHub", address: "https://github.com")
        try store.insert(try StorageTestSupport.row(payload: payload, createdAt: 100, updatedAt: 200))

        let row = try XCTUnwrap(try store.item(id: "a"))
        XCTAssertEqual(row.id, "a")
        XCTAssertEqual(row.category, "login")
        XCTAssertEqual(row.keyVersion, 1)
        XCTAssertEqual(row.dataIv.count, 12)

        let decrypted = try ItemCrypto.decryptPayload(row: row, vaultKey: StorageTestSupport.vaultKey)
        XCTAssertEqual(decrypted, payload)
    }

    /// Android's `VaultRepositoryImpl.insert` takes only `createdAt` and writes it
    /// into `updated_at` as well. An import MUST preserve both, so this store
    /// keeps them independent — and this test is the guard against anyone
    /// "simplifying" it back.
    func testInsertKeepsCreatedAtAndUpdatedAtIndependent() throws {
        let store = try VaultStore.inMemory()
        let row = try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "T"),
            createdAt: 1_000_000,
            updatedAt: 9_999_999
        )
        try store.insert(row)

        let stored = try XCTUnwrap(try store.item(id: "a"))
        XCTAssertEqual(stored.createdAt, 1_000_000)
        XCTAssertEqual(stored.updatedAt, 9_999_999)
        XCTAssertNotEqual(stored.createdAt, stored.updatedAt)
    }

    func testGetMissingItemReturnsNil() throws {
        let store = try VaultStore.inMemory()
        XCTAssertNil(try store.item(id: "nope"))
    }

    func testUpdateReplacesContentButNotCreatedAt() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "Old"),
            createdAt: 100,
            updatedAt: 200
        ))

        let updatedPayload = StorageTestSupport.note(id: "a", title: "New", notes: "n")
        try store.update(try StorageTestSupport.row(
            payload: updatedPayload,
            createdAt: 999_999,
            updatedAt: 300
        ))

        let stored = try XCTUnwrap(try store.item(id: "a"))
        // created_at is not in the UPDATE statement: an edit must never move an
        // item's creation date.
        XCTAssertEqual(stored.createdAt, 100)
        XCTAssertEqual(stored.updatedAt, 300)
        XCTAssertEqual(stored.category, "note")
        XCTAssertEqual(
            try ItemCrypto.decryptPayload(row: stored, vaultKey: StorageTestSupport.vaultKey),
            updatedPayload
        )
    }

    func testDeleteById() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "T"), createdAt: 1, updatedAt: 1))
        XCTAssertEqual(try store.count(), 1)
        try store.delete(id: "a")
        XCTAssertEqual(try store.count(), 0)
        XCTAssertNil(try store.item(id: "a"))
    }

    func testDeleteByIds() throws {
        let store = try VaultStore.inMemory()
        for id in ["a", "b", "c"] {
            try store.insert(try StorageTestSupport.row(
                payload: StorageTestSupport.login(id: id, title: id), createdAt: 1, updatedAt: 1))
        }
        try store.delete(ids: ["a", "c"])
        XCTAssertEqual(try store.count(), 1)
        XCTAssertNotNil(try store.item(id: "b"))
    }

    func testDeleteWithEmptyIdListIsANoOp() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "T"), createdAt: 1, updatedAt: 1))
        try store.delete(ids: [])
        XCTAssertEqual(try store.count(), 1)
    }

    func testInsertingADuplicateIdFails() throws {
        let store = try VaultStore.inMemory()
        let row = try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "T"), createdAt: 1, updatedAt: 1)
        try store.insert(row)
        XCTAssertThrowsError(try store.insert(row))
    }

    // MARK: - Header projection

    /// The security-relevant property: the list query must not read the payload
    /// column. Asserted on the SQL itself, because the cost of this regression is
    /// invisible until the vault is large.
    func testHeaderQueryDoesNotSelectThePayload() {
        XCTAssertFalse(VaultStore.headersSQL.contains("encrypted_data"))
        XCTAssertFalse(VaultStore.headersSQL.contains("data_iv"))
        XCTAssertTrue(VaultStore.headersSQL.contains("encrypted_title"))
        XCTAssertFalse(VaultStore.headersSQL.contains("SELECT *"))
    }

    func testHeadersAreOrderedNewestFirst() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "old", title: "Old"), createdAt: 1, updatedAt: 100))
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "new", title: "New"), createdAt: 1, updatedAt: 300))
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "mid", title: "Mid"), createdAt: 1, updatedAt: 200))

        let headers = try store.headers()
        XCTAssertEqual(headers.map { $0.id }, ["new", "mid", "old"])
    }

    func testHeaderCarriesCategoryAndEnvelopes() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "GitHub", address: "https://github.com"),
            createdAt: 1,
            updatedAt: 2
        ))
        let header = try XCTUnwrap(try store.headers().first)
        XCTAssertEqual(header.category, .login)
        XCTAssertEqual(header.updatedAt, 2)
        XCTAssertNotNil(header.encryptedTitle)
        XCTAssertEqual(header.titleIv?.count, 12)
        XCTAssertNotNil(header.encryptedAddress)
        XCTAssertEqual(header.addressIv?.count, 12)
    }

    /// A note has no list subtitle, so no address envelope is written at all —
    /// same as Android, which stores null rather than an envelope around "".
    func testItemWithoutASubtitleHasNoAddressEnvelope() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.note(id: "n", title: "Note", notes: ""),
            createdAt: 1,
            updatedAt: 1
        ))
        let header = try XCTUnwrap(try store.headers().first)
        XCTAssertNotNil(header.encryptedTitle)
        XCTAssertNil(header.encryptedAddress)
        XCTAssertNil(header.addressIv)
    }

    func testUpdateHeaderColumnsOnly() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "Before"), createdAt: 1, updatedAt: 2))
        let before = try XCTUnwrap(try store.item(id: "a"))

        let envelopes = try ItemCrypto.encrypt(
            payload: StorageTestSupport.login(id: "a", title: "After", address: "https://x.com"),
            vaultKey: StorageTestSupport.vaultKey
        )
        try store.updateHeaderColumns(
            id: "a",
            encryptedTitle: envelopes.title.ciphertext,
            titleIv: envelopes.title.nonce,
            encryptedAddress: envelopes.address?.ciphertext,
            addressIv: envelopes.address?.nonce
        )

        let after = try XCTUnwrap(try store.item(id: "a"))
        // Payload and timestamps untouched.
        XCTAssertEqual(after.encryptedData, before.encryptedData)
        XCTAssertEqual(after.updatedAt, 2)
        let header = try XCTUnwrap(try store.headers().first)
        let decrypted = try XCTUnwrap(
            try ItemCrypto.decryptHeader(row: header, vaultKey: StorageTestSupport.vaultKey))
        XCTAssertEqual(decrypted.title, "After")
    }

    func testUpdatedAtByIdReadsNoCiphertext() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "A"), createdAt: 1, updatedAt: 10))
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "b", title: "B"), createdAt: 1, updatedAt: 20))
        XCTAssertEqual(try store.updatedAtById(), ["a": 10, "b": 20])
    }

    // MARK: - Metadata

    func testMetadataRoundTrip() throws {
        let store = try VaultStore.inMemory()
        let core = try VaultCore.createVault(
            passphrase: "master passphrase",
            params: KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)
        )
        try store.saveMetadata(StoredVaultMetadata(core))

        let loaded = try XCTUnwrap(try store.metadata())
        XCTAssertEqual(loaded.currentKeyVersion, 1)
        XCTAssertEqual(loaded.kdfSalt, core.kdfSalt)
        XCTAssertEqual(loaded.wrapperIv, core.wrapNonce)
        XCTAssertEqual(loaded.kdfParams, core.kdfParams)
        XCTAssertFalse(loaded.biometricEnabled)
        XCTAssertNil(loaded.biometricWrappedKey)

        // And the round trip is good enough to actually unlock with.
        let vaultKey = try VaultCore.unlock(
            passphrase: "master passphrase",
            metadata: loaded.coreMetadata
        )
        XCTAssertEqual(vaultKey.count, 32)
    }

    func testSaveMetadataReplacesTheSingleRow() throws {
        let store = try VaultStore.inMemory()
        let params = KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)
        let first = try VaultCore.createVault(passphrase: "one", params: params)
        let second = try VaultCore.createVault(passphrase: "two", params: params)

        try store.saveMetadata(StoredVaultMetadata(first))
        try store.saveMetadata(StoredVaultMetadata(second))

        let loaded = try XCTUnwrap(try store.metadata())
        XCTAssertEqual(loaded.kdfSalt, second.kdfSalt)
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: "one", metadata: loaded.coreMetadata))
        XCTAssertNoThrow(try VaultCore.unlock(passphrase: "two", metadata: loaded.coreMetadata))
    }

    func testMetadataStoresBiometricColumns() throws {
        let store = try VaultStore.inMemory()
        let core = try VaultCore.createVault(
            passphrase: "p",
            params: KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)
        )
        var metadata = StoredVaultMetadata(core)
        metadata.biometricEnabled = true
        metadata.biometricWrappedKey = Data([1, 2, 3])
        metadata.biometricWrapperIv = Data(repeating: 7, count: 12)
        try store.saveMetadata(metadata)

        let loaded = try XCTUnwrap(try store.metadata())
        XCTAssertTrue(loaded.biometricEnabled)
        XCTAssertEqual(loaded.biometricWrappedKey, Data([1, 2, 3]))
        XCTAssertEqual(loaded.biometricWrapperIv, Data(repeating: 7, count: 12))
    }
}
