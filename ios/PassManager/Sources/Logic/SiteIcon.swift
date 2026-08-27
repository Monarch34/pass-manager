import Foundation

/// The exact shape of the one network request this app ever makes.
///
/// `docs/IOS_PARITY.md` states the contract and both platforms make the same
/// promise; the Android half is `ui/components/FaviconImage.kt` plus the
/// `ImageLoader` in `PassManagerApp.kt`. Everything that decides WHAT leaves the
/// device lives in this file, with no UIKit and no networking in it, so the
/// promise can be tested rather than asserted — see `SiteIconTests`.
///
/// Three rules, and they are the whole feature:
///
/// 1. One host, ever. ``host`` is Google's icon CDN and nothing else is
///    contacted — not the site itself, which is where the user's credentials
///    point.
/// 2. Only a domain leaves. Never a path, never a query, never anything else off
///    the item. ``domain(from:)`` is the only door, and it is narrow.
/// 3. Redirects are refused. ``RedirectPolicy`` is what turns "one host" from a
///    statement about the request we made into one about the request that ran.
enum SiteIcon {

    /// The only host this app contacts, kept next to the code that enforces it.
    ///
    /// Deliberately the CDN and NOT `www.google.com/s2/favicons`: that address
    /// answers 301 and sends the client here anyway, so a loader that refuses
    /// redirects could never use it, and one that follows them has handed the
    /// user's vault domains to whatever host the redirect happened to name. This
    /// endpoint answers 200 directly, which is what makes refusal affordable.
    ///
    /// If Google ever retires this shape the response stops being a 200 and every
    /// entry quietly falls back to its category tile — the safe direction for a
    /// vault.
    static let host = "t0.gstatic.com"

    /// The domain to ask about, or `nil` when there is nothing askable.
    ///
    /// Mirrors Android's `extractDomain` case for case, including the one that
    /// looks like an oversight and is not: a string with NO DOT is not a URL and
    /// is never guessed at. That case is what stops a card's cardholder name or
    /// an identity's company — both of which share the address envelope with a
    /// login's URL — from being sent anywhere.
    ///
    /// `www.` is stripped so `www.github.com` and `github.com` are one domain and
    /// one request, exactly as on Android.
    static func domain(from address: String) -> String? {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized: String
        if trimmed.hasPrefix("https://") || trimmed.hasPrefix("http://") {
            normalized = trimmed
        } else if trimmed.contains(".") {
            normalized = "https://" + trimmed
        } else {
            return nil
        }
        // `URLComponents` rather than `URL`: it parses per RFC 3986 and returns
        // nil for an authority it cannot make sense of, which is the behaviour
        // Java's `URI` gives Android. `URL(string:)` on iOS 17+ is far more
        // forgiving and would start inventing hosts out of things that are not
        // addresses.
        guard let host = URLComponents(string: normalized)?.host, !host.isEmpty else {
            return nil
        }
        // Java's `URI` REJECTS a string with a space in it outright, and Android
        // leans on that to keep prose out of this function — an identity's
        // company, "ACME Yazılım A.Ş.", has a dot in it and is not an address.
        // Foundation's parsers have grown more forgiving over releases and a
        // lenient one would hand back a "host" made of that prose, or of its
        // percent-escaped form. Neither is a hostname, and neither is sent.
        guard !host.contains("%"),
              host.rangeOfCharacter(from: .whitespacesAndNewlines) == nil
        else {
            return nil
        }
        let stripped = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
        return stripped.isEmpty ? nil : stripped
    }

    /// The icon request for a domain, built by hand rather than by
    /// `URLComponents`.
    ///
    /// The string is exact and the test compares against it literally, because
    /// this is the line that decides what a network observer sees. Two details
    /// are load-bearing and neither survives round-tripping through
    /// `URLComponents`: `fallback_opts` carries LITERAL commas, and the `url`
    /// value is FULLY escaped — `URLComponents` would leave `:` and `/` in the
    /// clear, producing a different request from the one Android makes.
    static func iconURL(for domain: String) -> URL? {
        guard let encoded = ("https://" + domain)
            .addingPercentEncoding(withAllowedCharacters: formUnreserved)
        else {
            return nil
        }
        return URL(string:
            "https://\(host)/faviconV2"
                + "?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL"
                + "&url=\(encoded)&size=128"
        )
    }

    /// The characters Java's `URLEncoder` leaves alone, written out rather than
    /// derived from `CharacterSet.alphanumerics` — that set is all of Unicode's
    /// letters and digits, so an internationalised domain would travel
    /// unescaped and produce a URL Foundation then refuses to build. Spelling
    /// the ASCII set out means a non-ASCII domain is percent-encoded as UTF-8,
    /// which is what Android does.
    private static let formUnreserved: CharacterSet = {
        var set = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz")
        set.formUnion(CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        set.formUnion(CharacterSet(charactersIn: "0123456789"))
        set.formUnion(CharacterSet(charactersIn: "-_.*"))
        return set
    }()

    /// Refuses every redirect, for every task on the session that installs it.
    ///
    /// This is the enforceable half of "no other host is contacted". Foundation
    /// follows 3xx silently by default, so without this the app's own promise
    /// would describe the request it made and say nothing at all about the one
    /// that ran. Passing `nil` to the completion handler tells `URLSession` to
    /// stop and hand the redirect response back as the result, which fails to
    /// decode as an image and lands the row on its category tile — the same
    /// place every other failure lands.
    ///
    /// Installed on the SESSION rather than per task, so no future call site can
    /// forget it.
    final class RedirectPolicy: NSObject, URLSessionTaskDelegate {
        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            willPerformHTTPRedirection response: HTTPURLResponse,
            newRequest request: URLRequest,
            completionHandler: @escaping (URLRequest?) -> Void
        ) {
            completionHandler(nil)
        }
    }
}
