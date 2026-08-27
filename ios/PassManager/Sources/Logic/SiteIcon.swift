import Foundation
import PassVaultCore

/// The exact shape of the one network request this app ever makes.
///
/// `docs/IOS_PARITY.md` states the contract and both platforms make the same
/// promise; the Android half is `ui/components/FaviconImage.kt` plus the
/// `ImageLoader` in `PassManagerApp.kt`. Everything that decides WHAT leaves the
/// device lives in this file, with no UIKit and no networking in it, so the
/// promise can be tested rather than asserted — see `SiteIconTests`.
///
/// Four rules, and they are the whole feature:
///
/// 1. Logins only. The address envelope is shared by all five categories and only
///    holds a SITE for one of them. ``domain(for:address:)`` asks the category
///    first and the string second.
/// 2. One host, ever. ``host`` is Google's icon CDN and nothing else is
///    contacted — not the site itself, which is where the user's credentials
///    point.
/// 3. Only a domain leaves. Never a path, never a query, never anything else off
///    the item. ``domain(for:address:)`` is the only door, and it is narrow.
/// 4. Redirects are refused. ``RedirectPolicy`` is what turns "one host" from a
///    statement about the request we made into one about the request that ran.
enum SiteIcon {

    /// A host that has already been through the gate below, and the only thing
    /// the loader will accept.
    ///
    /// The initializer is `fileprivate`, so this file is the only place in the
    /// app that can mint one and ``domain(for:address:)`` is the only function in
    /// this file that does. That is what makes the category check structural
    /// rather than advisory: a call site cannot hand the loader a string it
    /// assembled itself, or one it took off an item of some other category,
    /// because a `String` is not what the loader is asking for.
    struct Domain: Hashable, Sendable {
        let value: String

        fileprivate init(_ value: String) {
            self.value = value
        }
    }

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

    /// The domain to ask about, or `nil` when nothing may be asked.
    ///
    /// THE CATEGORY DECIDES, NOT THE STRING. Every item carries an address
    /// envelope and only a login's holds a site: an identity's is an email
    /// address, a note's is the first characters of the body, a card's is the
    /// cardholder's name, a bank's is the bank's name. Those parse as readily as
    /// a URL does — `ayse@example.com` yields `example.com` with the userinfo
    /// stripped and no whitespace to object to — so a lookup that reads only the
    /// string discloses the user's mail provider the moment the setting is
    /// switched on. Enabling site icons is consent to look up the domains of your
    /// LOGINS.
    ///
    /// The alternative — a cleverer filter over free text — was considered and
    /// refused. Deciding whether a string "looks like" a URL is a guess, and a
    /// guess is the wrong instrument for deciding what leaves a vault. The
    /// category is a fact the item already carries.
    ///
    /// The switch is exhaustive with no `default` on purpose: a sixth category
    /// stops this file compiling until someone states, here, whether its envelope
    /// holds a site.
    static func domain(for category: ItemCategory, address: String) -> Domain? {
        switch category {
        case .login:
            return parse(address).map(Domain.init)
        case .card, .bank, .note, .identity:
            return nil
        }
    }

    /// The host inside an address, or `nil` when there is not one.
    ///
    /// PRIVATE, and that is the point: the gate above is the only way to reach
    /// it, so no call site can parse an envelope without first saying which
    /// category it came off.
    ///
    /// Mirrors Android's `extractDomain` case for case, including the one that
    /// looks like an oversight and is not: a string with NO DOT is not a URL and
    /// is never guessed at. With the category gate in front, these guards are
    /// defence in depth rather than the thing carrying the weight — they now
    /// catch a login whose address is prose, which is a mistake rather than a
    /// disclosure.
    ///
    /// `www.` is stripped so `www.github.com` and `github.com` are one domain and
    /// one request, exactly as on Android.
    private static func parse(_ address: String) -> String? {
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
        // leans on that to keep prose out of this function — "ACME Yazılım A.Ş."
        // has a dot in it and is not an address. Foundation's parsers have grown
        // more forgiving over releases and a lenient one would hand back a "host"
        // made of that prose, or of its percent-escaped form. Neither is a
        // hostname, and neither is sent. Typed into a LOGIN'S address field, which
        // is now the only way prose gets this far.
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
    ///
    /// Takes a ``Domain`` rather than a `String`, so the only URL this app can
    /// build is one for a host that came off a login.
    ///
    /// `size=256` because the CDN returns what the site PUBLISHES, capped by the
    /// request — it does not synthesise resolution. Measured 2026-08-27 and
    /// written into `docs/IOS_PARITY.md`: `stackoverflow.com` and
    /// `garantibbva.com.tr` hand back 180×180 at 256 where 128 gets exactly
    /// 128×128, while `github.com` (32×32) and `netflix.com` (64×64) return
    /// BYTE-IDENTICAL responses at every size. So the larger request gains real
    /// resolution where there is any to gain and costs nothing where there is
    /// not. Re-measure before changing it; this is not a number to reason about.
    static func iconURL(for domain: Domain) -> URL? {
        guard let encoded = ("https://" + domain.value)
            .addingPercentEncoding(withAllowedCharacters: formUnreserved)
        else {
            return nil
        }
        return URL(string:
            "https://\(host)/faviconV2"
                + "?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL"
                + "&url=\(encoded)&size=256"
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
