import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class ItemCryptoTests: XCTestCase {

    private let key = StorageTestSupport.vaultKey

    func testThreeEnvelopesUseDistinctIvs() throws {
        let payload = StorageTestSupport.login(id: "a", title: "GitHub", address: "https://github.com")
        let envelopes = try ItemCrypto.encrypt(payload: payload, vaultKey: key)

        let address = try XCTUnwrap(envelopes.address)
        XCTAssertEqual(envelopes.data.nonce.count, 12)
        XCTAssertEqual(envelopes.title.nonce.count, 12)
        XCTAssertEqual(address.nonce.count, 12)
        XCTAssertNotEqual(envelopes.data.nonce, envelopes.title.nonce)
        XCTAssertNotEqual(envelopes.data.nonce, address.nonce)
        XCTAssertNotEqual(envelopes.title.nonce, address.nonce)
    }

    /// The whole point of three envelopes: the list can open the title without
    /// ever touching the payload.
    func testTitleDecryptsWithoutThePayload() throws {
        let payload = StorageTestSupport.login(id: "a", title: "GitHub", address: "https://github.com")
        let row = try StorageTestSupport.row(payload: payload, createdAt: 1, updatedAt: 2)

        let headerOnly = VaultItemHeaderRow(
            id: row.id,
            encryptedTitle: row.encryptedTitle,
            titleIv: row.titleIv,
            encryptedAddress: row.encryptedAddress,
            addressIv: row.addressIv,
            category: .login,
            updatedAt: row.updatedAt
        )
        let decrypted = try XCTUnwrap(try ItemCrypto.decryptHeader(row: headerOnly, vaultKey: key))
        XCTAssertEqual(decrypted.title, "GitHub")
        XCTAssertEqual(decrypted.address, "https://github.com")
    }

    func testAddressEnvelopeIsAbsentForAnEmptySubtitle() throws {
        let payload = StorageTestSupport.note(id: "n", title: "Note", notes: "")
        let envelopes = try ItemCrypto.encrypt(payload: payload, vaultKey: key)
        XCTAssertNil(envelopes.address)
    }

    func testHeaderWithoutAddressDecryptsToEmptyString() throws {
        let row = try StorageTestSupport.row(
            payload: StorageTestSupport.note(id: "n", title: "Note"),
            createdAt: 1,
            updatedAt: 1
        )
        let headerOnly = VaultItemHeaderRow(
            id: row.id,
            encryptedTitle: row.encryptedTitle,
            titleIv: row.titleIv,
            encryptedAddress: nil,
            addressIv: nil,
            category: .note,
            updatedAt: 1
        )
        let decrypted = try XCTUnwrap(try ItemCrypto.decryptHeader(row: headerOnly, vaultKey: key))
        XCTAssertEqual(decrypted.title, "Note")
        XCTAssertEqual(decrypted.address, "")
    }

    func testMissingHeaderColumnsDecodeToNilRatherThanThrowing() throws {
        let headerOnly = VaultItemHeaderRow(
            id: "x",
            encryptedTitle: nil,
            titleIv: nil,
            encryptedAddress: nil,
            addressIv: nil,
            category: .login,
            updatedAt: 1
        )
        XCTAssertNil(try ItemCrypto.decryptHeader(row: headerOnly, vaultKey: key))
    }

    func testWrongKeyFailsEveryEnvelope() throws {
        let row = try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "T", address: "https://x.com"),
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertThrowsError(
            try ItemCrypto.decryptPayload(row: row, vaultKey: StorageTestSupport.otherVaultKey))

        let headerOnly = VaultItemHeaderRow(
            id: row.id,
            encryptedTitle: row.encryptedTitle,
            titleIv: row.titleIv,
            encryptedAddress: row.encryptedAddress,
            addressIv: row.addressIv,
            category: .login,
            updatedAt: 1
        )
        XCTAssertThrowsError(
            try ItemCrypto.decryptHeader(row: headerOnly, vaultKey: StorageTestSupport.otherVaultKey))
    }

    func testCategoryColumnMatchesThePayloadType() throws {
        let cases: [(ItemPayload, String)] = [
            (StorageTestSupport.login(id: "1", title: "T"), "login"),
            (.card(ItemPayload.Card(id: "2", title: "T")), "card"),
            (.bank(ItemPayload.Bank(id: "3", title: "T")), "bank"),
            (StorageTestSupport.note(id: "4", title: "T"), "note"),
            (.identity(ItemPayload.Identity(id: "5", title: "T")), "identity")
        ]
        for (payload, expected) in cases {
            let row = try StorageTestSupport.row(payload: payload, createdAt: 1, updatedAt: 1)
            XCTAssertEqual(row.category, expected)
        }
    }

    func testRoundTripThroughTheStoreForEveryCategory() throws {
        let store = try VaultStore.inMemory()
        let payloads: [ItemPayload] = [
            StorageTestSupport.login(id: "1", title: "Login", address: "https://x.com"),
            .card(ItemPayload.Card(id: "2", title: "Card", cardholderName: "Ada")),
            .bank(ItemPayload.Bank(id: "3", title: "Bank", bankName: "Ziraat",
                                   previousPasswords: ["a", "b"])),
            StorageTestSupport.note(id: "4", title: "Note", notes: "secret"),
            .identity(ItemPayload.Identity(id: "5", title: "Identity", email: "ada@example.com"))
        ]
        for payload in payloads {
            try store.insert(try StorageTestSupport.row(payload: payload, createdAt: 1, updatedAt: 1))
        }

        for payload in payloads {
            let row = try XCTUnwrap(try store.item(id: payload.id))
            XCTAssertEqual(try ItemCrypto.decryptPayload(row: row, vaultKey: key), payload)
        }
    }
}
