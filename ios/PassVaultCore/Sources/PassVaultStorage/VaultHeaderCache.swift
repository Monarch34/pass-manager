import Foundation
import PassVaultCore

/// In-memory cache of decrypted vault list headers.
///
/// Mirrors Android's `VaultListDecryptionManager`:
///
/// - keyed by `id`, validated against `updatedAt`, so only rows that actually
///   changed are decrypted again;
/// - entries for deleted rows are pruned on every pass;
/// - ``clear()`` on lock, because this holds plaintext titles and addresses and
///   locking must mean the plaintext is gone.
///
/// Not thread-safe by itself, exactly like the Android manager: it is owned by one
/// view model and mutated from one place.
public final class VaultHeaderCache {

    private var titlesByID: [String: String] = [:]
    private var addressesByID: [String: String] = [:]
    /// `updatedAt` as of the last successful decrypt for that id.
    private var decryptedAtByID: [String: Int64] = [:]

    public init() {}

    public var titles: [String: String] {
        return titlesByID
    }

    public var addresses: [String: String] {
        return addressesByID
    }

    public var count: Int {
        return titlesByID.count
    }

    public func title(for id: String) -> String {
        return titlesByID[id] ?? ""
    }

    public func address(for id: String) -> String {
        return addressesByID[id] ?? ""
    }

    /// Which of `headers` need decrypting: never seen, or changed since we last
    /// looked.
    public func staleHeaders(in headers: [VaultItemHeaderRow]) -> [VaultItemHeaderRow] {
        return headers.filter { header in
            if titlesByID[header.id] == nil {
                return true
            }
            return decryptedAtByID[header.id] != header.updatedAt
        }
    }

    public func store(id: String, updatedAt: Int64, header: DecryptedHeader) {
        titlesByID[id] = header.title
        addressesByID[id] = header.address
        decryptedAtByID[id] = updatedAt
    }

    /// Drop everything not in `ids` — i.e. rows deleted since the last pass.
    public func prune(keeping ids: Set<String>) {
        // Rebuilt via `filter` rather than removing while iterating `keys`, which
        // reads as a mutation-during-iteration bug even where it happens to work.
        titlesByID = titlesByID.filter { ids.contains($0.key) }
        addressesByID = addressesByID.filter { ids.contains($0.key) }
        decryptedAtByID = decryptedAtByID.filter { ids.contains($0.key) }
    }

    /// Called when the vault locks. Locking means zeroing the key; it must also
    /// mean losing every decrypted string derived from it.
    public func clear() {
        titlesByID.removeAll()
        addressesByID.removeAll()
        decryptedAtByID.removeAll()
    }

    /// One incremental refresh pass: prune, find the stale rows, decrypt only
    /// those, store the results.
    ///
    /// A row that fails to decrypt is skipped and reported rather than aborting
    /// the pass, so one damaged item cannot blank the whole list.
    @discardableResult
    public func refresh(
        headers: [VaultItemHeaderRow],
        vaultKey: Data
    ) -> VaultHeaderCacheRefreshResult {
        var currentIDs = Set<String>()
        for header in headers {
            currentIDs.insert(header.id)
        }
        prune(keeping: currentIDs)

        let stale = staleHeaders(in: headers)
        var decrypted = 0
        var failedIDs: [String] = []

        for header in stale {
            do {
                if let value = try ItemCrypto.decryptHeader(row: header, vaultKey: vaultKey) {
                    store(id: header.id, updatedAt: header.updatedAt, header: value)
                    decrypted += 1
                } else {
                    failedIDs.append(header.id)
                }
            } catch {
                failedIDs.append(header.id)
            }
        }

        return VaultHeaderCacheRefreshResult(decrypted: decrypted, failedIDs: failedIDs)
    }
}

public struct VaultHeaderCacheRefreshResult: Equatable, Sendable {
    public var decrypted: Int
    public var failedIDs: [String]

    public init(decrypted: Int, failedIDs: [String]) {
        self.decrypted = decrypted
        self.failedIDs = failedIDs
    }

    /// Whether the caller should show the partial-decrypt warning.
    public var hadFailure: Bool {
        return !failedIDs.isEmpty
    }
}
