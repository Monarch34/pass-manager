import Foundation

/// Where the vault lives on disk, and how it is replaced.
///
/// The single most common way a password manager loses everything is a torn write, so the
/// file is never modified in place: the new vault is written whole to a temporary file and
/// then moved over the old one. A crash therefore leaves either the previous vault or the
/// new one, and never half of either.
enum VaultStore {

    enum StoreError: LocalizedError {
        case cannotWrite(String)
        var errorDescription: String? {
            switch self {
            case .cannotWrite(let detail): return "The vault could not be saved: \(detail)"
            }
        }
    }

    /// Application Support rather than Documents: the vault is the application's own state,
    /// not a document the user files away, and Documents is visible over iTunes sharing.
    static var url: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base.appendingPathComponent("vault.pmvault")
    }

    static var exists: Bool { FileManager.default.fileExists(atPath: url.path) }

    static func read() throws -> Data { try Data(contentsOf: url) }

    /// Writes the vault, then moves it into place.
    ///
    /// The protection class is set **at creation**, on the temporary file, because a move
    /// carries the source's class onto the destination. Set it afterwards and the first
    /// save silently and permanently downgrades the vault.
    ///
    /// `completeUnlessOpen` rather than the strict class: `complete` makes an already-open
    /// file handle fail the moment the device locks, which would break a save in progress.
    static func write(_ data: Data) throws {
        let temporary = url.deletingLastPathComponent()
            .appendingPathComponent("vault.pmvault.writing")

        try? FileManager.default.removeItem(at: temporary)
        let created = FileManager.default.createFile(
            atPath: temporary.path,
            contents: nil,
            attributes: [.protectionKey: FileProtectionType.completeUnlessOpen]
        )
        guard created else { throw StoreError.cannotWrite("could not create a temporary file") }

        let handle = try FileHandle(forWritingTo: temporary)
        do {
            try handle.write(contentsOf: data)
            // Ask the device to actually flush, not merely to accept the bytes.
            try handle.synchronize()
            try handle.close()
        } catch {
            try? handle.close()
            try? FileManager.default.removeItem(at: temporary)
            throw StoreError.cannotWrite(error.localizedDescription)
        }

        _ = try FileManager.default.replaceItemAt(url, withItemAt: temporary)
    }

    /// Removes the vault entirely. Used by "start over" when a passphrase is forgotten,
    /// which is the only honest option: nothing here can recover a vault without its key.
    static func destroy() {
        try? FileManager.default.removeItem(at: url)
    }
}
