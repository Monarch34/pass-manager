import XCTest
@testable import PassVaultCore

/// Known-answer tests for the vendored phc-winner-argon2.
///
/// Every expected value below was produced by compiling the exact vendored
/// sources in `Sources/CArgon2` and running `argon2id_hash_raw` against them.
/// The first one is additionally the widely published Argon2id reference vector
/// for v=19 / m=65536 / t=2 / p=1 / "password" / "somesalt", which is what ties
/// this vendored copy to the upstream library rather than merely to itself.
final class Argon2idTests: XCTestCase {

    func testPublishedReferenceVector() throws {
        let derived = try Argon2id.deriveKey(
            passphrase: Array("password".utf8),
            salt: Array("somesalt".utf8),
            memoryKiB: 65536,
            iterations: 2,
            parallelism: 1,
            hashLength: 32
        )
        XCTAssertEqual(
            TestSupport.hexString(derived),
            "09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7"
        )
    }

    /// The parameters `docs/FORMAT.md` pins for real vaults and real exports.
    func testPinnedProductionParameters() throws {
        var salt: [UInt8] = []
        for index in 1...16 {
            salt.append(UInt8(index))
        }
        let derived = try Argon2id.deriveKey(
            passphrase: Array("correct horse battery staple".utf8),
            salt: salt,
            params: KdfParams.standard
        )
        XCTAssertEqual(KdfParams.standard.memory, 65536)
        XCTAssertEqual(KdfParams.standard.iterations, 3)
        XCTAssertEqual(KdfParams.standard.parallelism, 4)
        XCTAssertEqual(KdfParams.standard.hashLength, 32)
        XCTAssertEqual(
            TestSupport.hexString(derived),
            "6ec690471257037ee9c75b275e6161c1c2f4335ab541400534dba6769a444397"
        )
    }

    func testMinimalCostVector() throws {
        let derived = try Argon2id.deriveKey(
            passphrase: Array("password".utf8),
            salt: Array("somesalt".utf8),
            memoryKiB: 8,
            iterations: 1,
            parallelism: 1,
            hashLength: 32
        )
        XCTAssertEqual(
            TestSupport.hexString(derived),
            "f137f8e186a403a679ccd0606e5ab5dcdafe43c1640855ac8c6e33e9bd63eeb3"
        )
    }

    /// p=4 takes the multi-lane path, which is the whole reason this package
    /// vendors phc-winner-argon2 instead of using libsodium's p=1-only wrapper.
    func testMultiLaneVector() throws {
        var salt: [UInt8] = []
        for index in 1...16 {
            salt.append(UInt8(index))
        }
        let derived = try Argon2id.deriveKey(
            passphrase: Array("password".utf8),
            salt: salt,
            memoryKiB: 64,
            iterations: 1,
            parallelism: 4,
            hashLength: 32
        )
        XCTAssertEqual(
            TestSupport.hexString(derived),
            "54ed02904fd3875da9713e93c245c3517220cabf883106a71c0ee3b5efe9e9fd"
        )
    }

    /// An empty Swift array has a nil `baseAddress`; the C library documents NULL
    /// with length 0 as valid, and produces the same tag as a pointer to "".
    func testEmptyPassphraseIsAcceptedAndMatchesTheCReference() throws {
        var salt: [UInt8] = []
        for index in 1...16 {
            salt.append(UInt8(index))
        }
        let derived = try Argon2id.deriveKey(
            passphrase: [],
            salt: salt,
            memoryKiB: 64,
            iterations: 1,
            parallelism: 1,
            hashLength: 32
        )
        XCTAssertEqual(
            TestSupport.hexString(derived),
            "fa6ef1eb339bf8513852ba52751317b2bc5747e9e871b6574325feb2c76cc815"
        )
    }

    func testDerivationIsDeterministic() throws {
        let first = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 9, count: 16),
            params: TestSupport.cheapKdf
        )
        let second = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 9, count: 16),
            params: TestSupport.cheapKdf
        )
        XCTAssertEqual(first, second)
        XCTAssertEqual(first.count, 32)
    }

    func testDifferentSaltProducesDifferentKey() throws {
        let first = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 1, count: 16),
            params: TestSupport.cheapKdf
        )
        let second = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 2, count: 16),
            params: TestSupport.cheapKdf
        )
        XCTAssertNotEqual(first, second)
    }

    func testDifferentPassphraseProducesDifferentKey() throws {
        let first = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 1, count: 16),
            params: TestSupport.cheapKdf
        )
        let second = try Argon2id.deriveKey(
            passphrase: Array("passphrasf".utf8),
            salt: Array(repeating: 1, count: 16),
            params: TestSupport.cheapKdf
        )
        XCTAssertNotEqual(first, second)
    }

    func testHashLengthIsHonoured() throws {
        let derived = try Argon2id.deriveKey(
            passphrase: Array("passphrase".utf8),
            salt: Array(repeating: 1, count: 16),
            memoryKiB: 64,
            iterations: 1,
            parallelism: 1,
            hashLength: 64
        )
        XCTAssertEqual(derived.count, 64)
    }

    // MARK: - Parameters the library must reject rather than crash on

    func testNegativeMemoryThrowsInsteadOfTrapping() {
        XCTAssertThrowsError(
            try Argon2id.deriveKey(
                passphrase: Array("x".utf8),
                salt: Array(repeating: 1, count: 16),
                memoryKiB: -1,
                iterations: 1,
                parallelism: 1,
                hashLength: 32
            )
        ) { error in
            XCTAssertTrue(error is Argon2Error, "expected Argon2Error, got \(error)")
        }
    }

    func testZeroIterationsThrows() {
        XCTAssertThrowsError(
            try Argon2id.deriveKey(
                passphrase: Array("x".utf8),
                salt: Array(repeating: 1, count: 16),
                memoryKiB: 64,
                iterations: 0,
                parallelism: 1,
                hashLength: 32
            )
        ) { error in
            XCTAssertTrue(error is Argon2Error, "expected Argon2Error, got \(error)")
        }
    }

    /// Argon2 requires a salt of at least 8 bytes; a short one must be a typed
    /// error carrying the library's own diagnostic, not a crash.
    func testShortSaltThrowsWithTheLibraryDiagnostic() {
        XCTAssertThrowsError(
            try Argon2id.deriveKey(
                passphrase: Array("x".utf8),
                salt: [1, 2, 3],
                memoryKiB: 64,
                iterations: 1,
                parallelism: 1,
                hashLength: 32
            )
        ) { error in
            guard let argonError = error as? Argon2Error else {
                XCTFail("expected Argon2Error, got \(error)")
                return
            }
            XCTAssertNotEqual(argonError.code, 0)
            XCTAssertFalse(argonError.message.isEmpty)
        }
    }
}
