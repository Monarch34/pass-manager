import PassManagerKit
import UIKit

/// A small picture of an attachment, for the row that lists it.
///
/// Only for images, and only for images the platform can decode. A document has no thumbnail
/// here — rendering the first page of a PDF to make one would mean opening the document on
/// every attach, which is a lot of work for a row that already says what the file is called.
///
/// The result travels inside the attachment's own sealed header, so it is encrypted exactly
/// like the file it depicts. That is the reason to make one at all rather than reading the
/// attachment back to draw a list: a header is a few kilobytes and an attachment can be five
/// megabytes.
enum Thumbnails {

    /// Big enough for a list row on a dense screen, small enough to encode to a few KB.
    private static let maxEdge: CGFloat = 192

    /// Null when the bytes are not an image, or when no small enough version could be made.
    static func of(_ data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }

        let longest = max(image.size.width, image.size.height)
        let scale = longest > maxEdge ? maxEdge / longest : 1
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)

        let renderer = UIGraphicsImageRenderer(size: size, format: .init())
        let scaled = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }

        // Quality is low on purpose. The whole budget is MaxThumbnailSize, and every
        // attachment's copy is read whenever an item is opened. Stepping down rather than
        // failing outright, because losing a thumbnail must never be why a scan cannot be
        // attached.
        let limit = Int(VaultSession.companion.MaxThumbnailSize)
        for quality in [0.55, 0.4, 0.25] {
            if let encoded = scaled.jpegData(compressionQuality: quality), encoded.count <= limit {
                return encoded
            }
        }
        return nil
    }
}
