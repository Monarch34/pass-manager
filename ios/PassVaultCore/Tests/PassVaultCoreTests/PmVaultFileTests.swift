import XCTest
@testable import PassVaultCore

final class PmVaultFileTests: XCTestCase {

    private let passphrase = "export-passphrase"

    // MARK: - Round trip

    func testRoundTripAllFiveCategories() throws {
        let body = TestSupport.sampleBody()
        let file = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        let readBack = try PmVaultFile.read(file, passphrase: passphrase)
        XCTAssertEqual(readBack, body)
        XCTAssertEqual(readBack.items.count, 5)
        XCTAssertEqual(
            Set(readBack.items.map { $0.category }),
            Set(["login", "card", "bank", "note", "identity"])
        )
    }

    func testRoundTripPreservesTimestampsExactly() throws {
        let body = TestSupport.sampleBody(exportedAt: 1_787_123_456_789)
        let file = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        let readBack = try PmVaultFile.read(file, passphrase: passphrase)
        XCTAssertEqual(readBack.exportedAt, 1_787_123_456_789)
        for item in readBack.items {
            XCTAssertEqual(item.createdAt, 1_786_000_000_000)
            XCTAssertEqual(item.updatedAt, 1_787_123_456_789)
        }
    }

    func testEmptyVaultRoundTrips() throws {
        let body = PmVaultBody(version: 1, exportedAt: 1, items: [])
        let file = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertEqual(try PmVaultFile.read(file, passphrase: passphrase), body)
    }

    /// Fresh salt and fresh IV per export — never reuse either.
    func testEverySaltAndIvIsFresh() throws {
        let body = TestSupport.sampleBody()
        let first = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        let second = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        XCTAssertNotEqual(first, second)
        XCTAssertNotEqual(headerBytes(of: first), headerBytes(of: second))
    }

    // MARK: - Container layout (docs/FORMAT.md)

    func testContainerLayout() throws {
        let body = TestSupport.sampleBody()
        let file = try PmVaultFile.write(body: body, passphrase: passphrase, params: TestSupport.cheapKdf)
        let bytes = [UInt8](file)

        // magic
        XCTAssertEqual(Array(bytes[0..<4]), [0x50, 0x4D, 0x56, 0x54])
        XCTAssertEqual(String(decoding: bytes[0..<4], as: UTF8.self), "PMVT")

        // headerLen, unsigned 16-bit big-endian
        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        XCTAssertGreaterThan(headerLength, 0)
        XCTAssertLessThanOrEqual(headerLength, 4096)

        // header is UTF-8 JSON of exactly headerLen bytes
        let headerData = Data(bytes[6..<(6 + headerLength)])
        let header = try JSONDecoder().decode(PmVaultHeader.self, from: headerData)
        XCTAssertEqual(header.version, 1)
        XCTAssertEqual(header.kdf, TestSupport.cheapKdf)
        XCTAssertEqual(Data(base64Encoded: header.salt)?.count, 16)

        // iv (12) + body (>= 16-byte tag)
        XCTAssertGreaterThanOrEqual(bytes.count, 6 + headerLength + 12 + 16)
    }

    // MARK: - Failure modes

    func testWrongPassphraseIsIndistinguishableFromCorruption() throws {
        let file = try PmVaultFile.write(
            body: TestSupport.sampleBody(),
            passphrase: passphrase,
            params: TestSupport.cheapKdf
        )
        expect(.wrongPassphraseOrCorrupt) {
            _ = try PmVaultFile.read(file, passphrase: "not the passphrase")
        }
    }

    func testTamperedBodyFails() throws {
        let file = try PmVaultFile.write(
            body: TestSupport.sampleBody(),
            passphrase: passphrase,
            params: TestSupport.cheapKdf
        )
        var bytes = [UInt8](file)
        let lastIndex = bytes.count - 1
        bytes[lastIndex] = bytes[lastIndex] ^ 0xFF
        expect(.wrongPassphraseOrCorrupt) {
            _ = try PmVaultFile.read(Data(bytes), passphrase: self.passphrase)
        }
    }

    func testEmptyFileIsMalformed() {
        expect(.malformed) {
            _ = try PmVaultFile.read(Data(), passphrase: "x")
        }
    }

    func testBadMagicIsMalformed() {
        var bytes: [UInt8] = [0x50, 0x4D, 0x56, 0x55, 0x00, 0x10]
        bytes.append(contentsOf: [UInt8](repeating: 0, count: 64))
        expect(.malformed) {
            _ = try PmVaultFile.read(Data(bytes), passphrase: "x")
        }
    }

    func testTruncatedFileIsMalformed() throws {
        let file = try PmVaultFile.write(
            body: TestSupport.sampleBody(),
            passphrase: passphrase,
            params: TestSupport.cheapKdf
        )
        let bytes = [UInt8](file)
        // Cut to one byte below the shortest structurally possible file, so this
        // is a container-level rejection rather than a tag failure.
        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        let minimumLength = 6 + headerLength + 12 + 16
        XCTAssertGreaterThan(bytes.count, minimumLength)
        let truncated = Data(bytes[0..<(minimumLength - 1)])
        expect(.malformed) {
            _ = try PmVaultFile.read(truncated, passphrase: self.passphrase)
        }
    }

    /// A file that is still structurally plausible but has lost part of its body
    /// is NOT malformed — it is a tag failure, indistinguishable from a wrong
    /// passphrase.
    func testPartiallyLostBodyFailsTheTag() throws {
        let file = try PmVaultFile.write(
            body: TestSupport.sampleBody(),
            passphrase: passphrase,
            params: TestSupport.cheapKdf
        )
        let bytes = [UInt8](file)
        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        let minimumLength = 6 + headerLength + 12 + 16
        let shortened = Data(bytes[0..<(bytes.count - 8)])
        XCTAssertGreaterThanOrEqual(shortened.count, minimumLength)
        expect(.wrongPassphraseOrCorrupt) {
            _ = try PmVaultFile.read(shortened, passphrase: self.passphrase)
        }
    }

    func testHeaderCutOffMidWayIsMalformed() throws {
        let file = try PmVaultFile.write(
            body: TestSupport.sampleBody(),
            passphrase: passphrase,
            params: TestSupport.cheapKdf
        )
        let bytes = [UInt8](file)
        let truncated = Data(bytes[0..<10])
        expect(.malformed) {
            _ = try PmVaultFile.read(truncated, passphrase: self.passphrase)
        }
    }

    func testNonJsonHeaderIsMalformed() {
        let junk = String(repeating: "x", count: 32)
        expect(.malformed) {
            _ = try PmVaultFile.read(self.assemble(headerJSON: junk), passphrase: "x")
        }
    }

    func testUnknownVersionIsRejectedCleanly() {
        let json = self.headerJSON(version: 2, saltBase64: Self.salt16Base64)
        expect(.unsupportedVersion) {
            _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
        }
    }

    // MARK: - The mandatory pre-KDF gate

    func testHeaderLengthOverLimitIsRejected() {
        // headerLen = 0x2000 = 8192, over the 4096 ceiling.
        var bytes: [UInt8] = [0x50, 0x4D, 0x56, 0x54, 0x20, 0x00]
        bytes.append(contentsOf: [UInt8](repeating: 0x7B, count: 64))
        expect(.invalidHeaderParameters) {
            _ = try PmVaultFile.read(Data(bytes), passphrase: "x")
        }
    }

    func testMemoryOverCeilingIsRejected() {
        let json = headerJSON(saltBase64: Self.salt16Base64, memory: 262_145)
        expect(.invalidHeaderParameters) {
            _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
        }
    }

    func testIterationsOutOfRangeAreRejected() {
        for iterations in [0, 17, -3] {
            let json = headerJSON(saltBase64: Self.salt16Base64, iterations: iterations)
            expect(.invalidHeaderParameters) {
                _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
            }
        }
    }

    func testParallelismOutOfRangeIsRejected() {
        for parallelism in [0, 9] {
            let json = headerJSON(saltBase64: Self.salt16Base64, parallelism: parallelism)
            expect(.invalidHeaderParameters) {
                _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
            }
        }
    }

    func testHashLengthOtherThan32IsRejected() {
        for hashLength in [16, 64] {
            let json = headerJSON(saltBase64: Self.salt16Base64, hashLength: hashLength)
            expect(.invalidHeaderParameters) {
                _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
            }
        }
    }

    func testSaltOtherThan16BytesIsRejected() {
        let shortSalt = Data(repeating: 4, count: 8).base64EncodedString()
        let longSalt = Data(repeating: 4, count: 32).base64EncodedString()
        for salt in [shortSalt, longSalt] {
            let json = headerJSON(saltBase64: salt)
            expect(.invalidHeaderParameters) {
                _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
            }
        }
    }

    func testNonBase64SaltIsRejected() {
        let json = headerJSON(saltBase64: "!!!not base64!!!")
        expect(.invalidHeaderParameters) {
            _ = try PmVaultFile.read(self.assemble(headerJSON: json), passphrase: "x")
        }
    }

    /// The point of the gate: a crafted header asking for a 1 GiB / t=16 / p=8
    /// derivation must be rejected without ever touching Argon2. Doing the
    /// derivation would take tens of seconds and a gigabyte of RAM, so returning
    /// promptly is the observable proof that validation ran first.
    func testAbsurdCostIsRejectedBeforeAnyDerivation() {
        let json = headerJSON(
            saltBase64: Self.salt16Base64,
            memory: 1_048_576,
            iterations: 16,
            parallelism: 8
        )
        let file = assemble(headerJSON: json)
        let start = Date()
        expect(.invalidHeaderParameters) {
            _ = try PmVaultFile.read(file, passphrase: "x")
        }
        XCTAssertLessThan(Date().timeIntervalSince(start), 5.0, "the KDF appears to have run before validation")
    }

    // MARK: - AAD

    /// Rewrites the stored header with a byte-for-byte DIFFERENT but semantically
    /// IDENTICAL header (the same JSON keys in a different order, so the length,
    /// the salt and the cost parameters are all unchanged). The derived key is
    /// therefore identical and the only thing that differs is the AAD — so this
    /// isolates the AAD check from everything else.
    func testHeaderTamperingFailsTheAadCheck() throws {
        let salt = [UInt8](repeating: 0x2B, count: 16)
        let saltBase64 = Data(salt).base64EncodedString()
        let params = TestSupport.cheapKdf

        let authenticHeader = headerJSON(saltBase64: saltBase64)
        let permutedHeader = permutedHeaderJSON(saltBase64: saltBase64)
        XCTAssertEqual(
            authenticHeader.utf8.count,
            permutedHeader.utf8.count,
            "the permuted header must be the same length, otherwise this tests the wrong thing"
        )
        XCTAssertNotEqual(authenticHeader, permutedHeader)

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let plaintext = try encoder.encode(TestSupport.sampleBody())

        let key = try Argon2id.deriveKey(passphrase: Array("pw".utf8), salt: salt, params: params)
        let prefix = PmVaultFile.makePrefix(headerData: Data(authenticHeader.utf8))
        let sealed = try AesGcm.seal(plaintext, key: Data(key), aad: prefix)

        // Sanity: with the authentic header the file reads back fine.
        let authenticFile = assemble(headerJSON: authenticHeader, iv: sealed.nonce, body: sealed.ciphertext)
        XCTAssertNoThrow(try PmVaultFile.read(authenticFile, passphrase: "pw"))

        // Swap in the permuted header: same key, same ciphertext, different AAD.
        let tamperedFile = assemble(headerJSON: permutedHeader, iv: sealed.nonce, body: sealed.ciphertext)
        expect(.wrongPassphraseOrCorrupt) {
            _ = try PmVaultFile.read(tamperedFile, passphrase: "pw")
        }
    }

    // MARK: - Helpers

    private static let salt16Base64 = Data(repeating: 0x2B, count: 16).base64EncodedString()

    private func headerJSON(
        version: Int = 1,
        saltBase64: String,
        memory: Int = 64,
        iterations: Int = 1,
        parallelism: Int = 1,
        hashLength: Int = 32
    ) -> String {
        return "{\"version\":\(version),\"salt\":\"\(saltBase64)\",\"kdf\":{\"memory\":\(memory),"
            + "\"iterations\":\(iterations),\"parallelism\":\(parallelism),\"hashLength\":\(hashLength)}}"
    }

    /// The same keys and values as `headerJSON` with the defaults, reordered.
    /// Reordering an object's keys cannot change the byte COUNT, which is what
    /// keeps `headerLen` and every downstream offset identical.
    private func permutedHeaderJSON(saltBase64: String) -> String {
        return "{\"kdf\":{\"hashLength\":32,\"iterations\":1,\"memory\":64,\"parallelism\":1},"
            + "\"salt\":\"\(saltBase64)\",\"version\":1}"
    }

    private func assemble(
        headerJSON: String,
        iv: Data = Data(repeating: 0, count: 12),
        body: Data = Data(repeating: 0, count: 64)
    ) -> Data {
        let headerData = Data(headerJSON.utf8)
        var out = Data()
        out.append(contentsOf: PmVaultFile.magic)
        out.append(UInt8((headerData.count >> 8) & 0xFF))
        out.append(UInt8(headerData.count & 0xFF))
        out.append(headerData)
        out.append(iv)
        out.append(body)
        return out
    }

    private func headerBytes(of file: Data) -> Data {
        let bytes = [UInt8](file)
        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        return Data(bytes[6..<(6 + headerLength)])
    }

    private func expect(
        _ kind: PmVaultError.Kind,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ body: () throws -> Void
    ) {
        XCTAssertThrowsError(try body(), "expected \(kind.rawValue)", file: file, line: line) { error in
            guard let pmError = error as? PmVaultError else {
                XCTFail("expected PmVaultError, got \(error)", file: file, line: line)
                return
            }
            XCTAssertEqual(pmError.kind, kind, "got \(pmError)", file: file, line: line)
        }
    }
}
