import XCTest
import PassVaultCore
@testable import PassManager

/// The privacy contract for site icons, written down as tests.
///
/// These are cheap and they are the point: every one of them fails loudly if the
/// one network feature in this app starts looking up something other than a
/// login, sending somewhere else, sending more than a domain, or quietly
/// following a redirect. `docs/IOS_PARITY.md` states the contract; this is what
/// stops it rotting.
final class SiteIconTests: XCTestCase {

    /// Every login lookup in this file goes through the real gate, because the
    /// gate is the only door — the parser behind it is private and there is
    /// nothing else to test.
    private func login(_ address: String) -> String? {
        return SiteIcon.domain(for: .login, address: address)?.value
    }

    // MARK: - Which items are looked up at all

    /// THE REGRESSION THIS SUITE EXISTS FOR.
    ///
    /// An identity's address envelope holds an EMAIL, and an email parses to a
    /// domain as cleanly as any URL: userinfo stripped, a dot present, no
    /// whitespace and no percent sign to object to. A lookup that read the string
    /// instead of the category sent `example.com` to Google the moment the user
    /// switched site icons on, disclosing their mail provider — which is not what
    /// enabling the setting consents to.
    func testIdentityEmailIsNeverLookedUp() {
        XCTAssertNil(
            SiteIcon.domain(for: .identity, address: "ayse@example.com"),
            "An identity's envelope is an email address. Enabling site icons is not "
                + "consent to disclose the user's mail provider."
        )
        XCTAssertNil(SiteIcon.domain(for: .identity, address: "ACME Yazılım A.Ş."))
        XCTAssertNil(SiteIcon.domain(for: .identity, address: "https://example.com"))
    }

    /// A note's envelope is the first 60 characters of its BODY. A note whose
    /// first line is a single token — `recovery.codes`, a filename, a hostname
    /// someone wrote down — parses to exactly that token.
    func testNoteBodyIsNeverLookedUp() {
        XCTAssertNil(
            SiteIcon.domain(for: .note, address: "recovery.codes"),
            "A note's envelope is the opening of its body. None of it may leave the device."
        )
        XCTAssertNil(SiteIcon.domain(for: .note, address: "vpn.internal.example.com"))
    }

    /// A card's envelope is the cardholder's name. Most names have no dot in them
    /// and the parser would refuse those anyway; the ones with an initial in them
    /// do, which is why this cannot rest on the parser.
    func testCardholderNameIsNeverLookedUp() {
        XCTAssertNil(
            SiteIcon.domain(for: .card, address: "Ayşe Yılmaz"),
            "A card's envelope is the cardholder's name, not a site."
        )
        XCTAssertNil(SiteIcon.domain(for: .card, address: "A.Yilmaz"))
    }

    /// A bank's envelope is the bank's name — a real-world institution the user
    /// holds an account with, and nobody else's business.
    func testBankNameIsNeverLookedUp() {
        XCTAssertNil(
            SiteIcon.domain(for: .bank, address: "Kadıköy Bankası"),
            "A bank's envelope is the institution's name, not a site."
        )
        XCTAssertNil(SiteIcon.domain(for: .bank, address: "ziraatbank.com.tr"))
    }

    /// Stated as a whole rather than one case at a time: whatever the string, four
    /// of the five categories produce nothing. This is the assertion that would
    /// have caught the original defect no matter which envelope leaked first.
    func testOnlyLoginsAreEverLookedUp() {
        let addresses = [
            "https://github.com",
            "github.com",
            "ayse@example.com",
            "recovery.codes"
        ]
        for category in ItemCategory.allCases where category != .login {
            for address in addresses {
                XCTAssertNil(
                    SiteIcon.domain(for: category, address: address),
                    "\(category.rawValue) must never be looked up, and \"\(address)\" is "
                        + "no exception — the category decides, not the string."
                )
            }
        }
        XCTAssertEqual(login("https://github.com"), "github.com")
    }

    // MARK: - What leaves the device

    func testDomainStripsSchemeAndPathAndQuery() {
        XCTAssertEqual(
            login("https://github.com/login?next=%2Fsettings"),
            "github.com",
            "Only the host may be taken out of an address — never the path or the query."
        )
    }

    func testDomainStripsWww() {
        XCTAssertEqual(login("https://www.github.com"), "github.com")
        XCTAssertEqual(login("www.github.com"), "github.com")
    }

    /// Only a LEADING `www.` goes. `www.co.uk` inside a longer host is part of it.
    func testDomainStripsOnlyTheLeadingWww() {
        XCTAssertEqual(login("https://mail.www.example.com"), "mail.www.example.com")
    }

    func testBareDomainIsAccepted() {
        XCTAssertEqual(login("example.com"), "example.com")
    }

    func testHttpSchemeIsAccepted() {
        XCTAssertEqual(login("http://example.com/x"), "example.com")
    }

    /// Defence in depth now rather than the load-bearing guard: a login whose
    /// address field holds a name is a typo, and a typo is not sent either.
    func testStringWithNoDotIsNotADomain() {
        XCTAssertNil(login("Ayşe Yılmaz"))
        XCTAssertNil(login("Kadıköy Bankası"))
        XCTAssertNil(login("localhost"))
    }

    func testUnparseableInputIsNotADomain() {
        XCTAssertNil(login(""))
        XCTAssertNil(login("   "))
        XCTAssertNil(login("https://"))
    }

    /// Prose that happens to contain a full stop is not an address, even in a
    /// login's own address field.
    func testProseWithADotIsNotADomain() {
        XCTAssertNil(login("ACME Yazılım A.Ş."))
        XCTAssertNil(login("not a url, but it has a dot."))
        XCTAssertNil(login("https://not a host either.com"))
    }

    func testSurroundingWhitespaceIsIgnored() {
        XCTAssertEqual(login("  https://github.com  "), "github.com")
    }

    // MARK: - The request itself

    /// Compared against the literal string, deliberately. This is the line a
    /// network observer sees, so a test that rebuilt it from the same parts would
    /// be testing nothing.
    func testIconURLIsExactlyTheAgreedShape() throws {
        let domain = try XCTUnwrap(SiteIcon.domain(for: .login, address: "github.com"))
        XCTAssertEqual(
            SiteIcon.iconURL(for: domain)?.absoluteString,
            "https://t0.gstatic.com/faviconV2"
                + "?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL"
                + "&url=https%3A%2F%2Fgithub.com&size=128"
        )
    }

    func testIconURLTargetsOnlyTheOneHost() throws {
        XCTAssertEqual(SiteIcon.host, "t0.gstatic.com")
        for address in ["github.com", "example.co.uk", "xn--nda.com"] {
            let domain = try XCTUnwrap(SiteIcon.domain(for: .login, address: address))
            XCTAssertEqual(
                SiteIcon.iconURL(for: domain)?.host,
                SiteIcon.host,
                "\(address) must be asked about at the CDN, never at the site itself."
            )
        }
    }

    /// The `url` parameter is fully escaped — `:` and `/` included — which is
    /// what `URLComponents` would NOT do and what Android's `URLEncoder` does.
    func testIconURLEscapesTheDomainArgument() throws {
        let domain = try XCTUnwrap(SiteIcon.domain(for: .login, address: "example.com"))
        let absolute = SiteIcon.iconURL(for: domain)?.absoluteString ?? ""
        XCTAssertTrue(absolute.contains("&url=https%3A%2F%2Fexample.com&"), absolute)
        XCTAssertFalse(absolute.contains("url=https://"), absolute)
    }

    /// `fallback_opts` keeps LITERAL commas; the endpoint does not answer to an
    /// escaped list.
    func testIconURLKeepsFallbackOptionsUnescaped() throws {
        let domain = try XCTUnwrap(SiteIcon.domain(for: .login, address: "example.com"))
        let absolute = SiteIcon.iconURL(for: domain)?.absoluteString ?? ""
        XCTAssertTrue(absolute.contains("fallback_opts=TYPE,SIZE,URL"), absolute)
    }

    // MARK: - Redirects

    /// The delegate answers `nil`, which is what makes "one host" a statement
    /// about the request that RAN rather than the one that was made.
    func testRedirectPolicyRefusesToFollow() {
        let policy = SiteIcon.RedirectPolicy()
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }

        let original = URL(string: "https://t0.gstatic.com/faviconV2?url=x")!
        let task = session.dataTask(with: original)
        defer { task.cancel() }

        let response = HTTPURLResponse(
            url: original,
            statusCode: 301,
            httpVersion: "HTTP/1.1",
            headerFields: ["Location": "https://icons.example.com/steal?domain=github.com"]
        )!
        let redirected = URLRequest(url: URL(string: "https://icons.example.com/steal")!)

        let answered = expectation(description: "the redirect delegate answers")
        var followed: URLRequest? = redirected
        policy.urlSession(
            session,
            task: task,
            willPerformHTTPRedirection: response,
            newRequest: redirected
        ) { request in
            followed = request
            answered.fulfill()
        }
        wait(for: [answered], timeout: 5)

        XCTAssertNil(followed, "A redirect must be refused, not followed to a second host.")
    }

    // MARK: - The setting

    /// Off unless the user says otherwise, mirroring Android's
    /// `AppSettingsDefaults.USE_GOOGLE_FAVICONS`. Read straight from a defaults
    /// domain with nothing written to it, which is the state of a fresh install.
    @MainActor
    func testSiteIconsDefaultToOff() {
        let defaults = UserDefaults.standard
        let saved = defaults.object(forKey: AppSession.siteIconsKey)
        defaults.removeObject(forKey: AppSession.siteIconsKey)
        defer {
            if let saved = saved {
                defaults.set(saved, forKey: AppSession.siteIconsKey)
            }
        }

        XCTAssertFalse(
            AppSession().useSiteIcons,
            "An install that has never been near Settings must make no requests."
        )
    }
}
