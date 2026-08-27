import XCTest
@testable import PassManager

/// The privacy contract for site icons, written down as tests.
///
/// These are cheap and they are the point: every one of them fails loudly if the
/// one network feature in this app starts sending somewhere else, sending more
/// than a domain, or quietly following a redirect. `docs/IOS_PARITY.md` states
/// the contract; this is what stops it rotting.
final class SiteIconTests: XCTestCase {

    // MARK: - What leaves the device

    func testDomainStripsSchemeAndPathAndQuery() {
        XCTAssertEqual(
            SiteIcon.domain(from: "https://github.com/login?next=%2Fsettings"),
            "github.com",
            "Only the host may be taken out of an address — never the path or the query."
        )
    }

    func testDomainStripsWww() {
        XCTAssertEqual(SiteIcon.domain(from: "https://www.github.com"), "github.com")
        XCTAssertEqual(SiteIcon.domain(from: "www.github.com"), "github.com")
    }

    /// Only a LEADING `www.` goes. `www.co.uk` inside a longer host is part of it.
    func testDomainStripsOnlyTheLeadingWww() {
        XCTAssertEqual(SiteIcon.domain(from: "https://mail.www.example.com"), "mail.www.example.com")
    }

    func testBareDomainIsAccepted() {
        XCTAssertEqual(SiteIcon.domain(from: "example.com"), "example.com")
    }

    func testHttpSchemeIsAccepted() {
        XCTAssertEqual(SiteIcon.domain(from: "http://example.com/x"), "example.com")
    }

    /// The case that matters most, because the address envelope is SHARED: a
    /// card's cardholder and a bank's name arrive here too, and neither is an
    /// address. No dot means not a URL, and nothing is guessed at.
    func testStringWithNoDotIsNotADomain() {
        XCTAssertNil(SiteIcon.domain(from: "Ayşe Yılmaz"))
        XCTAssertNil(SiteIcon.domain(from: "Kadıköy Bankası"))
        XCTAssertNil(SiteIcon.domain(from: "localhost"))
    }

    func testUnparseableInputIsNotADomain() {
        XCTAssertNil(SiteIcon.domain(from: ""))
        XCTAssertNil(SiteIcon.domain(from: "   "))
        XCTAssertNil(SiteIcon.domain(from: "https://"))
    }

    /// Prose that happens to contain a full stop is not an address. The address
    /// envelope is shared, so an identity's company lands here — and "ACME
    /// Yazılım A.Ş." would otherwise clear the dot test.
    func testProseWithADotIsNotADomain() {
        XCTAssertNil(SiteIcon.domain(from: "ACME Yazılım A.Ş."))
        XCTAssertNil(SiteIcon.domain(from: "not a url, but it has a dot."))
        XCTAssertNil(SiteIcon.domain(from: "https://not a host either.com"))
    }

    func testSurroundingWhitespaceIsIgnored() {
        XCTAssertEqual(SiteIcon.domain(from: "  https://github.com  "), "github.com")
    }

    // MARK: - The request itself

    /// Compared against the literal string, deliberately. This is the line a
    /// network observer sees, so a test that rebuilt it from the same parts would
    /// be testing nothing.
    func testIconURLIsExactlyTheAgreedShape() {
        XCTAssertEqual(
            SiteIcon.iconURL(for: "github.com")?.absoluteString,
            "https://t0.gstatic.com/faviconV2"
                + "?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL"
                + "&url=https%3A%2F%2Fgithub.com&size=128"
        )
    }

    func testIconURLTargetsOnlyTheOneHost() {
        XCTAssertEqual(SiteIcon.host, "t0.gstatic.com")
        for domain in ["github.com", "example.co.uk", "xn--nda.com"] {
            XCTAssertEqual(
                SiteIcon.iconURL(for: domain)?.host,
                SiteIcon.host,
                "\(domain) must be asked about at the CDN, never at the site itself."
            )
        }
    }

    /// The `url` parameter is fully escaped — `:` and `/` included — which is
    /// what `URLComponents` would NOT do and what Android's `URLEncoder` does.
    func testIconURLEscapesTheDomainArgument() {
        let absolute = SiteIcon.iconURL(for: "example.com")?.absoluteString ?? ""
        XCTAssertTrue(absolute.contains("&url=https%3A%2F%2Fexample.com&"), absolute)
        XCTAssertFalse(absolute.contains("url=https://"), absolute)
    }

    /// `fallback_opts` keeps LITERAL commas; the endpoint does not answer to an
    /// escaped list.
    func testIconURLKeepsFallbackOptionsUnescaped() {
        let absolute = SiteIcon.iconURL(for: "example.com")?.absoluteString ?? ""
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
