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
                    Text("This item could not be opened.")
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
                HStack(spacing: 12) {
                    CategoryTile(category: payload.category, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(payload.title)
                            .font(.headline)
                            .foregroundStyle(AppColor.onSurface)
                        Text(payload.category.label)
                            .font(.caption)
                            .foregroundStyle(AppColor.tint(for: payload.category))
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                switch payload {
                case .login(let value):
                    plainRow("Username", value.username)
                    plainRow("Address", value.address)
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
                Section("Notes") {
                    Text(payload.notes)
                        .font(.body)
                        .foregroundStyle(AppColor.onSurface)
                }
            }

            Section {
                Button(role: .destructive) {
                    showingDeleteConfirmation = true
                } label: {
                    Label("Delete item", systemImage: "trash")
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

    // MARK: - Rows

    @ViewBuilder
    private func plainRow(_ label: String, _ value: String) -> some View {
        if !value.isEmpty {
            HStack {
                Text(label)
                    .foregroundStyle(AppColor.onSurfaceVariant)
                Spacer()
                Text(value)
                    .foregroundStyle(AppColor.onSurface)
                    .multilineTextAlignment(.trailing)
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

    /// Masked by default, with reveal and copy. Copying goes through
    /// ``Clipboard``, which sets a 15-second expiry and keeps the value on this
    /// device only.
    @ViewBuilder
    private func secretRow(_ label: String, _ value: String, key: String) -> some View {
        if !value.isEmpty {
            HStack(spacing: 12) {
                Text(label)
                    .foregroundStyle(AppColor.onSurfaceVariant)
                Spacer(minLength: 8)
                Text(revealedFields.contains(key) ? value : String(repeating: "•", count: min(value.count, 12)))
                    .font(revealedFields.contains(key)
                          ? Font.system(.body, design: .monospaced)
                          : Font.body)
                    .foregroundStyle(AppColor.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Button {
                    toggleReveal(key)
                } label: {
                    Image(systemName: revealedFields.contains(key) ? "eye.slash" : "eye")
                }
                .buttonStyle(.plain)
                .foregroundStyle(AppColor.primary)
                .accessibilityLabel(revealedFields.contains(key) ? "Hide \(label)" : "Reveal \(label)")

                Button {
                    copy(value, key: key)
                } label: {
                    Image(systemName: copiedField == key ? "checkmark" : "doc.on.doc")
                }
                .buttonStyle(.plain)
                .foregroundStyle(AppColor.primary)
                .accessibilityLabel("Copy \(label)")
            }
        }
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
        // Only the "Copied" tick resets here. The pasteboard entry itself is
        // cleared by the system at the expiry date `Clipboard` set.
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if copiedField == key {
                copiedField = nil
            }
        }
    }
}
