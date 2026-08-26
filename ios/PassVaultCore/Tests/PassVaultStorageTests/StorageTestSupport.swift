import Foundation
import XCTest
import PassVaultCore
@testable import PassVaultStorage

enum StorageTestSupport {

    /// A fixed key: these tests are about storage behaviour, not key management,
    /// and a deterministic key keeps failures readable.
    static let vaultKey = Data(repeating: 0x11, count: 32)
    static let otherVaultKey = Data(repeating: 0x22, count: 32)

    static func login(
        id: String,
        title: String,
        address: String = "",
        password: String = "pw"
    ) -> ItemPayload {
        return .login(ItemPayload.Login(
            id: id,
            title: title,
            username: "user",
            address: address,
            password: password
        ))
    }

    static func note(id: String, title: String, notes: String = "") -> ItemPayload {
        return .note(ItemPayload.SecureNote(id: id, title: title, notes: notes))
    }

    static func row(
        payload: ItemPayload,
        createdAt: Int64,
        updatedAt: Int64,
        keyVersion: Int = 1
    ) throws -> VaultItemRow {
        return try ItemCrypto.makeRow(
            payload: payload,
            vaultKey: vaultKey,
            keyVersion: keyVersion,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }

    static func fileItem(
        payload: ItemPayload,
        createdAt: Int64,
        updatedAt: Int64
    ) -> PmVaultItem {
        return PmVaultItem(payload: payload, createdAt: createdAt, updatedAt: updatedAt)
    }

    /// A temporary file path that the caller is responsible for removing.
    static func temporaryDatabasePath() -> String {
        let directory = FileManager.default.temporaryDirectory
        let name = "pmvault-test-\(UUID().uuidString).sqlite"
        return directory.appendingPathComponent(name).path
    }

    static func removeDatabase(at path: String) {
        let manager = FileManager.default
        for suffix in ["", "-wal", "-shm"] {
            let candidate = path + suffix
            if manager.fileExists(atPath: candidate) {
                try? manager.removeItem(atPath: candidate)
            }
        }
    }
}
