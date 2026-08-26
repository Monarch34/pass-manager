import Foundation
import PassVaultCore

/// How much of a `.pmvault` file an import is allowed to apply.
public enum ImportMode: String, Sendable, CaseIterable {
    /// Insert new items and overwrite older local ones.
    case merge
    /// Insert new items only; never touch anything that already exists.
    /// `docs/FORMAT.md` requires this to be offered alongside the summary.
    case addOnly
}

/// What one item in the file already looks like locally.
///
/// `title` is the DECRYPTED local title, supplied by the caller from the header
/// cache. Planning does no crypto and no I/O of its own — that is what makes it
/// testable, and what lets the UI show a confirmation before anything is written.
public struct ExistingItemSnapshot: Equatable, Sendable {
    public var id: String
    public var updatedAt: Int64
    public var title: String

    public init(id: String, updatedAt: Int64, title: String) {
        self.id = id
        self.updatedAt = updatedAt
        self.title = title
    }
}

/// Why a file item is not being applied.
public enum ImportSkipReason: String, Sendable {
    /// The local copy is newer than (or the same age as) the file's.
    case localCopyIsNewer
    /// Add-only mode: the item exists locally and would have been overwritten.
    case addOnlyMode
    /// The file listed this id more than once; a different record won.
    case duplicateIdInFile
}

public struct ImportSkip: Equatable, Sendable {
    public var id: String
    public var reason: ImportSkipReason

    public init(id: String, reason: ImportSkipReason) {
        self.id = id
        self.reason = reason
    }
}

/// A fully decided import, ready to show to the user and then apply verbatim.
public struct ImportPlan: Equatable, Sendable {

    public struct Insert: Equatable, Sendable {
        public var item: PmVaultItem
        /// `min(file value, now)` — see ``ImportMerge``.
        public var effectiveUpdatedAt: Int64

        public init(item: PmVaultItem, effectiveUpdatedAt: Int64) {
            self.item = item
            self.effectiveUpdatedAt = effectiveUpdatedAt
        }
    }

    public struct Overwrite: Equatable, Sendable {
        public var item: PmVaultItem
        public var effectiveUpdatedAt: Int64
        public var existingUpdatedAt: Int64
        /// The local title about to be replaced — this is what the confirmation
        /// screen lists, so the user can see exactly what they are losing.
        public var existingTitle: String

        public init(
            item: PmVaultItem,
            effectiveUpdatedAt: Int64,
            existingUpdatedAt: Int64,
            existingTitle: String
        ) {
            self.item = item
            self.effectiveUpdatedAt = effectiveUpdatedAt
            self.existingUpdatedAt = existingUpdatedAt
            self.existingTitle = existingTitle
        }
    }

    public var mode: ImportMode
    public var inserts: [Insert]
    public var overwrites: [Overwrite]
    public var skipped: [ImportSkip]

    public init(
        mode: ImportMode,
        inserts: [Insert],
        overwrites: [Overwrite],
        skipped: [ImportSkip]
    ) {
        self.mode = mode
        self.inserts = inserts
        self.overwrites = overwrites
        self.skipped = skipped
    }

    // MARK: - The summary shown before anything is written

    public var insertCount: Int {
        return inserts.count
    }

    public var overwriteCount: Int {
        return overwrites.count
    }

    public var skippedCount: Int {
        return skipped.count
    }

    /// Titles of the local items that would be replaced.
    public var overwrittenTitles: [String] {
        return overwrites.map { $0.existingTitle }
    }

    /// True when applying this plan would change nothing.
    public var isEmpty: Bool {
        return inserts.isEmpty && overwrites.isEmpty
    }
}

public struct ImportOutcome: Equatable, Sendable {
    public var inserted: Int
    public var overwritten: Int
    public var skipped: Int

    public init(inserted: Int, overwritten: Int, skipped: Int) {
        self.inserted = inserted
        self.overwritten = overwritten
        self.skipped = skipped
    }
}

/// Import merge semantics from `docs/FORMAT.md`.
///
/// The rules, in the order they matter:
///
/// - Match by `id`. An unknown id is an insert that PRESERVES `createdAt` and
///   `updatedAt` from the file rather than stamping "now".
/// - A known id is decided by `updatedAt`: the newer wins, the loser is left
///   completely untouched.
/// - `updatedAt` is clamped to `min(file value, now)` on read, so a file carrying
///   a forged far-future timestamp cannot permanently shadow local edits. The
///   clamped value is what gets compared AND what gets stored — clamping only for
///   the comparison and then writing the forged value back would defeat the whole
///   point on the next import.
/// - Nothing is ever deleted.
///
/// Planning and applying are separate steps so the UI can show a summary and get
/// confirmation before a single row changes.
public enum ImportMerge {

    /// Decide what an import would do, without doing any of it.
    ///
    /// - Parameters:
    ///   - fileItems: items straight out of a decrypted `PmVaultBody`.
    ///   - existing: local snapshots; only ids present here can be overwrites.
    ///   - now: current epoch milliseconds, used for the future-timestamp clamp.
    public static func plan(
        fileItems: [PmVaultItem],
        existing: [ExistingItemSnapshot],
        now: Int64,
        mode: ImportMode = .merge
    ) -> ImportPlan {
        var existingById: [String: ExistingItemSnapshot] = [:]
        for snapshot in existing {
            existingById[snapshot.id] = snapshot
        }

        // A hostile (or merely buggy) file can list the same id twice. Resolve
        // that first, so the plan can never contain two writes to one primary key.
        let deduplicated = deduplicate(fileItems: fileItems, now: now)

        var inserts: [ImportPlan.Insert] = []
        var overwrites: [ImportPlan.Overwrite] = []
        var skipped: [ImportSkip] = deduplicated.skipped

        for item in deduplicated.items {
            let effectiveUpdatedAt = min(item.updatedAt, now)

            guard let local = existingById[item.id] else {
                inserts.append(ImportPlan.Insert(item: item, effectiveUpdatedAt: effectiveUpdatedAt))
                continue
            }

            if mode == .addOnly {
                skipped.append(ImportSkip(id: item.id, reason: .addOnlyMode))
                continue
            }

            // Strictly newer wins. An equal timestamp is NOT newer, so a
            // re-import of the same file is a no-op rather than a churn of
            // rewrites.
            if effectiveUpdatedAt > local.updatedAt {
                overwrites.append(ImportPlan.Overwrite(
                    item: item,
                    effectiveUpdatedAt: effectiveUpdatedAt,
                    existingUpdatedAt: local.updatedAt,
                    existingTitle: local.title
                ))
            } else {
                skipped.append(ImportSkip(id: item.id, reason: .localCopyIsNewer))
            }
        }

        return ImportPlan(mode: mode, inserts: inserts, overwrites: overwrites, skipped: skipped)
    }

    /// Convenience overload taking the store's `id -> updatedAt` map plus a title
    /// lookup (typically the decrypted header cache).
    public static func plan(
        fileItems: [PmVaultItem],
        existingUpdatedAt: [String: Int64],
        titles: [String: String],
        now: Int64,
        mode: ImportMode = .merge
    ) -> ImportPlan {
        var snapshots: [ExistingItemSnapshot] = []
        snapshots.reserveCapacity(existingUpdatedAt.count)
        for (id, updatedAt) in existingUpdatedAt {
            snapshots.append(ExistingItemSnapshot(
                id: id,
                updatedAt: updatedAt,
                title: titles[id] ?? ""
            ))
        }
        return plan(fileItems: fileItems, existing: snapshots, now: now, mode: mode)
    }

    // MARK: - Duplicate ids inside one file

    private struct Deduplicated {
        var items: [PmVaultItem]
        var skipped: [ImportSkip]
    }

    private static func deduplicate(fileItems: [PmVaultItem], now: Int64) -> Deduplicated {
        var winnerByID: [String: PmVaultItem] = [:]
        var order: [String] = []
        var skipped: [ImportSkip] = []

        for item in fileItems {
            guard let incumbent = winnerByID[item.id] else {
                winnerByID[item.id] = item
                order.append(item.id)
                continue
            }
            // Same tie-break as the merge itself: the newer clamped timestamp
            // wins, and a tie leaves the incumbent in place.
            if min(item.updatedAt, now) > min(incumbent.updatedAt, now) {
                winnerByID[item.id] = item
            }
            skipped.append(ImportSkip(id: item.id, reason: .duplicateIdInFile))
        }

        var items: [PmVaultItem] = []
        items.reserveCapacity(order.count)
        for id in order {
            if let item = winnerByID[id] {
                items.append(item)
            }
        }
        return Deduplicated(items: items, skipped: skipped)
    }
}
