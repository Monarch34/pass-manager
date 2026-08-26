import XCTest
@testable import PassVaultCore

final class AesGcmTests: XCTestCase {

    private let key = Data(repeating: 0xA5, count: 32)
    private let otherKey = Data(repeating: 0x5A, count: 32)
    private let plaintext = Data("the quick brown fox jumps over the lazy dog".utf8)

    func testRoundTripWithoutAad() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        let opened = try AesGcm.open(sealed, key: key)
        XCTAssertEqual(opened, plaintext)
    }

    func testRoundTripWithAad() throws {
        let aad = Data("PMVT-header".utf8)
        let sealed = try AesGcm.seal(plaintext, key: key, aad: aad)
        let opened = try AesGcm.open(sealed, key: key, aad: aad)
        XCTAssertEqual(opened, plaintext)
    }

    /// The property the `.pmvault` container leans on: change the AAD and the tag
    /// stops verifying, even though the key, nonce and ciphertext are untouched.
    func testAadMismatchFails() throws {
        let sealed = try AesGcm.seal(plaintext, key: key, aad: Data("header-A".utf8))
        XCTAssertThrowsError(try AesGcm.open(sealed, key: key, aad: Data("header-B".utf8))) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    func testMissingAadFailsWhenOneWasUsed() throws {
        let sealed = try AesGcm.seal(plaintext, key: key, aad: Data("header-A".utf8))
        XCTAssertThrowsError(try AesGcm.open(sealed, key: key)) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    func testUnexpectedAadFailsWhenNoneWasUsed() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        XCTAssertThrowsError(try AesGcm.open(sealed, key: key, aad: Data("header-A".utf8))) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    func testWrongKeyFails() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        XCTAssertThrowsError(try AesGcm.open(sealed, key: otherKey)) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    func testTamperedCiphertextFails() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        var bytes = [UInt8](sealed.ciphertext)
        bytes[0] = bytes[0] ^ 0xFF
        let tampered = AesGcm.Sealed(nonce: sealed.nonce, ciphertext: Data(bytes))
        XCTAssertThrowsError(try AesGcm.open(tampered, key: key)) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    func testTamperedTagFails() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        var bytes = [UInt8](sealed.ciphertext)
        let lastIndex = bytes.count - 1
        bytes[lastIndex] = bytes[lastIndex] ^ 0xFF
        let tampered = AesGcm.Sealed(nonce: sealed.nonce, ciphertext: Data(bytes))
        XCTAssertThrowsError(try AesGcm.open(tampered, key: key)) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.authenticationFailure)
        }
    }

    /// JCA's `AES/GCM/NoPadding` produces `ciphertext || tag` with a 128-bit tag,
    /// and so must this, or Android cannot read what iOS writes.
    func testLayoutMatchesJcaCiphertextPlusTag() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        XCTAssertEqual(sealed.nonce.count, 12)
        XCTAssertEqual(sealed.ciphertext.count, plaintext.count + 16)
    }

    func testNonceIsFreshPerSeal() throws {
        let first = try AesGcm.seal(plaintext, key: key)
        let second = try AesGcm.seal(plaintext, key: key)
        XCTAssertNotEqual(first.nonce, second.nonce)
        XCTAssertNotEqual(first.ciphertext, second.ciphertext)
    }

    func testEmptyPlaintextRoundTrips() throws {
        let sealed = try AesGcm.seal(Data(), key: key)
        XCTAssertEqual(sealed.ciphertext.count, 16)
        let opened = try AesGcm.open(sealed, key: key)
        XCTAssertEqual(opened, Data())
    }

    func testShortKeyIsRejected() {
        XCTAssertThrowsError(try AesGcm.seal(plaintext, key: Data(repeating: 1, count: 16))) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.invalidKeyLength(16))
        }
    }

    func testShortNonceIsRejected() {
        XCTAssertThrowsError(
            try AesGcm.seal(plaintext, key: key, nonce: Data(repeating: 1, count: 8))
        ) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.invalidNonceLength(8))
        }
    }

    func testCiphertextShorterThanTagIsRejected() {
        let sealed = AesGcm.Sealed(nonce: Data(repeating: 0, count: 12), ciphertext: Data([1, 2, 3]))
        XCTAssertThrowsError(try AesGcm.open(sealed, key: key)) { error in
            XCTAssertEqual(error as? AesGcmError, AesGcmError.invalidCiphertextLength(3))
        }
    }

    /// A `Data` slice with a non-zero `startIndex` must not shift the
    /// ciphertext/tag split.
    func testOpenHandlesSlicedData() throws {
        let sealed = try AesGcm.seal(plaintext, key: key)
        var padded = Data([0xDE, 0xAD, 0xBE, 0xEF])
        padded.append(sealed.ciphertext)
        let slice = padded[4...]
        XCTAssertNotEqual(slice.startIndex, 0)
        let reconstructed = AesGcm.Sealed(nonce: sealed.nonce, ciphertext: slice)
        let opened = try AesGcm.open(reconstructed, key: key)
        XCTAssertEqual(opened, plaintext)
    }
}
