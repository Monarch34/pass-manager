import XCTest
import Foundation
import UniformTypeIdentifiers
import PassVaultCore
@testable import PassManager

final class VaultTransferTests: XCTestCase {

    // MARK: - The lock-mid-transfer trap

    /// The scenario that bit Android: the Files picker backgrounds the app, the
    /// auto-lock timer fires, and the picker returns into a locked vault. The
    /// flow must rewind to "needs unlock" rather than being silently dropped.
    func testLockRewindsAnExportToNeedingUnlock() {
        let interrupted: [TransferStage] = [
            .awaitingExportPassphrase,
            .exporting
        ]
        for stage in interrupted {
            XCTAssertEqual(
                TransferLockPolicy.stageAfterLock(stage),
                .awaitingUnlockForExport,
                "\(stage)"
            )
        }
    }

    func testLockRewindsAnImportToNeedingUnlock() {
        let interrupted: [TransferStage] = [
            .awaitingImportPassphrase,
            .reviewingImport
        ]
        for stage in interrupted {
            XCTAssertEqual(
                TransferLockPolicy.stageAfterLock(stage),
                .awaitingUnlockForImport,
                "\(stage)"
            )
        }
    }

    func testLockLeavesAnIdleSessionIdle() {
        XCTAssertEqual(TransferLockPolicy.stageAfterLock(.idle), .idle)
    }

    /// Unlocking resumes exactly where the lock interrupted, so the user does not
    /// have to find the file in Files a second time.
    func testUnlockResumesTheInterruptedFlow() {
        XCTAssertEqual(
            TransferLockPolicy.stageAfterUnlock(.awaitingUnlockForExport),
            .awaitingExportPassphrase
        )
        XCTAssertEqual(
            TransferLockPolicy.stageAfterUnlock(.awaitingUnlockForImport),
            .awaitingImportPassphrase
        )
    }

    /// An unlock must not fast-forward a flow that was not waiting on one.
    func testUnlockDoesNotAdvanceOtherStages() {
        for stage in [TransferStage.idle, .exporting, .reviewingImport, .awaitingExportPassphrase] {
            XCTAssertEqual(TransferLockPolicy.stageAfterUnlock(stage), stage, "\(stage)")
        }
    }

    /// A lock-then-unlock round trip lands on the passphrase step, NEVER back on
    /// a stage that would reuse a passphrase collected before the lock.
    func testRoundTripAlwaysReturnsToThePassphraseStep() {
        for stage in [TransferStage.awaitingExportPassphrase, .exporting] {
            let resumed = TransferLockPolicy.stageAfterUnlock(
                TransferLockPolicy.stageAfterLock(stage)
            )
            XCTAssertEqual(resumed, .awaitingExportPassphrase, "\(stage)")
        }
        for stage in [TransferStage.awaitingImportPassphrase, .reviewingImport] {
            let resumed = TransferLockPolicy.stageAfterUnlock(
                TransferLockPolicy.stageAfterLock(stage)
            )
            XCTAssertEqual(resumed, .awaitingImportPassphrase, "\(stage)")
        }
    }

    func testStageClassification() {
        XCTAssertTrue(TransferStage.exporting.isExport)
        XCTAssertTrue(TransferStage.awaitingUnlockForExport.isExport)
        XCTAssertFalse(TransferStage.exporting.isImport)
        XCTAssertTrue(TransferStage.reviewingImport.isImport)
        XCTAssertTrue(TransferStage.awaitingUnlockForImport.isImport)
        XCTAssertFalse(TransferStage.idle.isExport)
        XCTAssertFalse(TransferStage.idle.isImport)
    }

    func testLockAlwaysDiscardsPlaintext() {
        XCTAssertTrue(TransferLockPolicy.discardsPlaintextOnLock())
    }

    // MARK: - Export passphrase floor

    func testExportPassphraseMustBeLongEnough() {
        XCTAssertEqual(
            ExportPassphrasePolicy.rejection(for: "Ab1!", confirmation: "Ab1!", masterPassphrase: nil),
            .tooShort
        )
    }

    /// The floor is `good`, matching Android — an eight-character all-lowercase
    /// passphrase scores `weak` and must be refused.
    func testExportPassphraseMustReachTheStrengthFloor() {
        XCTAssertEqual(ExportPassphrasePolicy.minimumStrength, .good)

        let weak = "abcdefgh"
        XCTAssertEqual(PasswordStrength.evaluate(weak), .weak)
        XCTAssertEqual(
            ExportPassphrasePolicy.rejection(for: weak, confirmation: weak, masterPassphrase: nil),
            .tooWeak
        )

        let fair = "abcdefgH"
        XCTAssertEqual(PasswordStrength.evaluate(fair), .fair)
        XCTAssertEqual(
            ExportPassphrasePolicy.rejection(for: fair, confirmation: fair, masterPassphrase: nil),
            .tooWeak
        )

        let good = "abcdefG1"
        XCTAssertEqual(PasswordStrength.evaluate(good), .good)
        XCTAssertNil(
            ExportPassphrasePolicy.rejection(for: good, confirmation: good, masterPassphrase: nil)
        )
    }

    func testExportPassphraseMustNotReuseTheMaster() {
        let shared = "abcdeF1!master"
        XCTAssertEqual(
            ExportPassphrasePolicy.rejection(
                for: shared,
                confirmation: shared,
                masterPassphrase: shared
            ),
            .matchesMaster
        )
        // A different one is fine even when a master is supplied for comparison.
        XCTAssertNil(
            ExportPassphrasePolicy.rejection(
                for: "abcdeF1!export",
                confirmation: "abcdeF1!export",
                masterPassphrase: shared
            )
        )
    }

    func testExportPassphraseMustBeConfirmed() {
        XCTAssertEqual(
            ExportPassphrasePolicy.rejection(
                for: "abcdeF1!",
                confirmation: "abcdeF1?",
                masterPassphrase: nil
            ),
            .confirmationMismatch
        )
    }

    func testEveryRejectionExplainsItself() {
        for rejection in [
            ExportPassphrasePolicy.Rejection.tooShort,
            .tooWeak,
            .matchesMaster,
            .confirmationMismatch
        ] {
            XCTAssertFalse(rejection.message.isEmpty, "\(rejection)")
        }
    }

    // MARK: - Backup reminder

    private let day: Int64 = 86_400_000
    private let now: Int64 = 1_800_000_000_000

    func testEmptyVaultNeedsNoBackup() {
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: nil, now: now, itemCount: 0),
            .upToDate
        )
    }

    func testItemsWithNoExportEverAreFlagged() {
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: nil, now: now, itemCount: 3),
            .neverExported
        )
        // A zero timestamp is "never", not "1970".
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: 0, now: now, itemCount: 3),
            .neverExported
        )
    }

    func testRecentBackupIsUpToDate() {
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: now - day, now: now, itemCount: 3),
            .upToDate
        )
    }

    func testStaleBackupReportsItsAge() {
        let age = Int64(BackupReminder.intervalDays)
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: now - age * day, now: now, itemCount: 3),
            .stale(days: BackupReminder.intervalDays)
        )
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: now - 100 * day, now: now, itemCount: 3),
            .stale(days: 100)
        )
    }

    /// Just inside the interval is not yet stale.
    func testBoundaryIsExclusiveBelowTheInterval() {
        let justInside = now - (Int64(BackupReminder.intervalDays) * day - 1)
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: justInside, now: now, itemCount: 3),
            .upToDate
        )
    }

    /// A clock that jumped backwards is not evidence of a stale backup.
    func testFutureTimestampDoesNotNagTheUser() {
        XCTAssertEqual(
            BackupReminder.status(lastExportAt: now + 10 * day, now: now, itemCount: 3),
            .upToDate
        )
    }

    func testOnlyStaleAndNeverProduceAMessage() {
        XCTAssertNil(BackupReminder.Status.upToDate.message)
        XCTAssertNotNil(BackupReminder.Status.neverExported.message)
        XCTAssertNotNil(BackupReminder.Status.stale(days: 45).message)
        // Singular reads correctly.
        XCTAssertEqual(BackupReminder.Status.stale(days: 1).message?.contains("1 day"), true)
        XCTAssertEqual(BackupReminder.Status.stale(days: 2).message?.contains("2 days"), true)
    }

    /// The key matches Android's so both platforms name the same thing the same
    /// way, even though the reminder interval itself is unverified against it.
    func testDefaultsKeyMatchesAndroid() {
        XCTAssertEqual(BackupReminder.defaultsKey, "last_export_at_ms")
    }

    // MARK: - Document

    func testDocumentRoundTripsItsBytes() throws {
        let bytes = Data([0x50, 0x4D, 0x56, 0x54, 0x00, 0x01, 0x02])
        let document = PmVaultDocument(data: bytes)
        XCTAssertEqual(document.data, bytes)
        XCTAssertEqual(PmVaultDocument.readableContentTypes, PmVaultDocument.writableContentTypes)
    }

    func testExportedTypeIdentifier() {
        XCTAssertEqual(UTType.pmvault.identifier, "com.passmanager.pmvault")
        XCTAssertTrue(UTType.pmvault.conforms(to: .data))
    }

    /// Checks the declaration this app OWNS is actually in the shipped
    /// Info.plist. Without it the Files picker greys out every `.pmvault` the app
    /// itself wrote, which is the sort of thing that only shows up on a device.
    /// `Bundle.main` is the host app here, not the test bundle.
    func testExportedTypeIsDeclaredInTheAppInfoPlist() throws {
        let raw = Bundle.main.object(forInfoDictionaryKey: "UTExportedTypeDeclarations")
        let declarations = try XCTUnwrap(
            raw as? [[String: Any]],
            "UTExportedTypeDeclarations missing from the app Info.plist"
        )
        let vault = try XCTUnwrap(
            declarations.first { ($0["UTTypeIdentifier"] as? String) == "com.passmanager.pmvault" },
            "no declaration for com.passmanager.pmvault"
        )
        XCTAssertEqual(vault["UTTypeConformsTo"] as? [String], ["public.data"])

        let tags = try XCTUnwrap(vault["UTTypeTagSpecification"] as? [String: Any])
        XCTAssertEqual(tags["public.filename-extension"] as? [String], ["pmvault"])
    }
}
