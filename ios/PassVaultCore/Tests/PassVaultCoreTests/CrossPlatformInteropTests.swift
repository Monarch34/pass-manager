import XCTest
import Foundation
import Crypto
@testable import PassVaultCore

/// The proof that the two apps actually interoperate.
///
/// Everything else in this package tests iOS against itself. This suite tests it
/// against bytes Android produced, and pins bytes Android has to read back.
///
/// PROVENANCE of `Fixtures/`:
///
/// - `android-export-v1.pmvault` was produced by Android's PRODUCTION
///   `ExportVaultUseCase`, and independently decrypted outside the JVM with
///   `argon2-cffi` + Python AES-GCM before it was committed — so the reference
///   Argon2 implementation agrees it is well formed.
/// - `android-export-v1.expected.json` is a verbatim copy of that file's
///   decrypted body.
/// - `ios-export-v1.pmvault` is the reverse direction, for Track A to read on
///   Android. It carries the same five records under the same passphrase.
///   HONESTY NOTE: it was assembled by a script replicating this package's
///   writer, because the machine that produced it has no Swift toolchain. It is
///   not taken on trust — `testIosExportIsExactlyWhatThisWriterProduces` rebuilds
///   it from this package's own Argon2, AES-GCM and JSON encoder and asserts the
///   result is byte-identical, so if the replica were wrong in any respect, CI
///   fails.
///
/// The files under `Tests/PassVaultCoreTests/Fixtures/` are copies of the
/// canonical ones in the repository-root `fixtures/` directory; SwiftPM only
/// bundles resources that live inside the target. `testFixtureDigestsArePinned`
/// is what catches the two copies drifting apart.
final class CrossPlatformInteropTests: XCTestCase {

    private static let passphrase = "CrossPlatform-Fixture-2026"

    // MARK: - The expected records, written out by hand

    /// Deliberately hand-written rather than parsed from the JSON: decoding the
    /// expectation with the same decoder under test would make the comparison
    /// circular. These literals are read off `expected.json` by eye.
    private static func expectedItems() -> [PmVaultItem] {
        return [
            PmVaultItem(
                id: "a1000000-0000-4000-8000-000000000001",
                category: "login",
                createdAt: 1_735_689_600_000,
                updatedAt: 1_767_225_600_000,
                payload: .login(ItemPayload.Login(
                    id: "a1000000-0000-4000-8000-000000000001",
                    title: "GitHub",
                    notes: "",
                    username: "octocat",
                    address: "https://github.com",
                    password: "hunter2"
                ))
            ),
            PmVaultItem(
                id: "a1000000-0000-4000-8000-000000000002",
                category: "card",
                createdAt: 1_736_000_000_000,
                updatedAt: 1_770_000_000_000,
                payload: .card(ItemPayload.Card(
                    id: "a1000000-0000-4000-8000-000000000002",
                    title: "Kadıköy Bankası Kartı",
                    notes: "Ay sonu ödemesi",
                    cardholderName: "Ayşe Yılmaz",
                    cardNumber: "4111111111111111",
                    cardCvc: "123",
                    cardExpiry: "12/29"
                ))
            ),
            PmVaultItem(
                id: "a1000000-0000-4000-8000-000000000003",
                category: "note",
                createdAt: 1_737_000_000_000,
                updatedAt: 1_771_000_000_000,
                payload: .note(ItemPayload.SecureNote(
                    id: "a1000000-0000-4000-8000-000000000003",
                    title: "Kurtarma kodları",
                    notes: "satır 1\nsatır 2\tsekmeli\n\"tırnaklı\" ve ters bölü \\ ile 🔐"
                ))
            ),
            PmVaultItem(
                id: "a1000000-0000-4000-8000-000000000004",
                category: "identity",
                createdAt: 1_738_000_000_000,
                updatedAt: 1_772_000_000_000,
                payload: .identity(ItemPayload.Identity(
                    id: "a1000000-0000-4000-8000-000000000004",
                    title: "Pasaport bilgileri",
                    notes: "",
                    firstName: "Ayşe",
                    lastName: "Yılmaz",
                    email: "ayse@example.com",
                    phone: "+90 555 000 00 00",
                    address: "Kadıköy, İstanbul",
                    company: "ACME Yazılım A.Ş."
                ))
            ),
            PmVaultItem(
                id: "a1000000-0000-4000-8000-000000000005",
                category: "bank",
                createdAt: 1_739_000_000_000,
                updatedAt: 1_773_000_000_000,
                payload: .bank(ItemPayload.Bank(
                    id: "a1000000-0000-4000-8000-000000000005",
                    title: "Kadıköy Bankası",
                    notes: "Ortak hesap",
                    accountNumber: "TR33 0006 1005 1978 6457 8413 26",
                    bankName: "Kadıköy Bankası",
                    password: "Bank-2026!x",
                    previousPasswords: ["Eski-2025!a", "Daha-Eski-2024!b"]
                ))
            )
        ]
    }

    // MARK: - Forward: Android wrote it, iOS reads it

    func testAndroidExportDecryptsFieldByField() throws {
        let body = try PmVaultFile.read(
            try fixture("android-export-v1", "pmvault"),
            passphrase: Self.passphrase
        )

        XCTAssertEqual(body.version, 1)
        XCTAssertEqual(body.exportedAt, 1_787_000_000_000)

        let expected = Self.expectedItems()
        XCTAssertEqual(body.items.count, expected.count)

        for (actual, want) in zip(body.items, expected) {
            XCTAssertEqual(actual.id, want.id)
            XCTAssertEqual(actual.category, want.category)
            XCTAssertEqual(actual.createdAt, want.createdAt, "createdAt for \(want.id)")
            XCTAssertEqual(actual.updatedAt, want.updatedAt, "updatedAt for \(want.id)")
            XCTAssertEqual(actual.payload, want.payload, "payload for \(want.id)")
            // `category` duplicates `payload.type`; the payload is authoritative
            // and the two must agree.
            XCTAssertEqual(actual.payload.category.rawValue, actual.category)
        }
    }

    /// Every timestamp is distinct in this fixture precisely so a reader that
    /// collapsed `createdAt` into `updatedAt` — the Android repository bug this
    /// port deliberately did not inherit — would fail here.
    func testTimestampsSurviveExactly() throws {
        let body = try PmVaultFile.read(
            try fixture("android-export-v1", "pmvault"),
            passphrase: Self.passphrase
        )
        for item in body.items {
            XCTAssertNotEqual(item.createdAt, item.updatedAt, "\(item.id)")
        }
        XCTAssertEqual(body.items.map { $0.createdAt }, [
            1_735_689_600_000, 1_736_000_000_000, 1_737_000_000_000,
            1_738_000_000_000, 1_739_000_000_000
        ])
        XCTAssertEqual(body.items.map { $0.updatedAt }, [
            1_767_225_600_000, 1_770_000_000_000, 1_771_000_000_000,
            1_772_000_000_000, 1_773_000_000_000
        ])
    }

    /// Turkish letters, a tab, a newline, an escaped quote, a backslash and an
    /// astral-plane emoji, all through Argon2 → AES-GCM → JSON → Swift `String`.
    func testMultibyteAndEscapedTextSurvives() throws {
        let body = try PmVaultFile.read(
            try fixture("android-export-v1", "pmvault"),
            passphrase: Self.passphrase
        )
        guard case .note(let note) = body.items[2].payload else {
            XCTFail("item 2 should be a note")
            return
        }
        XCTAssertEqual(note.title, "Kurtarma kodları")
        XCTAssertTrue(note.notes.contains("\n"))
        XCTAssertTrue(note.notes.contains("\t"))
        XCTAssertTrue(note.notes.contains("\"tırnaklı\""))
        XCTAssertTrue(note.notes.contains("\\"))
        XCTAssertTrue(note.notes.contains("🔐"))
        // The emoji is one Character but two UTF-16 code units and four UTF-8
        // bytes; a truncation bug anywhere in the chain shows up here.
        XCTAssertEqual(note.notes.unicodeScalars.filter { $0.value == 0x1F510 }.count, 1)

        guard case .identity(let identity) = body.items[3].payload else {
            XCTFail("item 3 should be an identity")
            return
        }
        XCTAssertEqual(identity.address, "Kadıköy, İstanbul")
        XCTAssertEqual(identity.company, "ACME Yazılım A.Ş.")
    }

    /// Android omits fields carrying the Kotlin default, so `notes` is absent
    /// from the login and identity payloads. Absent must decode to `""`, not to
    /// a failure and not to a missing key propagating onward.
    func testOmittedDefaultsDecodeToEmpty() throws {
        let body = try PmVaultFile.read(
            try fixture("android-export-v1", "pmvault"),
            passphrase: Self.passphrase
        )
        let raw = try JSONSerialization.jsonObject(
            with: try fixture("android-export-v1", "expected.json")
        )
        guard
            let root = raw as? [String: Any],
            let items = root["items"] as? [[String: Any]]
        else {
            XCTFail("expected.json is not shaped as expected")
            return
        }

        // Confirm the fixture really does omit them, so this test cannot pass
        // vacuously against a file that started carrying explicit empties.
        let loginPayload = items[0]["payload"] as? [String: Any]
        XCTAssertNil(loginPayload?["notes"], "the fixture no longer exercises omitted defaults")
        let identityPayload = items[3]["payload"] as? [String: Any]
        XCTAssertNil(identityPayload?["notes"])

        XCTAssertEqual(body.items[0].payload.notes, "")
        XCTAssertEqual(body.items[3].payload.notes, "")
        // And a field that IS present is not blanked by the same code path.
        XCTAssertEqual(body.items[1].payload.notes, "Ay sonu ödemesi")
    }

    func testDecryptedPlaintextIsByteIdenticalToExpectedJson() throws {
        let plaintext = try decryptRaw(try fixture("android-export-v1", "pmvault"))
        let expected = try fixture("android-export-v1", "expected.json")
        XCTAssertEqual(plaintext, expected)
    }

    func testWrongPassphraseOnTheAndroidFixtureIsRejected() throws {
        let data = try fixture("android-export-v1", "pmvault")
        XCTAssertThrowsError(try PmVaultFile.read(data, passphrase: "not the passphrase")) { error in
            guard let pmError = error as? PmVaultError else {
                XCTFail("expected PmVaultError, got \(error)")
                return
            }
            XCTAssertEqual(pmError.kind, .wrongPassphraseOrCorrupt)
        }
    }

    // MARK: - The discriminator's position

    /// `docs/FORMAT.md` fixes no key order. Android happens to write `"type"`
    /// first; this package's `.sortedKeys` encoder writes it in the middle. Both
    /// must decode, so the decoder may not depend on position — and neither may
    /// a reader that only ever saw Android's output.
    func testDiscriminatorIsFoundAnywhereInTheObject() throws {
        let first = """
        {"type":"login","id":"x","title":"T","username":"u","password":"p"}
        """
        let middle = """
        {"id":"x","password":"p","title":"T","type":"login","username":"u"}
        """
        let last = """
        {"id":"x","password":"p","title":"T","username":"u","type":"login"}
        """
        let expected = ItemPayload.login(ItemPayload.Login(
            id: "x", title: "T", username: "u", password: "p"
        ))
        for json in [first, middle, last] {
            XCTAssertEqual(try PayloadJson.decode(fromString: json), expected, json)
        }
    }

    /// The property Android has to tolerate, asserted rather than assumed: this
    /// writer does NOT put `"type"` first.
    func testThisWriterEmitsTheDiscriminatorAwayFromTheFront() throws {
        let payload = ItemPayload.login(ItemPayload.Login(
            id: "x", title: "T", username: "u", address: "https://a.example", password: "p"
        ))
        let json = try PayloadJson.encodeToString(payload)
        XCTAssertTrue(json.contains("\"type\":\"login\""))
        XCTAssertFalse(
            json.hasPrefix("{\"type\""),
            "sorted keys should not leave type first; got \(json)"
        )
        // Round trips through this package regardless.
        XCTAssertEqual(try PayloadJson.decode(fromString: json), payload)
    }

    // MARK: - Reverse: iOS wrote it, Android must read it

    func testIosExportCarriesTheSameFiveRecords() throws {
        let body = try PmVaultFile.read(
            try fixture("ios-export-v1", "pmvault"),
            passphrase: Self.passphrase
        )
        XCTAssertEqual(body.version, 1)
        XCTAssertEqual(body.exportedAt, 1_787_000_000_000)
        XCTAssertEqual(body.items.count, 5)

        for (actual, want) in zip(body.items, Self.expectedItems()) {
            XCTAssertEqual(actual.id, want.id)
            XCTAssertEqual(actual.category, want.category)
            XCTAssertEqual(actual.createdAt, want.createdAt)
            XCTAssertEqual(actual.updatedAt, want.updatedAt)
            XCTAssertEqual(actual.payload, want.payload)
        }
    }

    /// The committed reverse fixture is not taken on trust. Rebuild it from this
    /// package's own primitives, reusing only the salt and IV that a writer would
    /// have drawn at random, and require byte equality. If the generator script
    /// diverged from this writer anywhere — key order, slash escaping, omitted
    /// defaults, AAD extent, tag placement — this fails.
    func testIosExportIsExactlyWhatThisWriterProduces() throws {
        let file = [UInt8](try fixture("ios-export-v1", "pmvault"))

        let headerLength = (Int(file[4]) << 8) | Int(file[5])
        let headerEnd = 6 + headerLength
        let storedHeader = Data(file[6..<headerEnd])
        let storedIv = Data(file[headerEnd..<(headerEnd + 12)])
        let storedBody = Data(file[(headerEnd + 12)..<file.count])

        let header = try JSONDecoder().decode(PmVaultHeader.self, from: storedHeader)
        let salt = try XCTUnwrap(Data(base64Encoded: header.salt))

        // 1. The header is byte-for-byte what this writer would emit.
        let rebuiltHeader = try PmVaultFile.makeEncoder().encode(
            PmVaultHeader(version: 1, salt: header.salt, kdf: header.kdf)
        )
        assertBytesEqual(rebuiltHeader, storedHeader, "header")

        // 2. The body plaintext is byte-for-byte what this writer would emit.
        let key = try Argon2id.deriveKey(
            passphrase: Data(Self.passphrase.utf8),
            salt: salt,
            params: header.kdf
        )
        let aad = Data(file[0..<headerEnd])
        let plaintext = try AesGcm.open(
            AesGcm.Sealed(nonce: storedIv, ciphertext: storedBody),
            key: key,
            aad: aad
        )
        let decoded = try JSONDecoder().decode(PmVaultBody.self, from: plaintext)
        let reencoded = try PmVaultFile.makeEncoder().encode(decoded)
        assertBytesEqual(reencoded, plaintext, "body")

        // 3. Sealing that plaintext with the same key, IV and AAD reproduces the
        //    stored ciphertext exactly, which pins the whole container.
        let resealed = try AesGcm.seal(plaintext, key: key, nonce: storedIv, aad: aad)
        XCTAssertEqual(resealed.ciphertext, storedBody, "ciphertext differs")

        var rebuiltFile = PmVaultFile.makePrefix(headerData: rebuiltHeader)
        rebuiltFile.append(storedIv)
        rebuiltFile.append(resealed.ciphertext)
        XCTAssertEqual([UInt8](rebuiltFile), file, "reassembled file differs")
    }

    /// The reverse fixture must use the parameters `docs/FORMAT.md` pins, not
    /// whatever happened to be cheap when it was generated.
    func testIosExportUsesThePinnedKdfCost() throws {
        let file = [UInt8](try fixture("ios-export-v1", "pmvault"))
        let headerLength = (Int(file[4]) << 8) | Int(file[5])
        let header = try JSONDecoder().decode(
            PmVaultHeader.self,
            from: Data(file[6..<(6 + headerLength)])
        )
        XCTAssertEqual(header.version, 1)
        XCTAssertEqual(header.kdf, KdfParams.standard)
        XCTAssertEqual(Data(base64Encoded: header.salt)?.count, 16)
        XCTAssertEqual(String(decoding: file[0..<4], as: UTF8.self), "PMVT")
    }

    // MARK: - Fixture integrity

    /// Pins the exact bytes this suite was written against. If the canonical
    /// files in the repository root are regenerated and only one copy is
    /// refreshed, this says so instead of the suite quietly testing a stale file.
    func testFixtureDigestsArePinned() throws {
        let cases: [(String, String, String)] = [
            ("android-export-v1", "pmvault",
             "45a9062da3d544e065b1118f491d4d808abd850850069aa7c6405826366e1f75"),
            ("android-export-v1", "expected.json",
             "ffe42ea583e5fb4907727e4c3912b4ca75c6a12bc4a5c1803af82b67ceb949f1"),
            ("ios-export-v1", "pmvault",
             "ad5aabb5f61c904685645563169624b69e4b672be56a4867a86a99eb223eb1a0")
        ]
        for (name, ext, digest) in cases {
            let data = try fixture(name, ext)
            XCTAssertEqual(sha256Hex(data), digest, "\(name).\(ext)")
        }
    }

    func testFixtureSizesMatchWhatWasCommitted() throws {
        XCTAssertEqual(try fixture("android-export-v1", "pmvault").count, 1910)
        XCTAssertEqual(try fixture("android-export-v1", "expected.json").count, 1759)
        XCTAssertEqual(try fixture("ios-export-v1", "pmvault").count, 1910)
    }

    // MARK: - Helpers

    private func fixture(_ name: String, _ ext: String) throws -> Data {
        let url = Bundle.module.url(
            forResource: name,
            withExtension: ext,
            subdirectory: "Fixtures"
        )
        let resolved = try XCTUnwrap(url, "missing fixture \(name).\(ext)")
        return try Data(contentsOf: resolved)
    }

    /// Decrypts a container down to the raw plaintext bytes, which
    /// `PmVaultFile.read` deliberately does not expose.
    private func decryptRaw(_ file: Data) throws -> Data {
        let bytes = [UInt8](file)
        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        let headerEnd = 6 + headerLength
        let header = try JSONDecoder().decode(
            PmVaultHeader.self,
            from: Data(bytes[6..<headerEnd])
        )
        let salt = try XCTUnwrap(Data(base64Encoded: header.salt))
        let key = try Argon2id.deriveKey(
            passphrase: Data(Self.passphrase.utf8),
            salt: salt,
            params: header.kdf
        )
        let sealed = AesGcm.Sealed(
            nonce: Data(bytes[headerEnd..<(headerEnd + 12)]),
            ciphertext: Data(bytes[(headerEnd + 12)..<bytes.count])
        )
        return try AesGcm.open(sealed, key: key, aad: Data(bytes[0..<headerEnd]))
    }

    /// `XCTAssertEqual` on two `Data` values prints only "1759 bytes is not equal
    /// to 1759 bytes", which says nothing about WHERE they diverge. This reports
    /// the offset and the surrounding text, which is what actually identifies a
    /// JSON key-order or escaping difference.
    private func assertBytesEqual(
        _ actual: Data,
        _ expected: Data,
        _ label: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        if actual == expected {
            return
        }
        let lhs = [UInt8](actual)
        let rhs = [UInt8](expected)
        var offset = 0
        while offset < min(lhs.count, rhs.count) && lhs[offset] == rhs[offset] {
            offset += 1
        }
        let start = max(0, offset - 40)
        let end = min(min(lhs.count, rhs.count), offset + 40)
        let actualWindow = String(decoding: lhs[start..<min(end, lhs.count)], as: UTF8.self)
        let expectedWindow = String(decoding: rhs[start..<min(end, rhs.count)], as: UTF8.self)
        XCTFail(
            """
            \(label) bytes differ at offset \(offset) \
            (\(lhs.count) vs \(expected.count) bytes)
              rebuilt : …\(actualWindow)…
              stored  : …\(expectedWindow)…
            """,
            file: file,
            line: line
        )
    }

    private func sha256Hex(_ data: Data) -> String {
        var output = ""
        for byte in SHA256.hash(data: data) {
            output += String(format: "%02x", byte)
        }
        return output
    }
}
