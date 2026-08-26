import XCTest
@testable import PassVaultCore

final class ItemPayloadTests: XCTestCase {

    // MARK: - Round trips

    func testAllCategoriesRoundTrip() throws {
        for payload in TestSupport.samplePayloads() {
            let data = try PayloadJson.encode(payload)
            let decoded = try PayloadJson.decode(data)
            XCTAssertEqual(decoded, payload, "round trip failed for \(payload.category.rawValue)")
        }
    }

    func testEncodingIsStableAcrossCalls() throws {
        for payload in TestSupport.samplePayloads() {
            let first = try PayloadJson.encode(payload)
            let second = try PayloadJson.encode(payload)
            XCTAssertEqual(first, second)
        }
    }

    func testDiscriminatorMatchesCategoryForEveryCase() throws {
        for payload in TestSupport.samplePayloads() {
            let data = try PayloadJson.encode(payload)
            let object = try jsonObject(data)
            XCTAssertEqual(object["type"] as? String, payload.category.rawValue)
        }
    }

    // MARK: - `encodeDefaults = false` parity

    func testEmptyOptionalFieldsAreOmitted() throws {
        let payload = ItemPayload.login(ItemPayload.Login(id: "abc", title: "Bare"))
        let object = try jsonObject(try PayloadJson.encode(payload))
        XCTAssertEqual(Set(object.keys), Set(["type", "id", "title"]))
    }

    func testEmptyPreviousPasswordsIsOmitted() throws {
        let payload = ItemPayload.bank(ItemPayload.Bank(
            id: "abc",
            title: "Bank",
            password: "secret"
        ))
        let object = try jsonObject(try PayloadJson.encode(payload))
        XCTAssertEqual(Set(object.keys), Set(["type", "id", "title", "password"]))
    }

    /// `id` and `title` have no Kotlin default, so kotlinx writes them even when
    /// empty. Omitting them here would break byte-compatibility.
    func testIdAndTitleAreWrittenEvenWhenEmpty() throws {
        let payload = ItemPayload.note(ItemPayload.SecureNote(id: "", title: ""))
        let object = try jsonObject(try PayloadJson.encode(payload))
        XCTAssertEqual(Set(object.keys), Set(["type", "id", "title"]))
        XCTAssertEqual(object["id"] as? String, "")
        XCTAssertEqual(object["title"] as? String, "")
    }

    func testPopulatedFieldsAreAllWritten() throws {
        let payload = ItemPayload.identity(ItemPayload.Identity(
            id: "i",
            title: "Me",
            notes: "n",
            firstName: "Ada",
            lastName: "Lovelace",
            email: "ada@example.com",
            phone: "+1",
            address: "Somewhere",
            company: "Analytical Engines"
        ))
        let object = try jsonObject(try PayloadJson.encode(payload))
        XCTAssertEqual(
            Set(object.keys),
            Set(["type", "id", "title", "notes", "firstName", "lastName", "email", "phone", "address", "company"])
        )
    }

    /// Foundation escapes forward slashes by default; kotlinx never does. An
    /// address is almost always a URL, so this would show up immediately.
    func testForwardSlashesAreNotEscaped() throws {
        let payload = ItemPayload.login(ItemPayload.Login(
            id: "1",
            title: "GitHub",
            address: "https://github.com"
        ))
        let json = try PayloadJson.encodeToString(payload)
        XCTAssertTrue(json.contains("https://github.com"), "unexpected JSON: \(json)")
        XCTAssertFalse(json.contains("\\/"), "unexpected JSON: \(json)")
    }

    // MARK: - Decoding hand-written Android-shaped JSON

    func testDecodesAndroidLoginLiteral() throws {
        let json = """
        {"type":"login","id":"11111111-2222-3333-4444-555555555555","title":"GitHub","username":"octocat","address":"https://github.com","password":"hunter2"}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .login(ItemPayload.Login(
            id: "11111111-2222-3333-4444-555555555555",
            title: "GitHub",
            notes: "",
            username: "octocat",
            address: "https://github.com",
            password: "hunter2"
        )))
    }

    func testDecodesAndroidCardLiteral() throws {
        let json = """
        {"type":"card","id":"card-1","title":"Visa","cardholderName":"Ada Lovelace","cardNumber":"4111111111111111","cardCvc":"737","cardExpiry":"12/30"}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .card(ItemPayload.Card(
            id: "card-1",
            title: "Visa",
            cardholderName: "Ada Lovelace",
            cardNumber: "4111111111111111",
            cardCvc: "737",
            cardExpiry: "12/30"
        )))
    }

    func testDecodesAndroidBankLiteral() throws {
        let json = """
        {"type":"bank","id":"bank-1","title":"Ziraat","accountNumber":"TR00","bankName":"Ziraat","password":"Bank!2345","previousPasswords":["Old!1234","Older!123"]}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .bank(ItemPayload.Bank(
            id: "bank-1",
            title: "Ziraat",
            accountNumber: "TR00",
            bankName: "Ziraat",
            password: "Bank!2345",
            previousPasswords: ["Old!1234", "Older!123"]
        )))
    }

    func testDecodesAndroidNoteLiteral() throws {
        let json = """
        {"type":"note","id":"note-1","title":"Recovery","notes":"a1b2"}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .note(ItemPayload.SecureNote(
            id: "note-1",
            title: "Recovery",
            notes: "a1b2"
        )))
    }

    func testDecodesAndroidIdentityLiteral() throws {
        let json = """
        {"type":"identity","id":"id-1","title":"Passport","firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"+90","address":"Kadıköy","company":"AE"}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .identity(ItemPayload.Identity(
            id: "id-1",
            title: "Passport",
            firstName: "Ada",
            lastName: "Lovelace",
            email: "ada@example.com",
            phone: "+90",
            address: "Kadıköy",
            company: "AE"
        )))
    }

    /// Kotlin's `ignoreUnknownKeys = true`: a field added by a newer build must
    /// not crash an older one.
    func testUnknownFieldsAreIgnored() throws {
        let json = """
        {"type":"login","id":"1","title":"T","futureField":"whatever","anotherOne":42}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .login(ItemPayload.Login(id: "1", title: "T")))
    }

    func testMissingOptionalFieldsDecodeToEmpty() throws {
        let json = """
        {"type":"bank","id":"1","title":"T"}
        """
        let decoded = try PayloadJson.decode(fromString: json)
        XCTAssertEqual(decoded, .bank(ItemPayload.Bank(id: "1", title: "T")))
        XCTAssertEqual(decoded.notes, "")
    }

    /// An unknown discriminator is a hard error, NOT a silent downgrade to login.
    func testUnknownTypeIsRejected() {
        let json = """
        {"type":"cryptowallet","id":"1","title":"T"}
        """
        XCTAssertThrowsError(try PayloadJson.decode(fromString: json))
    }

    func testMissingTypeIsRejected() {
        let json = """
        {"id":"1","title":"T","username":"u"}
        """
        XCTAssertThrowsError(try PayloadJson.decode(fromString: json))
    }

    // MARK: - Derived properties

    func testCategoryMatchesCase() {
        XCTAssertEqual(ItemPayload.login(ItemPayload.Login(id: "1", title: "T")).category, .login)
        XCTAssertEqual(ItemPayload.card(ItemPayload.Card(id: "1", title: "T")).category, .card)
        XCTAssertEqual(ItemPayload.bank(ItemPayload.Bank(id: "1", title: "T")).category, .bank)
        XCTAssertEqual(ItemPayload.note(ItemPayload.SecureNote(id: "1", title: "T")).category, .note)
        XCTAssertEqual(ItemPayload.identity(ItemPayload.Identity(id: "1", title: "T")).category, .identity)
    }

    func testListSubtitleMirrorsAndroid() {
        XCTAssertEqual(
            ItemPayload.login(ItemPayload.Login(id: "1", title: "T", address: "https://x.com")).listSubtitle,
            "https://x.com"
        )
        XCTAssertEqual(
            ItemPayload.card(ItemPayload.Card(id: "1", title: "T", cardholderName: "Ada")).listSubtitle,
            "Ada"
        )
        XCTAssertEqual(
            ItemPayload.bank(ItemPayload.Bank(id: "1", title: "T", bankName: "Ziraat")).listSubtitle,
            "Ziraat"
        )
        let longNote = String(repeating: "x", count: 100)
        XCTAssertEqual(
            ItemPayload.note(ItemPayload.SecureNote(id: "1", title: "T", notes: longNote)).listSubtitle.count,
            60
        )
    }

    /// Android: `email.ifEmpty { "first last".trim() }.ifEmpty { company }.ifEmpty { phone }`
    func testIdentityListSubtitleFallbackChain() {
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(
                id: "1", title: "T", firstName: "Ada", email: "a@b.c"
            )).listSubtitle,
            "a@b.c"
        )
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(
                id: "1", title: "T", firstName: "Ada", lastName: "Lovelace"
            )).listSubtitle,
            "Ada Lovelace"
        )
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(
                id: "1", title: "T", firstName: "Ada"
            )).listSubtitle,
            "Ada"
        )
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(
                id: "1", title: "T", phone: "+1", company: "AE"
            )).listSubtitle,
            "AE"
        )
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(id: "1", title: "T", phone: "+1")).listSubtitle,
            "+1"
        )
        XCTAssertEqual(
            ItemPayload.identity(ItemPayload.Identity(id: "1", title: "T")).listSubtitle,
            ""
        )
    }

    func testLenientCategoryParse() {
        XCTAssertEqual(ItemCategory.lenientParse("login"), .login)
        XCTAssertEqual(ItemCategory.lenientParse("LOGIN"), .login)
        XCTAssertEqual(ItemCategory.lenientParse(" Card "), .card)
        XCTAssertEqual(ItemCategory.lenientParse(""), .login)
        XCTAssertEqual(ItemCategory.lenientParse("nonsense"), .login)
    }

    // MARK: - Helpers

    private func jsonObject(_ data: Data) throws -> [String: Any] {
        let parsed = try JSONSerialization.jsonObject(with: data, options: [])
        guard let object = parsed as? [String: Any] else {
            throw NSError(domain: "ItemPayloadTests", code: 1, userInfo: nil)
        }
        return object
    }
}
