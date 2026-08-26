import XCTest
@testable import PassVaultCore

final class VaultCoreTests: XCTestCase {

    private let passphrase = "correct horse battery staple"
    private let wrongPassphrase = "correct horse battery stapl3"

    func testCreateProducesWellFormedMetadata() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertEqual(metadata.keyVersion, 1)
        XCTAssertEqual(metadata.kdfSalt.count, 16)
        XCTAssertEqual(metadata.wrapNonce.count, 12)
        // 32-byte vault key + 16-byte GCM tag.
        XCTAssertEqual(metadata.wrappedVaultKey.count, 48)
        XCTAssertEqual(metadata.kdfParams, TestSupport.cheapKdf)
    }

    func testCreateThenUnlock() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let vaultKey = try VaultCore.unlock(passphrase: passphrase, metadata: metadata)
        XCTAssertEqual(vaultKey.count, 32)
    }

    func testUnlockIsRepeatableAndStable() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let first = try VaultCore.unlock(passphrase: passphrase, metadata: metadata)
        let second = try VaultCore.unlock(passphrase: passphrase, metadata: metadata)
        XCTAssertEqual(first, second)
    }

    /// Two vaults made from the SAME passphrase must not share a vault key: the
    /// key is random, not derived.
    func testVaultKeysAreIndependentOfThePassphrase() throws {
        let firstMetadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let secondMetadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertNotEqual(firstMetadata.kdfSalt, secondMetadata.kdfSalt)
        let firstKey = try VaultCore.unlock(passphrase: passphrase, metadata: firstMetadata)
        let secondKey = try VaultCore.unlock(passphrase: passphrase, metadata: secondMetadata)
        XCTAssertNotEqual(firstKey, secondKey)
    }

    /// There is no stored verifier — the GCM tag failing IS the wrong-passphrase
    /// signal, exactly as on Android.
    func testWrongPassphraseThrowsTypedError() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: wrongPassphrase, metadata: metadata)) { error in
            XCTAssertEqual(error as? VaultError, VaultError.wrongPassphrase)
        }
    }

    func testTamperedWrappedKeyThrowsWrongPassphrase() throws {
        var metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        var bytes = [UInt8](metadata.wrappedVaultKey)
        bytes[0] = bytes[0] ^ 0xFF
        metadata.wrappedVaultKey = Data(bytes)
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: passphrase, metadata: metadata)) { error in
            XCTAssertEqual(error as? VaultError, VaultError.wrongPassphrase)
        }
    }

    func testShortSaltIsRejected() throws {
        var metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        metadata.kdfSalt = Data(repeating: 3, count: 8)
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: passphrase, metadata: metadata)) { error in
            XCTAssertEqual(error as? VaultError, VaultError.invalidSaltLength(8))
        }
    }

    // MARK: - Change passphrase

    func testChangePassphrasePreservesTheVaultKey() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let originalKey = try VaultCore.unlock(passphrase: passphrase, metadata: metadata)

        let updated = try VaultCore.changePassphrase(
            currentPassphrase: passphrase,
            newPassphrase: "a brand new passphrase",
            metadata: metadata,
            params: TestSupport.cheapKdf
        )

        let keyAfterChange = try VaultCore.unlock(passphrase: "a brand new passphrase", metadata: updated)
        // Items are not re-encrypted, so the vault key MUST survive unchanged.
        XCTAssertEqual(originalKey, keyAfterChange)
    }

    func testChangePassphraseInvalidatesTheOldOne() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let updated = try VaultCore.changePassphrase(
            currentPassphrase: passphrase,
            newPassphrase: "a brand new passphrase",
            metadata: metadata,
            params: TestSupport.cheapKdf
        )
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: passphrase, metadata: updated)) { error in
            XCTAssertEqual(error as? VaultError, VaultError.wrongPassphrase)
        }
    }

    func testChangePassphraseRewrapsWithFreshMaterial() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let updated = try VaultCore.changePassphrase(
            currentPassphrase: passphrase,
            newPassphrase: "a brand new passphrase",
            metadata: metadata,
            params: TestSupport.cheapKdf
        )
        XCTAssertNotEqual(updated.kdfSalt, metadata.kdfSalt)
        XCTAssertNotEqual(updated.wrapNonce, metadata.wrapNonce)
        XCTAssertNotEqual(updated.wrappedVaultKey, metadata.wrappedVaultKey)
        XCTAssertEqual(updated.keyVersion, metadata.keyVersion)
    }

    func testChangePassphraseRejectsAWrongCurrentPassphrase() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertThrowsError(
            try VaultCore.changePassphrase(
                currentPassphrase: wrongPassphrase,
                newPassphrase: "irrelevant",
                metadata: metadata,
                params: TestSupport.cheapKdf
            )
        ) { error in
            XCTAssertEqual(error as? VaultError, VaultError.wrongPassphrase)
        }
    }

    /// Android's `ChangePassphraseUseCase` uses the change as the moment to move
    /// an old vault onto the current default cost.
    func testChangePassphraseAdoptsTheSuppliedCostParameters() throws {
        let metadata = try VaultCore.createVault(
            passphrase: passphrase,
            params: KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)
        )
        let stronger = KdfParams(memory: 128, iterations: 2, parallelism: 2, hashLength: 32)
        let updated = try VaultCore.changePassphrase(
            currentPassphrase: passphrase,
            newPassphrase: "new one",
            metadata: metadata,
            params: stronger
        )
        XCTAssertEqual(updated.kdfParams, stronger)
        XCTAssertNoThrow(try VaultCore.unlock(passphrase: "new one", metadata: updated))
    }

    /// Metadata is the portable half only — it must survive a JSON round trip so
    /// the storage layer can persist it without a bespoke encoder.
    func testMetadataIsCodable() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase, params: TestSupport.cheapKdf)
        let encoded = try JSONEncoder().encode(metadata)
        let decoded = try JSONDecoder().decode(VaultMetadata.self, from: encoded)
        XCTAssertEqual(decoded, metadata)
    }

    /// One end-to-end run at the real pinned cost, so a regression in the
    /// production parameters cannot hide behind the cheap ones.
    func testCreateAndUnlockAtPinnedCost() throws {
        let metadata = try VaultCore.createVault(passphrase: passphrase)
        XCTAssertEqual(metadata.kdfParams, KdfParams.standard)
        let vaultKey = try VaultCore.unlock(passphrase: passphrase, metadata: metadata)
        XCTAssertEqual(vaultKey.count, 32)
        XCTAssertThrowsError(try VaultCore.unlock(passphrase: wrongPassphrase, metadata: metadata))
    }
}
