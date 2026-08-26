import XCTest
import PassVaultCore
@testable import PassVaultStorage

final class ImportMergeTests: XCTestCase {

    private let now: Int64 = 1_800_000_000_000

    // MARK: - Planning

    func testUnknownIdIsAnInsertPreservingBothTimestamps() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "new", title: "Fresh"),
            createdAt: 1_000_000,
            updatedAt: 2_000_000
        )
        let plan = ImportMerge.plan(fileItems: [item], existing: [], now: now)

        XCTAssertEqual(plan.insertCount, 1)
        XCTAssertEqual(plan.overwriteCount, 0)
        XCTAssertEqual(plan.inserts.first?.item.createdAt, 1_000_000)
        XCTAssertEqual(plan.inserts.first?.effectiveUpdatedAt, 2_000_000)
    }

    func testNewerFileItemOverwritesTheLocalOne() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "From file"),
            createdAt: 100,
            updatedAt: 500
        )
        let existing = [ExistingItemSnapshot(id: "a", updatedAt: 400, title: "Local title")]
        let plan = ImportMerge.plan(fileItems: [item], existing: existing, now: now)

        XCTAssertEqual(plan.insertCount, 0)
        XCTAssertEqual(plan.overwriteCount, 1)
        XCTAssertEqual(plan.overwrites.first?.existingUpdatedAt, 400)
        XCTAssertEqual(plan.overwrites.first?.effectiveUpdatedAt, 500)
        // The summary shows the LOCAL title, because that is what is being lost.
        XCTAssertEqual(plan.overwrittenTitles, ["Local title"])
    }

    func testOlderFileItemLosesAndIsLeftUntouched() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "Stale"),
            createdAt: 100,
            updatedAt: 300
        )
        let existing = [ExistingItemSnapshot(id: "a", updatedAt: 400, title: "Local")]
        let plan = ImportMerge.plan(fileItems: [item], existing: existing, now: now)

        XCTAssertTrue(plan.isEmpty)
        XCTAssertEqual(plan.skipped.map { $0.reason }, [.localCopyIsNewer])
    }

    /// Equal is not newer. Re-importing the same file must be a no-op rather than
    /// rewriting every row.
    func testEqualTimestampIsNotAnOverwrite() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "Same"),
            createdAt: 100,
            updatedAt: 400
        )
        let existing = [ExistingItemSnapshot(id: "a", updatedAt: 400, title: "Local")]
        let plan = ImportMerge.plan(fileItems: [item], existing: existing, now: now)

        XCTAssertEqual(plan.overwriteCount, 0)
        XCTAssertEqual(plan.skipped.first?.reason, .localCopyIsNewer)
    }

    // MARK: - The future-timestamp clamp

    /// A forged far-future `updatedAt` must be clamped to `now` BEFORE the
    /// comparison, otherwise a crafted file wins every future merge forever.
    func testForgedFutureTimestampIsClampedBeforeComparing() {
        let farFuture: Int64 = now + 10_000_000_000
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "Forged"),
            createdAt: 100,
            updatedAt: farFuture
        )
        // Local copy is newer than `now` would allow the file to claim.
        let existing = [ExistingItemSnapshot(id: "a", updatedAt: now, title: "Local")]
        let plan = ImportMerge.plan(fileItems: [item], existing: existing, now: now)

        XCTAssertEqual(plan.overwriteCount, 0, "a forged timestamp won the comparison")
        XCTAssertEqual(plan.skipped.first?.reason, .localCopyIsNewer)
    }

    /// And the clamped value is what gets STORED, not the forged one — clamping
    /// only for the comparison would hand the file a permanent win next time.
    func testClampedValueIsWhatGetsStoredOnInsert() {
        let farFuture: Int64 = now + 10_000_000_000
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "new", title: "Forged"),
            createdAt: 100,
            updatedAt: farFuture
        )
        let plan = ImportMerge.plan(fileItems: [item], existing: [], now: now)

        XCTAssertEqual(plan.insertCount, 1)
        XCTAssertEqual(plan.inserts.first?.effectiveUpdatedAt, now)
    }

    func testTimestampsInThePastAreNotClamped() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "new", title: "Old but honest"),
            createdAt: 100,
            updatedAt: 200
        )
        let plan = ImportMerge.plan(fileItems: [item], existing: [], now: now)
        XCTAssertEqual(plan.inserts.first?.effectiveUpdatedAt, 200)
    }

    // MARK: - Add-only mode

    func testAddOnlyModeSkipsEveryOverwrite() {
        let items = [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "existing", title: "From file"),
                createdAt: 100, updatedAt: 900),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "new", title: "Fresh"),
                createdAt: 100, updatedAt: 900)
        ]
        let existing = [ExistingItemSnapshot(id: "existing", updatedAt: 400, title: "Local")]

        let merge = ImportMerge.plan(fileItems: items, existing: existing, now: now, mode: .merge)
        XCTAssertEqual(merge.insertCount, 1)
        XCTAssertEqual(merge.overwriteCount, 1)

        let addOnly = ImportMerge.plan(fileItems: items, existing: existing, now: now, mode: .addOnly)
        XCTAssertEqual(addOnly.insertCount, 1)
        XCTAssertEqual(addOnly.overwriteCount, 0)
        XCTAssertEqual(addOnly.skipped.map { $0.reason }, [.addOnlyMode])
        XCTAssertEqual(addOnly.inserts.first?.item.id, "new")
    }

    // MARK: - Never delete

    /// Local items absent from the file must not appear anywhere in the plan.
    /// There is no delete list, and this asserts nothing sneaks in through the
    /// skip list either.
    func testLocalOnlyItemsAreNotTouched() {
        let item = StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "In file"),
            createdAt: 1, updatedAt: 500)
        let existing = [
            ExistingItemSnapshot(id: "a", updatedAt: 100, title: "A"),
            ExistingItemSnapshot(id: "local-only", updatedAt: 100, title: "Untouched")
        ]
        let plan = ImportMerge.plan(fileItems: [item], existing: existing, now: now)

        XCTAssertEqual(plan.overwriteCount, 1)
        XCTAssertFalse(plan.inserts.contains { $0.item.id == "local-only" })
        XCTAssertFalse(plan.overwrites.contains { $0.item.id == "local-only" })
        XCTAssertFalse(plan.skipped.contains { $0.id == "local-only" })
    }

    // MARK: - Hostile input

    /// A file listing the same id twice would otherwise produce two writes to one
    /// primary key. The newer record wins and the plan stays single-valued.
    func testDuplicateIdsInTheFileAreResolved() {
        let items = [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "dup", title: "Older"),
                createdAt: 1, updatedAt: 100),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "dup", title: "Newer"),
                createdAt: 1, updatedAt: 900)
        ]
        let plan = ImportMerge.plan(fileItems: items, existing: [], now: now)

        XCTAssertEqual(plan.insertCount, 1)
        XCTAssertEqual(plan.inserts.first?.effectiveUpdatedAt, 900)
        XCTAssertEqual(plan.inserts.first?.item.payload.title, "Newer")
        XCTAssertEqual(plan.skipped.map { $0.reason }, [.duplicateIdInFile])
    }

    func testEmptyFileProducesAnEmptyPlan() {
        let plan = ImportMerge.plan(fileItems: [], existing: [], now: now)
        XCTAssertTrue(plan.isEmpty)
        XCTAssertEqual(plan.insertCount, 0)
        XCTAssertEqual(plan.overwriteCount, 0)
    }

    // MARK: - Summary

    func testSummaryCountsAndTitles() {
        let items = [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "new1", title: "N1"), createdAt: 1, updatedAt: 900),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "new2", title: "N2"), createdAt: 1, updatedAt: 900),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "old1", title: "O1"), createdAt: 1, updatedAt: 900),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "loser", title: "L"), createdAt: 1, updatedAt: 10)
        ]
        let existing = [
            ExistingItemSnapshot(id: "old1", updatedAt: 100, title: "Local One"),
            ExistingItemSnapshot(id: "loser", updatedAt: 100, title: "Local Loser")
        ]
        let plan = ImportMerge.plan(fileItems: items, existing: existing, now: now)

        XCTAssertEqual(plan.insertCount, 2)
        XCTAssertEqual(plan.overwriteCount, 1)
        XCTAssertEqual(plan.skippedCount, 1)
        XCTAssertEqual(plan.overwrittenTitles, ["Local One"])
        XCTAssertFalse(plan.isEmpty)
    }

    func testPlanFromStoreShapedInputs() {
        let items = [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "a", title: "A"), createdAt: 1, updatedAt: 900)
        ]
        let plan = ImportMerge.plan(
            fileItems: items,
            existingUpdatedAt: ["a": 100],
            titles: ["a": "Local A"],
            now: now
        )
        XCTAssertEqual(plan.overwriteCount, 1)
        XCTAssertEqual(plan.overwrittenTitles, ["Local A"])
    }

    // MARK: - Applying

    func testApplyInsertsAndOverwritesInTheDatabase() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "Local A"),
            createdAt: 50,
            updatedAt: 100
        ))

        let items = [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "a", title: "File A"),
                createdAt: 50, updatedAt: 900),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "b", title: "File B"),
                createdAt: 70, updatedAt: 800)
        ]
        let plan = ImportMerge.plan(
            fileItems: items,
            existingUpdatedAt: try store.updatedAtById(),
            titles: ["a": "Local A"],
            now: now
        )
        let outcome = try store.applyImport(plan, vaultKey: StorageTestSupport.vaultKey, keyVersion: 1)

        XCTAssertEqual(outcome.inserted, 1)
        XCTAssertEqual(outcome.overwritten, 1)
        XCTAssertEqual(try store.count(), 2)

        let overwritten = try XCTUnwrap(try store.item(id: "a"))
        XCTAssertEqual(overwritten.updatedAt, 900)
        // An overwrite must not move created_at.
        XCTAssertEqual(overwritten.createdAt, 50)
        XCTAssertEqual(
            try ItemCrypto.decryptPayload(row: overwritten, vaultKey: StorageTestSupport.vaultKey).title,
            "File A"
        )

        // An insert preserves BOTH timestamps from the file.
        let inserted = try XCTUnwrap(try store.item(id: "b"))
        XCTAssertEqual(inserted.createdAt, 70)
        XCTAssertEqual(inserted.updatedAt, 800)
    }

    func testApplyNeverDeletes() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "keep", title: "Keep me"),
            createdAt: 1, updatedAt: 1))

        let items = [StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "other", title: "Other"),
            createdAt: 1, updatedAt: 2)]
        let plan = ImportMerge.plan(
            fileItems: items,
            existingUpdatedAt: try store.updatedAtById(),
            titles: [:],
            now: now
        )
        try store.applyImport(plan, vaultKey: StorageTestSupport.vaultKey, keyVersion: 1)

        XCTAssertEqual(try store.count(), 2)
        XCTAssertNotNil(try store.item(id: "keep"))
    }

    func testApplyingAnAddOnlyPlanLeavesExistingRowsAlone() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "a", title: "Local A"),
            createdAt: 50, updatedAt: 100))
        let before = try XCTUnwrap(try store.item(id: "a"))

        let items = [StorageTestSupport.fileItem(
            payload: StorageTestSupport.login(id: "a", title: "File A"),
            createdAt: 50, updatedAt: 900)]
        let plan = ImportMerge.plan(
            fileItems: items,
            existingUpdatedAt: try store.updatedAtById(),
            titles: ["a": "Local A"],
            now: now,
            mode: .addOnly
        )
        let outcome = try store.applyImport(plan, vaultKey: StorageTestSupport.vaultKey, keyVersion: 1)

        XCTAssertEqual(outcome.inserted, 0)
        XCTAssertEqual(outcome.overwritten, 0)
        let after = try XCTUnwrap(try store.item(id: "a"))
        XCTAssertEqual(after.encryptedData, before.encryptedData)
        XCTAssertEqual(after.updatedAt, 100)
    }

    /// End to end: write a `.pmvault`, read it back, merge it into a store that
    /// already holds one of its items.
    func testFullPmVaultRoundTripIntoTheStore() throws {
        let store = try VaultStore.inMemory()
        try store.insert(try StorageTestSupport.row(
            payload: StorageTestSupport.login(id: "shared", title: "Local version"),
            createdAt: 10, updatedAt: 100))

        let body = PmVaultBody(version: 1, exportedAt: 500, items: [
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.login(id: "shared", title: "File version"),
                createdAt: 10, updatedAt: 400),
            StorageTestSupport.fileItem(
                payload: StorageTestSupport.note(id: "brand-new", title: "New note", notes: "n"),
                createdAt: 20, updatedAt: 450)
        ])
        let cheap = KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)
        let file = try PmVaultFile.write(body: body, passphrase: "export-pw", params: cheap)
        let readBack = try PmVaultFile.read(file, passphrase: "export-pw")

        let plan = ImportMerge.plan(
            fileItems: readBack.items,
            existingUpdatedAt: try store.updatedAtById(),
            titles: ["shared": "Local version"],
            now: now
        )
        XCTAssertEqual(plan.insertCount, 1)
        XCTAssertEqual(plan.overwriteCount, 1)
        XCTAssertEqual(plan.overwrittenTitles, ["Local version"])

        try store.applyImport(plan, vaultKey: StorageTestSupport.vaultKey, keyVersion: 1)
        XCTAssertEqual(try store.count(), 2)

        let shared = try XCTUnwrap(try store.item(id: "shared"))
        XCTAssertEqual(
            try ItemCrypto.decryptPayload(row: shared, vaultKey: StorageTestSupport.vaultKey).title,
            "File version"
        )
        let fresh = try XCTUnwrap(try store.item(id: "brand-new"))
        XCTAssertEqual(fresh.category, "note")
        XCTAssertEqual(fresh.createdAt, 20)
        XCTAssertEqual(fresh.updatedAt, 450)
    }
}
