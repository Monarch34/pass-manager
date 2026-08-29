import Foundation
import PassManagerKit

/// Attachments, one file each, in a directory of their own.
///
/// The directory is exactly what it appears to be: a count of attachments and nothing else.
/// Every file is named by its random identifier, and every other fact about it — the
/// filename, the type, the size, the item it belongs to — is inside its seal.
///
/// `readPrefix` genuinely reads a prefix rather than a whole file and then trimming it. That
/// is the whole point of the method: listing an item's attachments needs each one's details
/// and none of their contents, and pulling five megabytes off the disk to draw a row would
/// make opening an item with a few scans attached noticeably slow.
final class BlobStore: NSObject, BlobFileStore {

    private let directory: URL

    override init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        directory = base.appendingPathComponent("blobs", isDirectory: true)
        super.init()
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func list() -> [String] {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return names.filter { !$0.hasSuffix(Self.writing) }
    }

    func read(id: String) -> KotlinByteArray {
        guard let data = try? Data(contentsOf: file(id)) else { return Data().kotlinBytes }
        return data.kotlinBytes
    }

    func readPrefix(id: String, maxBytes: Int32) -> KotlinByteArray {
        guard let handle = try? FileHandle(forReadingFrom: file(id)) else {
            return Data().kotlinBytes
        }
        defer { try? handle.close() }
        let prefix = (try? handle.read(upToCount: Int(maxBytes))) ?? Data()
        return prefix.kotlinBytes
    }

    func write(id: String, bytes: KotlinByteArray) {
        let temporary = directory.appendingPathComponent(id + Self.writing)
        try? FileManager.default.removeItem(at: temporary)

        // The protection class is set at creation, on the temporary file, because a move
        // carries the source's class onto the destination. Set afterwards, the first write
        // would silently and permanently downgrade the attachment.
        guard FileManager.default.createFile(
            atPath: temporary.path,
            contents: nil,
            attributes: [.protectionKey: FileProtectionType.completeUnlessOpen]
        ), let handle = try? FileHandle(forWritingTo: temporary) else { return }

        do {
            try handle.write(contentsOf: bytes.swiftData)
            try handle.synchronize()
            try handle.close()
            _ = try FileManager.default.replaceItemAt(file(id), withItemAt: temporary)
        } catch {
            try? handle.close()
            try? FileManager.default.removeItem(at: temporary)
        }
    }

    func delete(id: String) {
        try? FileManager.default.removeItem(at: file(id))
    }

    /// Refuses anything that is not a bare identifier.
    ///
    /// The name comes from inside a file this app wrote, but a container is a thing someone
    /// can hand over: without this, a crafted identifier of `../Preferences/x` would make a
    /// read or a delete escape this directory.
    private func file(_ id: String) -> URL {
        let safe = id.allSatisfy { $0.isHexDigit && !$0.isUppercase } && !id.isEmpty
        precondition(safe, "an attachment identifier is lower-case hexadecimal")
        return directory.appendingPathComponent(id)
    }

    private static let writing = ".writing"
}
