import Foundation
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// The `.pmvault` container.
    ///
    /// Declared as an EXPORTED type — this app defines the format, it does not
    /// merely consume someone else's. The matching `UTExportedTypeDeclarations`
    /// entry lives in `project.yml`; without it the system would not associate
    /// the extension with the app and the Files picker would grey the files out.
    static var pmvault: UTType {
        return UTType(exportedAs: "com.passmanager.pmvault", conformingTo: .data)
    }
}

/// The document handed to `.fileExporter`.
///
/// It carries already-encrypted bytes. Building the container is the vault's job
/// and happens before this type ever exists, so nothing here can leak plaintext
/// into a `FileWrapper`.
struct PmVaultDocument: FileDocument {

    static var readableContentTypes: [UTType] {
        return [.pmvault]
    }

    static var writableContentTypes: [UTType] {
        return [.pmvault]
    }

    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let contents = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = contents
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        return FileWrapper(regularFileWithContents: data)
    }
}

/// Where a transfer has got to.
///
/// Modelled explicitly because the interesting behaviour is what happens when the
/// vault locks HALFWAY THROUGH — see ``TransferLockPolicy``.
enum TransferStage: Equatable {
    case idle
    /// The user asked to export while locked, or locked mid-flow.
    case awaitingUnlockForExport
    /// Unlocked; asking for the export passphrase.
    case awaitingExportPassphrase
    /// The document is built and the system picker is up.
    case exporting
    /// A file has been read but the vault is locked.
    case awaitingUnlockForImport
    /// Unlocked; asking for the file's passphrase.
    case awaitingImportPassphrase
    /// Decrypted and planned; the summary is on screen awaiting confirmation.
    case reviewingImport

    var isExport: Bool {
        switch self {
        case .awaitingUnlockForExport, .awaitingExportPassphrase, .exporting:
            return true
        default:
            return false
        }
    }

    var isImport: Bool {
        switch self {
        case .awaitingUnlockForImport, .awaitingImportPassphrase, .reviewingImport:
            return true
        default:
            return false
        }
    }
}

/// What a lock and a subsequent unlock do to a transfer in progress.
///
/// THE TRAP THIS EXISTS FOR: the Files picker can send the app to the background,
/// the auto-lock timer can fire while it is up, and the picker then returns into
/// a LOCKED vault. Android hit this for real. Dropping the user back at the lock
/// screen with their selection silently discarded is the bad outcome; carrying
/// vault plaintext across the lock to avoid it is the worse one.
///
/// So: the flow survives, the secrets do not. A lock rewinds the stage to the
/// "needs unlock" step and the caller discards every plaintext artefact (the
/// built export document, the decrypted body, the plan). What survives is the
/// user's INTENT, plus — for an import — the file bytes, which are ciphertext and
/// no more sensitive than the file sitting in Files.
enum TransferLockPolicy {

    static func stageAfterLock(_ stage: TransferStage) -> TransferStage {
        switch stage {
        case .idle:
            return .idle
        case .awaitingUnlockForExport, .awaitingExportPassphrase, .exporting:
            return .awaitingUnlockForExport
        case .awaitingUnlockForImport, .awaitingImportPassphrase, .reviewingImport:
            return .awaitingUnlockForImport
        }
    }

    static func stageAfterUnlock(_ stage: TransferStage) -> TransferStage {
        switch stage {
        case .awaitingUnlockForExport:
            // The passphrase is asked AFTER the unlock, never before the picker:
            // one collected earlier would have been typed into a screen the user
            // had already been locked out of.
            return .awaitingExportPassphrase
        case .awaitingUnlockForImport:
            return .awaitingImportPassphrase
        default:
            return stage
        }
    }

    /// Whether plaintext derived from the vault must be discarded on this
    /// transition. Always true — stated as a function so the call site reads as a
    /// decision rather than an omission.
    static func discardsPlaintextOnLock() -> Bool {
        return true
    }
}

/// The export passphrase floor.
///
/// A `.pmvault` is a complete copy of the vault protected by this passphrase
/// alone, so it gets a real floor rather than the 8-character minimum the master
/// passphrase uses.
enum ExportPassphrasePolicy {

    /// Same threshold as Android: at least `good`.
    static let minimumStrength = PasswordStrength.good
    static let minimumLength = 8

    enum Rejection: Equatable {
        case tooShort
        case tooWeak
        case matchesMaster
        case confirmationMismatch

        var message: String {
            switch self {
            case .tooShort:
                return "At least \(ExportPassphrasePolicy.minimumLength) characters"
            case .tooWeak:
                return "Too weak for an export — add length, cases, digits or symbols"
            case .matchesMaster:
                return "Do not reuse your master passphrase for an export"
            case .confirmationMismatch:
                return "Passphrases do not match"
            }
        }
    }

    /// `nil` when the passphrase is acceptable.
    ///
    /// `masterPassphrase` is compared only when the caller has it in hand from
    /// the same screen; it is never stored to make this check possible.
    static func rejection(
        for passphrase: String,
        confirmation: String,
        masterPassphrase: String?
    ) -> Rejection? {
        if passphrase.count < minimumLength {
            return .tooShort
        }
        if PasswordStrength.evaluate(passphrase).rawValue < minimumStrength.rawValue {
            return .tooWeak
        }
        if let master = masterPassphrase, !master.isEmpty, master == passphrase {
            return .matchesMaster
        }
        if passphrase != confirmation {
            return .confirmationMismatch
        }
        return nil
    }
}

/// "You last backed up N days ago."
///
/// PARITY GAP, stated plainly: Android's export code is not present in this
/// repository, so the reminder interval below is a choice, not a mirror. The key
/// name matches Android's `last_export_at_ms` so the two at least agree on what
/// they are storing. The threshold needs confirming against Android before
/// release.
enum BackupReminder {

    static let defaultsKey = "last_export_at_ms"
    static let intervalDays = 30

    enum Status: Equatable {
        /// Nothing to back up, or backed up recently enough.
        case upToDate
        /// Items exist and none has ever been exported.
        case neverExported
        /// Last export was this many days ago.
        case stale(days: Int)

        var message: String? {
            switch self {
            case .upToDate:
                return nil
            case .neverExported:
                return "You have never exported this vault. An encrypted backup is the only way to recover it if this device is lost."
            case .stale(let days):
                return "Last backup was \(days) day\(days == 1 ? "" : "s") ago."
            }
        }
    }

    static func status(lastExportAt: Int64?, now: Int64, itemCount: Int) -> Status {
        if itemCount == 0 {
            return .upToDate
        }
        guard let lastExportAt = lastExportAt, lastExportAt > 0 else {
            return .neverExported
        }
        let elapsed = now - lastExportAt
        if elapsed < 0 {
            // A clock that moved backwards is not evidence of a stale backup.
            return .upToDate
        }
        let days = Int(elapsed / 86_400_000)
        if days >= intervalDays {
            return .stale(days: days)
        }
        return .upToDate
    }
}
