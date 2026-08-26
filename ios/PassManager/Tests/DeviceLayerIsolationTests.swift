import XCTest
import Foundation
import PassVaultCore
@testable import PassManager

/// Nothing from the device layer may ever reach a `.pmvault` file.
///
/// This is not housekeeping. The export format is the migration path between
/// devices and platforms, and it only works because it is derived from the raw
/// items and nothing else. The moment a Keychain blob, a device-bound wrapped
/// key or a master-passphrase salt leaks into an export, the file stops being
/// portable and starts carrying material that was deliberately pinned to one
/// device.
final class DeviceLayerIsolationTests: XCTestCase {

    private let masterPassphrase = "master passphrase for the device layer"
    private let exportPassphrase = "a different export passphrase"
    private let cheapKdf = KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)

    /// Builds a real vault, exports its items, and then hunts through the export
    /// for every piece of device-layer material.
    func testNoDeviceMaterialAppearsInAnExport() throws {
        // The device layer: a vault, its wrapped key, its salt, and the raw key
        // that biometric unlock would store in the Keychain.
        let metadata = try VaultCore.createVault(passphrase: masterPassphrase, params: cheapKdf)
        let vaultKey = try VaultCore.unlock(passphrase: masterPassphrase, metadata: metadata)
        XCTAssertEqual(vaultKey.count, 32)

        // The export: built from payloads only.
        let body = PmVaultBody(version: 1, exportedAt: 1_787_000_000_000, items: [
            PmVaultItem(
                payload: .login(ItemPayload.Login(
                    id: "1", title: "GitHub", username: "octocat",
                    address: "https://github.com", password: "hunter2"
                )),
                createdAt: 100,
                updatedAt: 200
            ),
            PmVaultItem(
                payload: .note(ItemPayload.SecureNote(
                    id: "2", title: "Kurtarma", notes: "gizli"
                )),
                createdAt: 300,
                updatedAt: 400
            )
        ])
        let file = try PmVaultFile.write(
            body: body,
            passphrase: exportPassphrase,
            params: cheapKdf
        )

        // Search BOTH the ciphertext and the decrypted plaintext. The ciphertext
        // check catches material smuggled into the header; the plaintext check
        // catches it inside the body.
        let plaintext = try encodedBody(of: file)

        let forbidden: [(String, Data)] = [
            ("raw vault key", vaultKey),
            ("wrapped vault key", metadata.wrappedVaultKey),
            ("wrap nonce", metadata.wrapNonce),
            ("master KDF salt", metadata.kdfSalt)
        ]
        for (label, needle) in forbidden {
            XCTAssertFalse(contains(file, needle), "\(label) leaked into the container")
            XCTAssertFalse(contains(plaintext, needle), "\(label) leaked into the body")
        }
    }

    /// The Keychain's own identifiers must not appear either — they would say
    /// which device produced the file.
    func testNoKeychainIdentifiersAppearInAnExport() throws {
        let body = PmVaultBody(version: 1, exportedAt: 1, items: [])
        let file = try PmVaultFile.write(
            body: body,
            passphrase: exportPassphrase,
            params: cheapKdf
        )
        let plaintext = try encodedBody(of: file)

        let identifiers = [
            KeychainVaultStore.service,
            KeychainVaultStore.Account.wrappedVaultKey.rawValue,
            KeychainVaultStore.Account.biometricVaultKey.rawValue
        ]
        for identifier in identifiers {
            XCTAssertFalse(contains(file, Data(identifier.utf8)), identifier)
            XCTAssertFalse(contains(plaintext, Data(identifier.utf8)), identifier)
        }
    }

    /// Structural rather than by search: an exported item has exactly five keys,
    /// so there is nowhere for a device field to hide even if someone adds one to
    /// the storage row.
    func testExportedItemsCarryOnlyTheContractedKeys() throws {
        let body = PmVaultBody(version: 1, exportedAt: 1, items: [
            PmVaultItem(
                payload: .login(ItemPayload.Login(id: "1", title: "T", password: "p")),
                createdAt: 1,
                updatedAt: 2
            )
        ])
        let file = try PmVaultFile.write(body: body, passphrase: exportPassphrase, params: cheapKdf)
        let plaintext = try encodedBody(of: file)

        guard
            let root = try JSONSerialization.jsonObject(with: plaintext) as? [String: Any],
            let items = root["items"] as? [[String: Any]],
            let first = items.first
        else {
            XCTFail("unexpected body shape")
            return
        }

        XCTAssertEqual(Set(root.keys), Set(["version", "exportedAt", "items"]))
        XCTAssertEqual(Set(first.keys), Set(["id", "category", "createdAt", "updatedAt", "payload"]))

        guard let payload = first["payload"] as? [String: Any] else {
            XCTFail("missing payload")
            return
        }
        // No key_version, no wrapped key, no IV, no biometric anything.
        for forbidden in ["keyVersion", "key_version", "wrappedVaultKey", "dataIv",
                          "biometricWrappedKey", "kdfSalt", "wrapperIv"] {
            XCTAssertNil(first[forbidden], forbidden)
            XCTAssertNil(payload[forbidden], forbidden)
        }
    }

    /// A `.pmvault` derives its key from the EXPORT passphrase alone. If the
    /// master passphrase could open it, the export would be tied to the device's
    /// own key material rather than standing on its own.
    func testExportIsIndependentOfTheMasterPassphrase() throws {
        let body = PmVaultBody(version: 1, exportedAt: 1, items: [])
        let file = try PmVaultFile.write(body: body, passphrase: exportPassphrase, params: cheapKdf)

        XCTAssertNoThrow(try PmVaultFile.read(file, passphrase: exportPassphrase))
        XCTAssertThrowsError(try PmVaultFile.read(file, passphrase: masterPassphrase)) { error in
            guard let pmError = error as? PmVaultError else {
                XCTFail("expected PmVaultError, got \(error)")
                return
            }
            XCTAssertEqual(pmError.kind, .wrongPassphraseOrCorrupt)
        }
    }

    /// The same guarantee, now through the shape the export UI actually builds:
    /// the document handed to `.fileExporter`. If the device layer could reach a
    /// `.pmvault`, this is the path it would take.
    func testTheExportDocumentCarriesNoDeviceMaterial() throws {
        let metadata = try VaultCore.createVault(passphrase: masterPassphrase, params: cheapKdf)
        let vaultKey = try VaultCore.unlock(passphrase: masterPassphrase, metadata: metadata)

        let body = PmVaultBody(version: 1, exportedAt: 1_787_000_000_000, items: [
            PmVaultItem(
                payload: .bank(ItemPayload.Bank(
                    id: "b", title: "Kadıköy Bankası", bankName: "Kadıköy",
                    password: "Bank-2026!x", previousPasswords: ["Eski-2025!a"]
                )),
                createdAt: 10,
                updatedAt: 20
            )
        ])
        let document = PmVaultDocument(
            data: try PmVaultFile.write(body: body, passphrase: exportPassphrase, params: cheapKdf)
        )

        let forbidden: [(String, Data)] = [
            ("raw vault key", vaultKey),
            ("wrapped vault key", metadata.wrappedVaultKey),
            ("wrap nonce", metadata.wrapNonce),
            ("master KDF salt", metadata.kdfSalt)
        ]
        for (label, needle) in forbidden {
            XCTAssertFalse(
                contains(document.data, needle),
                "\(label) leaked into the export document"
            )
        }

        // And the document still is what it claims to be, timestamps intact.
        let readBack = try PmVaultFile.read(document.data, passphrase: exportPassphrase)
        XCTAssertEqual(readBack.items.count, 1)
        XCTAssertEqual(readBack.items[0].createdAt, 10)
        XCTAssertEqual(readBack.items[0].updatedAt, 20)
    }

    /// Two exports of the same items under the same passphrase must differ:
    /// fresh salt and IV every time, never reused.
    func testEachExportDrawsFreshSaltAndIv() throws {
        let body = PmVaultBody(version: 1, exportedAt: 1, items: [])
        let first = try PmVaultFile.write(body: body, passphrase: exportPassphrase, params: cheapKdf)
        let second = try PmVaultFile.write(body: body, passphrase: exportPassphrase, params: cheapKdf)
        XCTAssertNotEqual(first, second)
    }

    // MARK: - Helpers

    /// Decrypts a container back to its raw body bytes.
    private func encodedBody(of file: Data) throws -> Data {
        let body = try PmVaultFile.read(file, passphrase: exportPassphrase)
        return try PmVaultFile.makeEncoder().encode(body)
    }

    /// Naive byte-substring search — the point is to be obviously correct rather
    /// than fast, over inputs of a few kilobytes.
    private func contains(_ haystack: Data, _ needle: Data) -> Bool {
        if needle.isEmpty || needle.count > haystack.count {
            return false
        }
        let hay = [UInt8](haystack)
        let pin = [UInt8](needle)
        let last = hay.count - pin.count
        var start = 0
        while start <= last {
            var offset = 0
            while offset < pin.count && hay[start + offset] == pin[offset] {
                offset += 1
            }
            if offset == pin.count {
                return true
            }
            start += 1
        }
        return false
    }
}
