import PDFKit
import PassManagerKit
import SwiftUI
import UIKit

/// An attachment, on screen, decrypted for exactly as long as it is being looked at.
///
/// Nothing is written out to show it. `UIImage` and `PDFDocument` both take `Data`, so an
/// image and a document are drawn from the bytes in hand — no temporary file, no copy in the
/// caches directory, and no handing the document to another application that would then hold
/// a copy this vault cannot reach. The cost of that is the last case below: what cannot be
/// drawn here is not shown at all.
///
/// Which kind of file it is comes from the shared core, so this and the Android viewer make
/// the same decision about the same bytes.
struct AttachmentView: View {
    let attachment: Attachment
    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss
    @State private var preview: Preview = .loading

    var body: some View {
        NavigationStack {
            Group {
                switch preview {
                case .loading:
                    ProgressView()
                case .picture(let image):
                    ZoomableImage(image: image)
                case .document(let document):
                    PdfPages(document: document)
                        .ignoresSafeArea(edges: .bottom)
                case .words(let text, let truncated):
                    Words(text: text, truncated: truncated)
                case .unavailable(let reason):
                    Unavailable(reason: reason, mimeType: attachment.mimeType)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Palette.background.ignoresSafeArea())
            .navigationTitle(attachment.filename.isEmpty ? "Attachment" : attachment.filename)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task {
            guard let data = session.openAttachment(attachment.id) else {
                preview = .unavailable("This attachment could not be opened.")
                return
            }
            preview = Self.render(attachment, data)
        }
    }

    private enum Preview {
        case loading
        case picture(UIImage)
        case document(PDFDocument)
        case words(String, truncated: Bool)
        case unavailable(String)
    }

    /// Enough text to read; a five-megabyte log in one `Text` is not readable and not fast.
    private static let maxCharacters = 256 * 1024

    private static func render(_ attachment: Attachment, _ data: Data) -> Preview {
        let prefix = Data(data.prefix(Int(AttachmentKinds.shared.SniffSize)))
        switch AttachmentKinds.shared.of(declaredType: attachment.mimeType, prefix: prefix.kotlinBytes) {
        case .image:
            guard let image = UIImage(data: data) else {
                // A kind the classifier recognises that this platform will not decode — an
                // SVG, most often, which is an image everywhere except to UIImage.
                return .unavailable("This image is in a format the phone cannot draw.")
            }
            return .picture(image)

        case .pdf:
            guard let document = PDFDocument(data: data) else {
                return .unavailable("This PDF could not be opened.")
            }
            return .document(document)

        case .text:
            guard let text = String(data: data, encoding: .utf8) else {
                return .unavailable("There is no viewer here for this file.")
            }
            let shown = String(text.prefix(maxCharacters))
            return .words(shown, truncated: shown.count < text.count)

        default:
            return .unavailable("There is no viewer here for this file.")
        }
    }
}

/// The image, fitted and then movable.
///
/// Pinch and drag rather than a fixed fit, because the reason to attach a photograph of a
/// document to a password entry is usually a number printed small on it.
private struct ZoomableImage: View {
    let image: UIImage
    @State private var scale: CGFloat = 1
    @State private var committed: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var placed: CGSize = .zero

    var body: some View {
        Image(uiImage: image)
            .resizable()
            .scaledToFit()
            .scaleEffect(scale)
            .offset(offset)
            .gesture(
                MagnificationGesture()
                    .onChanged { value in scale = min(max(committed * value, 1), 8) }
                    .onEnded { _ in
                        committed = scale
                        // Back at fit, the image belongs in the middle again rather than
                        // wherever it was dragged to while zoomed.
                        if scale == 1 {
                            offset = .zero
                            placed = .zero
                        }
                    }
            )
            .simultaneousGesture(
                DragGesture()
                    .onChanged { value in
                        guard scale > 1 else { return }
                        offset = CGSize(
                            width: placed.width + value.translation.width,
                            height: placed.height + value.translation.height
                        )
                    }
                    .onEnded { _ in placed = offset }
            )
    }
}

/// PDFKit's own view, which already scrolls, zooms and knows what a page is.
private struct PdfPages: UIViewRepresentable {
    let document: PDFDocument

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.document = document
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if view.document !== document { view.document = document }
    }
}

private struct Words: View {
    let text: String
    let truncated: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(text)
                    .font(.system(.footnote, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if truncated {
                    Text("Only the first part is shown. The whole file is still stored.")
                        .font(.footnote)
                        .foregroundStyle(Palette.onSurfaceVariant)
                }
            }
            .padding(16)
        }
    }
}

private struct Unavailable: View {
    let reason: String
    let mimeType: String

    var body: some View {
        PanelCard {
            VStack(alignment: .leading, spacing: 8) {
                Text(reason)
                Text("Images, PDFs and text can be shown here. Anything else stays sealed rather than being written out for another app to open.")
                    .font(.subheadline)
                    .foregroundStyle(Palette.onSurfaceVariant)
                if !mimeType.isEmpty {
                    Text(mimeType)
                        .font(.footnote)
                        .foregroundStyle(Palette.onSurfaceVariant)
                }
            }
            .padding(16)
        }
        .padding(16)
    }
}
