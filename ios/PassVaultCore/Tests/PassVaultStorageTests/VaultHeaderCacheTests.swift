import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class VaultHeaderCacheTests: XCTestCase {

    private let key = StorageTestSupport.vaultKey

    func testFirstPassDecryptsEverything() throws {
        let store = try makeStore([
            ("a", "GitHub", "https://github.com"),
            ("b", "Gmail", "https://mail.google.com")
        ])
        let cache = VaultHeaderCache()
        let result = cache.refresh(headers: try store.headers(), vaultKey: key)

        XCTAssertEqual(result.decrypted, 2)
        XCTAssertFalse(result.hadFailure)
        XCTAssertEqual(cache.title(for: "a"), "GitHub")
        XCTAssertEqual(cache.address(for: "a"), "https://github.com")
        XCTAssertEqual(cache.title(for: "b"), "Gmail")
    }

    /// The reason the cache exists: a second pass over unchanged rows must do no
    /// crypto at all.
    func testSecondPassDecryptsNothingWhenNothingChanged() throws {
        let store = try makeStore([("a", "GitHub", "")])
        let cache = VaultHeaderCache()
        _ = cache.refresh(headers: try store.headers(), vaultKey: key)

        let second = cache.refresh(headers: try store.headers(), vaultKey: key)
        XCTAssertEqual(second.decrypted, 0)
        XCTAssertEqual(cache.title(for: "a"), "GitHub")
    }

    func testOnlyTheChangedRowIsDecryptedAgain() throws {
        let store = try makeStore([("a", "A title", ""), ("b", "B title", "")])
        let cache = VaultHeaderCache()
        _ = cache.refresh(headers: try store.headers(), vaultKey: key)

        // Edit "a" only — new content and a new updatedAt.
        try store.update(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "A edited"),
            createdAt: 1,
            updatedAt: 999
        ))

        let result = cache.refresh(headers: try store.headers(), vaultKey: key)
        XCTAssertEqual(result.decrypted, 1)
        XCTAssertEqual(cache.title(for: "a"), "A edited")
        XCTAssertEqual(cache.title(for: "b"), "B title")
    }

    func testStaleDetectionUsesUpdatedAt() throws {
        let store = try makeStore([("a", "Title", "")])
        let cache = VaultHeaderCache()
        let headers = try store.headers()

        XCTAssertEqual(cache.staleHeaders(in: headers).count, 1)
        _ = cache.refresh(headers: headers, vaultKey: key)
        XCTAssertEqual(cache.staleHeaders(in: headers).count, 0)

        var bumped = headers
        bumped[0].updatedAt += 1
        XCTAssertEqual(cache.staleHeaders(in: bumped).count, 1)
    }

    func testDeletedRowsArePruned() throws {
        let store = try makeStore([("a", "A", ""), ("b", "B", "")])
        let cache = VaultHeaderCache()
        _ = cache.refresh(headers: try store.headers(), vaultKey: key)
        XCTAssertEqual(cache.count, 2)

        try store.delete(id: "b")
        _ = cache.refresh(headers: try store.headers(), vaultKey: key)

        XCTAssertEqual(cache.count, 1)
        XCTAssertEqual(cache.title(for: "a"), "A")
        XCTAssertEqual(cache.title(for: "b"), "", "a deleted item must leave no plaintext behind")
        XCTAssertNil(cache.titles["b"])
        XCTAssertNil(cache.addresses["b"])
    }

    /// Locking zeroes the vault key; it must also drop every string derived from
    /// it, or the plaintext outlives the lock.
    func testClearRemovesEveryDecryptedString() throws {
        let store = try makeStore([("a", "Secret title", "https://secret.example")])
        let cache = VaultHeaderCache()
        _ = cache.refresh(headers: try store.headers(), vaultKey: key)
        XCTAssertEqual(cache.count, 1)

        cache.clear()

        XCTAssertEqual(cache.count, 0)
        XCTAssertTrue(cache.titles.isEmpty)
        XCTAssertTrue(cache.addresses.isEmpty)
        XCTAssertEqual(cache.title(for: "a"), "")
        // And everything looks stale again, so the next unlock re-decrypts.
        XCTAssertEqual(cache.staleHeaders(in: try store.headers()).count, 1)
    }

    /// One damaged row must not blank the whole list.
    func testARowThatFailsToDecryptIsReportedNotFatal() throws {
        let store = try makeStore([("good", "Good", ""), ("bad", "Bad", "")])
        var headers = try store.headers()
        // Corrupt the "bad" row's title envelope.
        for index in headers.indices where headers[index].id == "bad" {
            var bytes = [UInt8](headers[index].encryptedTitle ?? Data())
            bytes[0] = bytes[0] ^ 0xFF
            headers[index].encryptedTitle = Data(bytes)
        }

        let cache = VaultHeaderCache()
        let result = cache.refresh(headers: headers, vaultKey: key)

        XCTAssertEqual(result.decrypted, 1)
        XCTAssertTrue(result.hadFailure)
        XCTAssertEqual(result.failedIDs, ["bad"])
        XCTAssertEqual(cache.title(for: "good"), "Good")
        XCTAssertEqual(cache.title(for: "bad"), "")
    }

    func testWrongKeyFailsEveryRowWithoutCrashing() throws {
        let store = try makeStore([("a", "A", ""), ("b", "B", "")])
        let cache = VaultHeaderCache()
        let result = cache.refresh(
            headers: try store.headers(),
            vaultKey: StorageTestSupport.otherVaultKey
        )
        XCTAssertEqual(result.decrypted, 0)
        XCTAssertEqual(result.failedIDs.sorted(), ["a", "b"])
        XCTAssertEqual(cache.count, 0)
    }

    // MARK: - Helpers

    private func makeStore(_ specs: [(String, String, String)]) throws -> VaultStore {
        let store = try VaultStore.inMemory()
        var updatedAt: Int64 = 0
        for spec in specs {
            updatedAt += 1
            try store.insert(try StorageTestSupport.row(
                payload: StorageTestSupport.login(id: spec.0, title: spec.1, address: spec.2),
                createdAt: 1,
                updatedAt: updatedAt
            ))
        }
        return store
    }
}
