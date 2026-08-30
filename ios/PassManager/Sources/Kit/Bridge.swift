import Foundation
import PassManagerKit

/// The seam between Swift and the Kotlin core.
///
/// Everything that knows how a `KotlinByteArray` differs from `Data` lives here. What a
/// vault *is* — opening it, searching it, saving an item, attaching a file — is not here at
/// all: that is `core:vault`, shared with Android, and this app calls it rather than
/// reimplementing it.
extension Data {
    /// Copies into a Kotlin array in one `memcpy`.
    ///
    /// This used to walk the bytes one at a time, each crossing the Objective-C bridge as a
    /// message send, defended on the grounds that it happened "on unlock, on save and on
    /// attach — not in a loop". Export and import are that loop: they walk every attachment
    /// in a vault, and at five megabytes each the element-at-a-time version was millions of
    /// calls per file. `Bytes` pins the Kotlin array and copies whole.
    ///
    /// No cast either way: Kotlin declares these as `NSData`, and Swift bridges `NSData` to
    /// `Data` automatically in an imported Objective-C signature. Spelling the cast out is
    /// what fails to compile, not what makes it work.
    var kotlinBytes: KotlinByteArray {
        Bytes.shared.fromData(data: self)
    }
}

extension KotlinByteArray {
    var swiftData: Data {
        Bytes.shared.toData(bytes: self)
    }
}

/// The vault file, in the application's own storage.
///
/// The write is atomic and durable, in that order: whole file to a temporary name, forced to
/// the device, then moved into place. A crash leaves either the previous vault or the new
/// one, never half of either — which is how a password manager usually loses everything.
///
/// The protection class is set **at creation**, on the temporary file, because a move carries
/// the source's class onto the destination. Set afterwards, the first save would silently and
/// permanently downgrade the vault. `completeUnlessOpen` rather than the strict class, which
/// would make an open handle fail the moment the device locks — mid-save.
final class VaultFile: NSObject, VaultFileStore {

    static var url: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base.appendingPathComponent("vault.pmvault")
    }

    func exists() -> Bool {
        FileManager.default.fileExists(atPath: Self.url.path)
    }

    func read() -> KotlinByteArray {
        ((try? Data(contentsOf: Self.url)) ?? Data()).kotlinBytes
    }

    func write(bytes: KotlinByteArray) {
        let url = Self.url
        let temporary = url.deletingLastPathComponent()
            .appendingPathComponent("vault.pmvault.writing")
        try? FileManager.default.removeItem(at: temporary)

        guard FileManager.default.createFile(
            atPath: temporary.path,
            contents: nil,
            attributes: [.protectionKey: FileProtectionType.completeUnlessOpen]
        ), let handle = try? FileHandle(forWritingTo: temporary) else { return }

        do {
            try handle.write(contentsOf: bytes.swiftData)
            try handle.synchronize()
            try handle.close()
            _ = try FileManager.default.replaceItemAt(url, withItemAt: temporary)
        } catch {
            try? handle.close()
            try? FileManager.default.removeItem(at: temporary)
        }
    }

    func delete() {
        try? FileManager.default.removeItem(at: Self.url)
    }
}
