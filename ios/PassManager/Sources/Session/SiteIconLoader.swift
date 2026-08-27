import Foundation
import UIKit

/// Fetches site icons, caches them in memory, and remembers what it could not
/// find.
///
/// NO THIRD-PARTY IMAGE LIBRARY. Kingfisher or SDWebImage would each be a
/// dependency added to a password manager — new supply-chain surface, and a
/// `project.yml` change — to do what `URLSession` and `UIImage` already do for a
/// 128px square. The parts a library would give us that actually matter here are
/// forty lines: a memory cache, request coalescing, and a decode off the main
/// thread. The part it would take away is control of the redirect policy, which
/// is the whole privacy contract.
///
/// One instance, ``shared``, because the cache and the miss set are session-wide
/// facts and not per-row ones. Android's equivalents are process-wide statics for
/// the same reason. Deliberately NOT an `ObservableObject`: nothing here drives a
/// redraw — a row asks for its own icon and holds it — so publishing this state
/// would invalidate every visible row every time any one of them resolved.
@MainActor
final class SiteIconLoader {

    static let shared = SiteIconLoader()

    /// Domains whose lookup already failed while the setting was on.
    ///
    /// Bounded and cleared WHOLESALE rather than evicted one at a time, exactly
    /// as on Android: losing the whole set costs at most one extra request per
    /// domain, while an LRU would put per-access bookkeeping in the list's
    /// scrolling path to save nothing anyone can measure.
    static let maxRememberedMisses = 512

    /// Keyed by the domain's string rather than by ``SiteIcon/Domain`` itself:
    /// `NSCache` below needs an `NSString`, and one key shape across all three
    /// stores is one thing to keep straight instead of two.
    private var misses: Set<String> = []
    /// One in-flight fetch per domain, so five logins at the same site ask once.
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    /// Bumped by every invalidation — a lock, or the setting being switched.
    ///
    /// Without it, a fetch already in flight when the vault locks would come back
    /// a moment later and quietly re-populate a cache that had just been emptied
    /// on purpose. A result that belongs to a previous generation is dropped.
    private var generation: UInt64 = 0

    /// `NSCache` rather than a dictionary: it evicts under memory pressure on its
    /// own, which decoded bitmaps held for the lifetime of a process otherwise
    /// would not.
    private let images: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 256
        return cache
    }()

    /// The redirect policy is installed on the SESSION, so it governs every task
    /// this loader will ever create rather than the ones a call site remembered
    /// to pass it to.
    ///
    /// `.ephemeral` with the caches explicitly emptied out is the same decision
    /// Android's `diskCache(null)` records: the vault encrypts every title and
    /// address, so the set of sites the user holds credentials for must not then
    /// be readable in a cache directory — as images, or as request URLs in a
    /// cache index. Icons live in memory, for as long as the vault is unlocked.
    ///
    /// Cookies are refused in both directions. A favicon request has no business
    /// carrying identity, and OkHttp — which the Android side uses — has no
    /// cookie jar at all unless you give it one, so this is parity rather than
    /// embellishment.
    private let session: URLSession
    private let redirectPolicy = SiteIcon.RedirectPolicy()

    private init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        // Fail fast rather than holding a row's fetch open: a network that is not
        // going to answer should land the row on its category tile promptly.
        configuration.timeoutIntervalForRequest = 10
        configuration.waitsForConnectivity = false
        session = URLSession(
            configuration: configuration,
            delegate: redirectPolicy,
            delegateQueue: nil
        )
    }

    // MARK: - Lookup

    /// The icon for a domain, or `nil` if there is not one to be had.
    ///
    /// TAKES A ``SiteIcon/Domain``, WHICH THIS TYPE CANNOT MAKE. The only mint is
    /// `SiteIcon.domain(for:address:)`, which refuses every category but `.login`,
    /// so "we only look up logins" is enforced by what this function will accept
    /// rather than by every caller remembering to check. A row holding an
    /// identity's email has nothing to pass here.
    ///
    /// Callers must also have decided that the setting is ON before getting here:
    /// this type does not read the setting, it is simply never asked when the
    /// setting is off. That is deliberate — "no request of any kind when the
    /// setting is off" is easiest to verify when the code that could make one is
    /// not reachable.
    func image(for domain: SiteIcon.Domain) async -> UIImage? {
        let key = domain.value
        if let cached = images.object(forKey: key as NSString) {
            return cached
        }
        if misses.contains(key) {
            return nil
        }
        if let running = inFlight[key] {
            return await running.value
        }

        let session = self.session
        let generation = self.generation
        // Unstructured on purpose: the caller is a row's `.task`, which is
        // cancelled the moment the row scrolls out of view. Letting that cancel
        // the fetch would mean a list in motion re-asking about the same domains
        // forever. The fetch finishes, the result is cached, and the next row to
        // want it pays nothing.
        let task = Task { await Self.fetch(domain: domain, session: session) }
        inFlight[key] = task
        let image = await task.value
        inFlight[key] = nil

        guard generation == self.generation else {
            // The vault locked, or the setting moved, while this was in the air.
            // Neither the image nor the miss may outlive that.
            return nil
        }
        if let image = image {
            images.setObject(image, forKey: key as NSString)
        } else {
            recordMiss(key)
        }
        return image
    }

    private func recordMiss(_ domain: String) {
        if misses.count >= Self.maxRememberedMisses {
            misses.removeAll(keepingCapacity: true)
        }
        misses.insert(domain)
    }

    // MARK: - The network half

    /// `nonisolated` so the request and the decode run off the main actor.
    ///
    /// Everything that can fail is treated the same way — a non-200, a redirect
    /// refused by the policy above, a body that is not an image, a transport
    /// error — because the caller has exactly one fallback and it is correct for
    /// all of them.
    private nonisolated static func fetch(domain: SiteIcon.Domain, session: URLSession) async -> UIImage? {
        guard let url = SiteIcon.iconURL(for: domain) else {
            return nil
        }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch {
            // Offline, DNS failure, timeout, cancellation — all one outcome.
            return nil
        }
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            return nil
        }
        // Belt and braces to the redirect policy: with 3xx refused this cannot be
        // any other host, and if it ever is, the bytes are dropped rather than
        // shown.
        guard http.url?.host == SiteIcon.host else {
            return nil
        }
        guard let image = UIImage(data: data) else {
            return nil
        }
        // Decode now, on this thread, rather than the first time a row draws it.
        return image.preparingForDisplay() ?? image
    }

    // MARK: - Lifecycle

    /// Called when the Settings toggle moves, in EITHER direction.
    ///
    /// Turning it back on retries the domains that failed the last time it was
    /// on, instead of inheriting a stale session of misses. Turning it off drops
    /// the icons already fetched, because a user who has just switched this off
    /// should not keep looking at the results of it — Android clears Coil's
    /// memory cache at the same moment and for the same reason.
    func settingChanged(enabled: Bool) {
        invalidate(dropImages: !enabled)
    }

    /// Called when the vault locks.
    ///
    /// The keys of this cache are the domains of sites the user holds credentials
    /// for — derived from the address envelope, and no less revealing than the
    /// decrypted titles `VaultHeaderCache` throws away at exactly this moment.
    /// Locking means the plaintext is gone, so it means this too. The cost is one
    /// re-fetch per domain after an unlock, and only while the setting is on.
    func clear() {
        invalidate(dropImages: true)
    }

    private func invalidate(dropImages: Bool) {
        generation &+= 1
        for task in inFlight.values {
            task.cancel()
        }
        inFlight.removeAll()
        misses.removeAll(keepingCapacity: false)
        if dropImages {
            images.removeAllObjects()
        }
    }
}
