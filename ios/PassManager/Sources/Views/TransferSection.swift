import PassManagerKit
import SwiftUI
import UniformTypeIdentifiers

/// Taking a copy off the phone, and putting one back.
///
/// There is no account and no server, so this is the only way a vault ever leaves this device
/// and the only way one arrives. Both sit under one heading because they are two directions
/// of the same thing, and because whoever is looking for "backup" needs to find "restore"
/// beside it rather than discover later that it was never there.
struct TransferSection: View {
    @EnvironmentObject private var session: AppSession

    @State private var exporting = false
    @State private var importing = false
    @State private var passphraseFor: Purpose?
    @State private var incoming: Data?
    @State private var changed = false

    private enum Purpose: Identifiable {
        case export
        case openFile
        case change
        var id: Int {
            switch self {
            case .export: return 0
            case .openFile: return 1
            case .change: return 2
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Passphrase")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onSurfaceVariant)

            PanelCard {
                row(
                    "Change passphrase",
                    changed
                        ? "Changed. Face ID still works."
                        : "Every other way in keeps working, and nothing is re-encrypted."
                ) { passphraseFor = .change }
            }

            Text("Backup")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onSurfaceVariant)

            PanelCard {
                row(
                    "Export this vault",
                    "One file, sealed with a passphrase you choose now."
                ) { passphraseFor = .export }
                Divider().padding(.leading, 14)
                row(
                    "Import a vault file",
                    "Merged into this vault. Nothing is removed without asking."
                ) { importing = true }
            }

            if session.busy {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Working. A file that leaves the phone is deliberately slow to open.")
                        .font(.footnote)
                        .foregroundStyle(Palette.onSurfaceVariant)
                }
            }

            Text("An export is a snapshot, not a spare key: it cannot open this vault and this vault cannot open it. Face ID does not travel with it, so restoring needs the passphrase you set here. Anyone holding the file and that passphrase can read everything in it, and there is no way to revoke that.")
                .font(.footnote)
                .foregroundStyle(Palette.onSurfaceVariant)
        }
        // The bytes exist before a destination does, so where to put them is asked the moment
        // they are ready rather than before the passphrase has even been typed.
        .onChange(of: session.pendingExport != nil) { ready in exporting = ready }
        .fileExporter(
            isPresented: $exporting,
            document: session.pendingExport.map(VaultFileDocument.init),
            contentType: .data,
            defaultFilename: "PassManager.pmvault"
        ) { _ in
            session.pendingExport = nil
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else {
                session.failure = "That file could not be read."
                return
            }
            incoming = data
            passphraseFor = .openFile
        }
        .sheet(item: $passphraseFor) { purpose in
            PassphrasePrompt(
                title: title(for: purpose),
                message: message(for: purpose),
                confirm: confirm(for: purpose),
                repeated: purpose != .openFile,
                askCurrent: purpose == .change
            ) { current, typed in
                passphraseFor = nil
                switch purpose {
                case .export:
                    session.export(passphrase: typed)
                case .change:
                    session.changePassphrase(current: current, next: typed) { changed = $0 }
                case .openFile:
                    if let data = incoming {
                        incoming = nil
                        session.readImport(data, passphrase: typed)
                    }
                }
            }
        }
        .alert(
            session.importPreview?.isEmpty == true ? "Nothing to import" : "Import this file?",
            isPresented: Binding(
                get: { session.importPreview != nil },
                set: { if !$0 { session.discardImport() } }
            ),
            presenting: session.importPreview
        ) { preview in
            Button(preview.isEmpty ? "Done" : "Import") { session.applyImport() }
            if !preview.isEmpty {
                Button("Cancel", role: .cancel) { session.discardImport() }
            }
        } message: { preview in
            Text(describe(preview))
        }
    }

    private func title(for purpose: Purpose) -> String {
        switch purpose {
        case .export: return "Passphrase for this export"
        case .openFile: return "Passphrase for this file"
        case .change: return "Change your passphrase"
        }
    }

    private func message(for purpose: Purpose) -> String {
        switch purpose {
        case .export:
            return "Not your vault passphrase unless you choose it to be. Restoring needs this exact passphrase, and nothing can recover it."
        case .openFile:
            return "The passphrase that was set when this file was exported."
        case .change:
            return "Exports already taken keep their own passphrases and are not affected."
        }
    }

    private func confirm(for purpose: Purpose) -> String {
        switch purpose {
        case .export: return "Export"
        case .openFile: return "Open"
        case .change: return "Change"
        }
    }

    private func row(_ title: String, _ subtitle: String, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).foregroundStyle(Palette.onSurface)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(session.busy)
    }

    /// Removals are named rather than counted: it is the only outcome that destroys something
    /// already here, and there is no undo anywhere in this application.
    private func describe(_ preview: ImportPreview) -> String {
        if preview.isEmpty { return "This vault already holds everything that file has." }
        var lines: [String] = []
        if !preview.added.isEmpty { lines.append("\(preview.added.count) to add") }
        if !preview.replaced.isEmpty {
            lines.append("\(preview.replaced.count) to update with a newer version")
        }
        if preview.attachmentsAdded > 0 {
            lines.append("\(preview.attachmentsAdded) attachments to add")
        }
        if !preview.removed.isEmpty {
            lines.append("Deleted on the other device, so they go here too:")
            lines.append(contentsOf: preview.removed.map { "· " + $0.payload.title })
        }
        return lines.joined(separator: "\n")
    }
}

/// The export, as something the Files app can be handed.
///
/// Read-only on purpose: this type exists to write one file that already exists in memory,
/// and giving it a reading initialiser would invite using it as a general document type for
/// a format whose reader is the shared core.
struct VaultFileDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.data]

    let bytes: Data

    init(_ bytes: Data) {
        self.bytes = bytes
    }

    init(configuration: ReadConfiguration) throws {
        throw CocoaError(.fileReadUnsupportedScheme)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: bytes)
    }
}

struct PassphrasePrompt: View {
    let title: String
    let message: String
    let confirm: String
    var repeated: Bool = true
    var askCurrent: Bool = false
    let onSubmit: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var current = ""
    @State private var passphrase = ""
    @State private var again = ""

    /// A length rule belongs on a passphrase being *chosen*, never on one being *entered*.
    /// `repeated` is what distinguishes the two, and applying the minimum to both would make
    /// a file whose passphrase is seven characters impossible to open — the file's passphrase
    /// is whatever it already is, and this screen does not get a vote.
    private var ready: Bool {
        (repeated ? passphrase.count >= 8 : !passphrase.isEmpty) &&
            (!repeated || passphrase == again) &&
            (!askCurrent || !current.isEmpty)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
                if askCurrent {
                    PanelField(label: "Current passphrase", text: $current, secure: true)
                }
                PanelField(
                    label: askCurrent ? "New passphrase" : "Passphrase",
                    text: $passphrase,
                    secure: true
                )
                if repeated {
                    PanelField(label: "Repeat it", text: $again, secure: true)
                    if !again.isEmpty && passphrase != again {
                        Text("The two do not match.")
                            .font(.footnote)
                            .foregroundStyle(Palette.error)
                    }
                }
                PillButton(title: confirm, enabled: ready) { onSubmit(current, passphrase) }
                Spacer()
            }
            .padding(20)
            .background(Palette.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
