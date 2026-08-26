import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class VaultSearchTests: XCTestCase {

    // MARK: - Folding

    func testFoldingLowercasesAscii() {
        XCTAssertEqual(VaultSearch.foldForSearch("GitHub"), "github")
        XCTAssertEqual(VaultSearch.foldForSearch("HELLO World"), "hello world")
    }

    /// Android does NOT fold diacritics — `ş` is a different letter from `s`.
    /// Swift's `.diacriticInsensitive` would erase that distinction, so the two
    /// apps would disagree about what a search finds.
    func testFoldingDoesNotStripDiacritics() {
        XCTAssertEqual(VaultSearch.foldForSearch("Şirket"), "şirket")
        XCTAssertNotEqual(VaultSearch.foldForSearch("Şirket"), "sirket")
        XCTAssertEqual(VaultSearch.foldForSearch("Ağrı"), "ağri")
        XCTAssertNotEqual(VaultSearch.foldForSearch("Ağrı"), "agri")
        XCTAssertEqual(VaultSearch.foldForSearch("Öğün"), "öğün")
    }

    /// Java's simple case mapping collapses `i`, `I`, `ı` and `İ`; Swift's
    /// `lowercased()` alone does not.
    func testTurkishDottedAndDotlessICollapseLikeJava() {
        XCTAssertEqual(VaultSearch.foldForSearch("i"), "i")
        XCTAssertEqual(VaultSearch.foldForSearch("I"), "i")
        XCTAssertEqual(VaultSearch.foldForSearch("ı"), "i")
        XCTAssertEqual(VaultSearch.foldForSearch("İ"), "i")
        XCTAssertEqual(VaultSearch.foldForSearch("İstanbul"), "istanbul")
        XCTAssertEqual(VaultSearch.foldForSearch("IŞIK"), "işik")
    }

    /// The concrete regression this protects against: a plain `lowercased()`
    /// expands `İ` to `i` + U+0307, so "iş" would not find "İş Bankası".
    func testFoldedCapitalDottedIHasNoCombiningMark() {
        let folded = VaultSearch.foldForSearch("İ")
        XCTAssertEqual(folded.unicodeScalars.count, 1)
        XCTAssertEqual(folded.unicodeScalars.first?.value, 0x0069)
    }

    // MARK: - Filtering

    func testSearchIsCaseInsensitive() throws {
        let (headers, cache) = try makeFixture([
            ("a", "GitHub", "https://github.com", .login),
            ("b", "Gmail", "https://mail.google.com", .login)
        ])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "github", cache: cache)), ["a"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "GITHUB", cache: cache)), ["a"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "GiThUb", cache: cache)), ["a"])
    }

    func testSearchMatchesTheAddress() throws {
        let (headers, cache) = try makeFixture([
            ("a", "GitHub", "https://github.com", .login),
            ("b", "Gmail", "https://mail.google.com", .login)
        ])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "google", cache: cache)), ["b"])
    }

    func testSearchMatchesTheCategoryLabelAndKey() throws {
        let (headers, cache) = try makeFixture([
            ("a", "GitHub", "", .login),
            ("b", "Visa", "", .card)
        ])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "Card", cache: cache)), ["b"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "card", cache: cache)), ["b"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "login", cache: cache)), ["a"])
    }

    func testTurkishTitleIsFoundByLowercaseQuery() throws {
        let (headers, cache) = try makeFixture([
            ("a", "İş Bankası", "", .bank),
            ("b", "Şirket Notu", "", .note),
            ("c", "Ağrı Dağı", "", .note)
        ])
        // İ -> i, so "iş" finds "İş Bankası" exactly as it does on Android.
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "iş", cache: cache)), ["a"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "İŞ", cache: cache)), ["a"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "bankası", cache: cache)), ["a"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "şirket", cache: cache)), ["b"])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "ŞİRKET", cache: cache)), ["b"])
    }

    /// The negative half of Turkish parity: no diacritic folding means an
    /// ASCII-ified query does NOT match, on both platforms.
    func testAsciiQueryDoesNotMatchTurkishDiacritics() throws {
        let (headers, cache) = try makeFixture([
            ("a", "Şirket Notu", "", .note),
            ("b", "Ağrı Dağı", "", .note)
        ])
        XCTAssertTrue(VaultSearch.filter(headers: headers, query: "sirket", cache: cache).isEmpty)
        XCTAssertTrue(VaultSearch.filter(headers: headers, query: "agri", cache: cache).isEmpty)
    }

    func testEmptyQueryReturnsEverything() throws {
        let (headers, cache) = try makeFixture([
            ("a", "GitHub", "", .login),
            ("b", "Visa", "", .card)
        ])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "", cache: cache)).count, 2)
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "   ", cache: cache)).count, 2)
    }

    func testCategoryFilterApplies() throws {
        let (headers, cache) = try makeFixture([
            ("a", "GitHub", "", .login),
            ("b", "Visa", "", .card)
        ])
        XCTAssertEqual(
            ids(VaultSearch.filter(headers: headers, query: "", cache: cache, category: .card)),
            ["b"]
        )
        XCTAssertTrue(
            VaultSearch.filter(headers: headers, query: "github", cache: cache, category: .card).isEmpty
        )
    }

    func testQueryIsTrimmed() throws {
        let (headers, cache) = try makeFixture([("a", "GitHub", "", .login)])
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "  github  ", cache: cache)), ["a"])
    }

    /// An item whose header has not been decrypted yet has no title in the cache,
    /// so it can only be found by category — never by a stale or guessed title.
    func testUndecryptedItemIsNotMatchedByTitle() throws {
        let (headers, _) = try makeFixture([("a", "GitHub", "", .login)])
        let emptyCache = VaultHeaderCache()
        XCTAssertTrue(VaultSearch.filter(headers: headers, query: "github", cache: emptyCache).isEmpty)
        XCTAssertEqual(ids(VaultSearch.filter(headers: headers, query: "login", cache: emptyCache)), ["a"])
    }

    // MARK: - Helpers

    private func ids(_ headers: [VaultItemHeaderRow]) -> [String] {
        return headers.map { $0.id }
    }

    private func makeFixture(
        _ specs: [(String, String, String, ItemCategory)]
    ) throws -> ([VaultItemHeaderRow], VaultHeaderCache) {
        var headers: [VaultItemHeaderRow] = []
        let cache = VaultHeaderCache()
        var updatedAt: Int64 = 0
        for spec in specs {
            updatedAt += 1
            headers.append(VaultItemHeaderRow(
                id: spec.0,
                encryptedTitle: Data([0]),
                titleIv: Data(repeating: 0, count: 12),
                encryptedAddress: nil,
                addressIv: nil,
                category: spec.3,
                updatedAt: updatedAt
            ))
            cache.store(
                id: spec.0,
                updatedAt: updatedAt,
                header: DecryptedHeader(title: spec.1, address: spec.2)
            )
        }
        return (headers, cache)
    }
}
