import SwiftUI
import PassVaultCore

struct ItemDetailView: View {

    let itemID: String

    @EnvironmentObject private var session: AppSession
    @State private var payload: ItemPayload?
    @State private var revealedFields: Set<String> = []
    @State private var copiedField: String?
    @State private var showingEdit = false
    @State private var showingDeleteConfirmation = false

    var body: some View {
        Group {
            if let payload = payload {
                content(for: payload)
            } else {
                VStack(spacing: 10) {
                    Image(systemName: "questionmark.folder")
                        .font(.system(size: 36))
                        .foregroundStyle(AppColor.outline)
                        .accessibilityHidden(true)
                    Text("This item could not be opened.")
                        .font(AppFont.fieldValue)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }
            }
        }
        .navigationTitle(payload?.title ?? "Item")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Edit") {
                    showingEdit = true
                }
            }
        }
        .sheet(isPresented: $showingEdit, onDismiss: load) {
            AddEditItemView(itemID: itemID)
        }
        .onAppear(perform: load)
    }

    @ViewBuilder
    private func content(for payload: ItemPayload) -> some View {
        List {
            Section {
                hero(for: payload)
            }

            Section {
                switch payload {
                case .login(let value):
                    plainRow("Username", value.username)
                    addressRow("Address", value.address)
                    secretRow("Password", value.password, key: "password")
                case .card(let value):
                    plainRow("Cardholder", value.cardholderName)
                    secretRow("Card number", value.cardNumber, key: "pan")
                    secretRow("CVC", value.cardCvc, key: "cvc")
                    plainRow("Expires", value.cardExpiry)
                case .bank(let value):
                    plainRow("Account number", value.accountNumber)
                    plainRow("Bank", value.bankName)
                    secretRow("Password", value.password, key: "password")
                case .note:
                    EmptyView()
                case .identity(let value):
                    plainRow("First name", value.firstName)
                    plainRow("Last name", value.lastName)
                    plainRow("Email", value.email)
                    plainRow("Phone", value.phone)
                    plainRow("Address", value.address)
                    plainRow("Company", value.company)
                }
            }

            if !payload.notes.isEmpty {
                Section {
                    Text(payload.notes)
                        .font(AppFont.fieldValue)
                        .foregroundStyle(AppColor.onSurface)
                        .textSelection(.enabled)
                } header: {
                    SectionHeader("Notes")
                }
            }

            Section {
                deleteRow
            } footer: {
                if let updated = lastUpdated {
                    Text("Last updated \(updated)")
                        .font(AppFont.footnote)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(AppColor.background)
        .confirmationDialog(
            "Delete this item?",
            isPresented: $showingDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                session.deleteItem(id: itemID)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This cannot be undone.")
        }
    }

    // MARK: - Header

    /// A real header rather than another list row: the tile is large enough to
    /// read as an identity mark, and the category is stated once here in its own
    /// tint instead of being repeated down the screen.
    private func hero(for payload: ItemPayload) -> some View {
        HStack(spacing: 14) {
            CategoryTile(category: payload.category, size: 56)
            VStack(alignment: .leading, spacing: 3) {
                Text(payload.title)
                    .font(AppFont.heroTitle)
                    .foregroundStyle(AppColor.onSurface)
                    .fixedSize(horizontal: false, vertical: true)
                Text(payload.category.label)
                    .font(AppFont.heroCategory)
                    .foregroundStyle(AppColor.tint(for: payload.category))
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }

    /// Both halves are pinned to `AppColor.error`.
    ///
    /// `Button(role: .destructive)` reddens the LABEL only; the glyph kept
    /// inheriting the app's teal accent, so the row rendered a teal trash can
    /// next to red text. A destructive row has to read as one thing.
    private var deleteRow: some View {
        Button(role: .destructive) {
            showingDeleteConfirmation = true
        } label: {
            Label {
                Text("Delete item")
                    .foregroundStyle(AppColor.error)
            } icon: {
                Image(systemName: "trash")
                    .foregroundStyle(AppColor.error)
            }
        }
    }

    // MARK: - Rows

    @ViewBuilder
    private func plainRow(_ label: String, _ value: String) -> some View {
        if !value.isEmpty {
            DetailFieldRow(label) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(value)
                        .font(AppFont.fieldValue)
                        .foregroundStyle(AppColor.onSurface)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                    if copiedField == label {
                        CopyConfirmation()
                    }
                }
            }
            .contextMenu {
                Button {
                    copy(value, key: label)
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
            }
        }
    }

    /// Like ``plainRow(_:_:)``, but renders a real `Link` when the value is an
    /// http(s) URL. Only those two schemes qualify — anything else stays inert
    /// text rather than becoming a tappable target of unknown destination.
    @ViewBuilder
    private func addressRow(_ label: String, _ value: String) -> some View {
        if let url = Self.webURL(from: value) {
            DetailFieldRow(label) {
                Link(destination: url) {
                    Text(value)
                        .font(AppFont.fieldValue)
                        .foregroundStyle(AppColor.primary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .contextMenu {
                Button {
                    copy(value, key: label)
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
            }
        } else {
            plainRow(label, value)
        }
    }

    /// Masked by default, with reveal and copy.
    ///
    /// The value sits on its own line under the label so a 16-digit card number
    /// or a long password is not squeezed into the gap the label leaves behind.
    /// Copying goes through ``Clipboard``, which sets a 15-second expiry and
    /// keeps the value on this device only — which the confirmation says out
    /// loud rather than leaving the user to assume.
    @ViewBuilder
    private func secretRow(_ label: String, _ value: String, key: String) -> some View {
        if !value.isEmpty {
            let isRevealed = revealedFields.contains(key)
            DetailFieldRow(label) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 12) {
                        Text(isRevealed ? value : String(repeating: "•", count: min(value.count, 16)))
                            .font(AppFont.secretValue)
                            .foregroundStyle(AppColor.onSurface)
                            .textSelection(.enabled)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Button {
                            toggleReveal(key)
                        } label: {
                            Image(systemName: isRevealed ? "eye.slash" : "eye")
                                .frame(minWidth: 30, minHeight: 30)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(AppColor.primary)
                        .accessibilityLabel(isRevealed ? "Hide \(label)" : "Reveal \(label)")

                        Button {
                            copy(value, key: key)
                        } label: {
                            Image(systemName: copiedField == key ? "checkmark" : "doc.on.doc")
                                .frame(minWidth: 30, minHeight: 30)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(AppColor.primary)
                        .accessibilityLabel("Copy \(label)")
                    }
                    if copiedField == key {
                        CopyConfirmation()
                    }
                }
            }
        }
    }

    // MARK: - Derived values

    /// Read from the header row the list already holds, so opening a detail
    /// screen does not cost an extra fetch.
    private var lastUpdated: String? {
        guard let header = session.headers.first(where: { $0.id == itemID }) else {
            return nil
        }
        let date = Date(timeIntervalSince1970: TimeInterval(header.updatedAt) / 1000)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private static func webURL(from value: String) -> URL? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              let host = url.host,
              !host.isEmpty
        else {
            return nil
        }
        return url
    }

    // MARK: - Actions

    private func load() {
        payload = session.payload(for: itemID)
    }

    private func toggleReveal(_ key: String) {
        if revealedFields.contains(key) {
            revealedFields.remove(key)
        } else {
            revealedFields.insert(key)
        }
    }

    private func copy(_ value: String, key: String) {
        Clipboard.copySecret(value)
        copiedField = key
        // Only the confirmation resets here. The pasteboard entry itself is
        // cleared by the system at the expiry date `Clipboard` set.
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if copiedField == key {
                copiedField = nil
            }
        }
    }
}
