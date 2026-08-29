import PassManagerKit
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import UniformTypeIdentifiers

struct ItemDetailView: View {
    let item: VaultItem
    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss
    @State private var editing = false
    @State private var confirmingDelete = false
    @State private var attachments: [Attachment] = []
    @State private var picking = false

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                hero
                PanelCard {
                    ForEach(Array(fields.enumerated()), id: \.offset) { index, field in
                        FieldRow(field: field)
                        if index < fields.count - 1 { Divider().padding(.leading, 14) }
                    }
                }
                attachmentSection

                Button(role: .destructive) { confirmingDelete = true } label: {
                    Text("Delete").frame(maxWidth: .infinity).padding(.vertical, 14)
                }
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.card, style: .continuous))
            }
            .padding(16)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(item.payload.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Edit") { editing = true }
            }
        }
        .sheet(isPresented: $editing) { AddEditItemView(existing: item) }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.data]) { result in
            if case .success(let url) = result {
                session.attach(url, to: item)
                attachments = session.attachments(of: item)
            }
        }
        .onAppear { attachments = session.attachments(of: item) }
        .alert("Delete this entry?", isPresented: $confirmingDelete) {
            Button("Delete", role: .destructive) {
                session.delete(item)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var attachmentSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Attachments")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onSurfaceVariant)

            PanelCard {
                if attachments.isEmpty {
                    Text("Nothing attached yet.")
                        .font(.subheadline)
                        .foregroundStyle(Palette.onSurfaceVariant)
                        .padding(14)
                } else {
                    ForEach(Array(attachments.enumerated()), id: \.element.id) { index, attachment in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(attachment.filename.isEmpty ? "Attachment" : attachment.filename)
                                Text(readableSize(attachment.size))
                                    .font(.footnote)
                                    .foregroundStyle(Palette.onSurfaceVariant)
                            }
                            Spacer()
                            Button("Remove") {
                                session.deleteAttachment(attachment.id)
                                attachments = session.attachments(of: item)
                            }
                            .foregroundStyle(Palette.error)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        if index < attachments.count - 1 { Divider().padding(.leading, 14) }
                    }
                }
            }

            if attachments.count < Int(VaultSession.companion.MaxAttachmentsPerItem) {
                Button("Add attachment") { picking = true }
                    .font(.subheadline)
            } else {
                Text("An item holds at most \(VaultSession.companion.MaxAttachmentsPerItem) attachments.")
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
            }
        }
    }

    /// Sizes as a person reads them, not as a machine stores them.
    private func readableSize(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) bytes" }
        if bytes < 1024 * 1024 { return "\(bytes / 1024) KB" }
        return String(format: "%.1f MB", Double(bytes) / (1024 * 1024))
    }

    private var hero: some View {
        VStack(spacing: 8) {
            Image(systemName: item.category.symbol)
                .font(.system(size: 30))
                .foregroundStyle(Palette.category(item.category))
                .frame(width: 64, height: 64)
                .background(Palette.category(item.category).opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: Metrics.field, style: .continuous))
            Text(item.category.label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Palette.onSurfaceVariant)
        }
        .padding(.vertical, 8)
    }

    private var fields: [Field] {
        var out: [Field] = []
        switch item.payload {
        case let login as ItemPayloadLogin:
            out.append(Field("Username", login.username))
            out.append(Field("Address", login.address))
            out.append(Field("Password", login.password))
        case let card as ItemPayloadCard:
            out.append(Field("Cardholder", card.cardholderName))
            out.append(Field("Number", card.cardNumber))
            out.append(Field("Security code", card.cardCvc))
            out.append(Field("Expires", card.cardExpiry))
        case let bank as ItemPayloadBank:
            out.append(Field("Bank", bank.bankName))
            out.append(Field("Account", bank.accountNumber))
            out.append(Field("Password", bank.password))
        case let identity as ItemPayloadIdentity:
            out.append(Field("Name", "\(identity.firstName) \(identity.lastName)".trimmingCharacters(in: .whitespaces)))
            out.append(Field("Email", identity.email))
            out.append(Field("Phone", identity.phone))
            out.append(Field("Address", identity.address))
            out.append(Field("Company", identity.company))
        default:
            break
        }
        out.append(Field("Notes", item.payload.notes))
        return out.filter { !$0.isEmpty }
    }
}

/// A field, and whether it is the kind that should be hidden until asked for.
struct Field {
    let label: String
    let value: String
    let secret: Bool

    init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
        self.secret = false
    }

    init(_ label: String, _ value: SecretText) {
        self.label = label
        // Revealed at the moment of display and not stored beyond this view's lifetime.
        // The type is what keeps that decision visible; SwiftUI needs a String to draw.
        self.value = value.revealed()
        self.secret = true
    }

    var isEmpty: Bool { value.isEmpty }
}

struct FieldRow: View {
    let field: Field
    @State private var shown = false
    @State private var copied = false

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(field.label)
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
                Text(display)
                    .font(field.secret ? .system(.body, design: .monospaced) : .body)
                    .foregroundStyle(Palette.onSurface)
                    .textSelection(.enabled)
            }
            Spacer()
            if field.secret {
                Button { shown.toggle() } label: {
                    Image(systemName: shown ? "eye.slash" : "eye")
                }
                .buttonStyle(.plain)
                .foregroundStyle(Palette.onSurfaceVariant)
            }
            Button { copy() } label: {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
            }
            .buttonStyle(.plain)
            .foregroundStyle(copied ? Palette.primary : Palette.onSurfaceVariant)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }

    private var display: String {
        guard field.secret, !shown else { return field.value }
        return String(repeating: "•", count: min(field.value.count, 12))
    }

    /// The clipboard is shared with every other app on the device, so a copied secret is
    /// marked sensitive — which keeps it out of the system's clipboard preview — and
    /// expires on its own rather than waiting to be overwritten.
    private func copy() {
        if field.secret {
            UIPasteboard.general.setItems(
                [[UTType.plainText.identifier: field.value]],
                options: [
                    .localOnly: true,
                    .expirationDate: Date().addingTimeInterval(45),
                ]
            )
        } else {
            UIPasteboard.general.string = field.value
        }
        copied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
    }
}
