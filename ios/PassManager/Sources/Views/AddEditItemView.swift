import PassManagerKit
import SwiftUI

struct AddEditItemView: View {
    let existing: VaultItem?

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var category: ItemCategory = .login
    @State private var title = ""
    @State private var notes = ""
    // Login
    @State private var username = ""
    @State private var address = ""
    @State private var password = ""
    // Card
    @State private var cardholderName = ""
    @State private var cardNumber = ""
    @State private var cardCvc = ""
    @State private var cardExpiry = ""
    // Bank
    @State private var bankName = ""
    @State private var accountNumber = ""
    /// Held whole, dangling identifiers included. Those name cards that exist on another
    /// device; nothing here can render them and nothing here may drop them.
    @State private var cardIds: [ItemId] = []
    // Identity
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var phone = ""
    @State private var company = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if existing == nil {
                        Picker("Kind", selection: $category) {
                            ForEach(AllCategories.list, id: \.name) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }

                    PanelField(label: "Title", text: $title)

                    switch category {
                    case .card:
                        PanelField(label: "Cardholder", text: $cardholderName)
                        PanelField(label: "Number", text: $cardNumber, secure: true)
                        PanelField(label: "Security code", text: $cardCvc, secure: true)
                        PanelField(label: "Expires", text: $cardExpiry)
                    case .bank:
                        PanelField(label: "Bank", text: $bankName)
                        PanelField(label: "Account", text: $accountNumber, secure: true)
                        PasswordField(label: "Password", text: $password)
                        cardLinks
                    case .identity:
                        PanelField(label: "First name", text: $firstName)
                        PanelField(label: "Last name", text: $lastName)
                        PanelField(label: "Email", text: $email)
                        PanelField(label: "Phone", text: $phone)
                        PanelField(label: "Address", text: $address)
                        PanelField(label: "Company", text: $company)
                    case .note:
                        EmptyView()
                    default:
                        PanelField(label: "Username", text: $username)
                        PanelField(label: "Address", text: $address)
                        PasswordField(label: "Password", text: $password)
                    }

                    PanelField(label: "Notes", text: $notes, multiline: true)
                }
                .padding(16)
            }
            .background(Palette.background.ignoresSafeArea())
            .navigationTitle(existing == nil ? "New entry" : "Edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") { save() }
                        .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let item = existing else { return }
        category = item.category
        title = item.payload.title
        notes = item.payload.notes.revealed()
        switch item.payload {
        case let login as ItemPayloadLogin:
            username = login.username
            address = login.address
            password = login.password.revealed()
        case let card as ItemPayloadCard:
            cardholderName = card.cardholderName
            cardNumber = card.cardNumber.revealed()
            cardCvc = card.cardCvc.revealed()
            cardExpiry = card.cardExpiry
        case let bank as ItemPayloadBank:
            bankName = bank.bankName
            accountNumber = bank.accountNumber.revealed()
            password = bank.password.revealed()
            cardIds = bank.cardIds
        case let identity as ItemPayloadIdentity:
            firstName = identity.firstName
            lastName = identity.lastName
            email = identity.email
            phone = identity.phone
            address = identity.address
            company = identity.company
        default:
            break
        }
    }

    private func save() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let secretNotes = SecretText.companion.of(text: notes)

        let payload: ItemPayload
        switch category {
        case .card:
            payload = ItemPayloadCard(
                title: title, notes: secretNotes,
                cardholderName: cardholderName,
                cardNumber: SecretText.companion.of(text: cardNumber),
                cardCvc: SecretText.companion.of(text: cardCvc),
                cardExpiry: cardExpiry
            )
        case .bank:
            // The password history is not a field this screen renders, so it is not one this
            // screen decides. withHistoryFrom carries the old list forward and captures the
            // password being replaced — in the model, where both apps reach one answer. This
            // used to pass an empty list, which destroyed the history on every save.
            payload = ItemPayloadBank(
                title: title, notes: secretNotes,
                bankName: bankName,
                accountNumber: SecretText.companion.of(text: accountNumber),
                password: SecretText.companion.of(text: password),
                previousPasswords: [],
                cardIds: cardIds
            ).withHistoryFrom(earlier: existing?.payload as? ItemPayloadBank)
        case .identity:
            payload = ItemPayloadIdentity(
                title: title, notes: secretNotes,
                firstName: firstName, lastName: lastName,
                email: email, phone: phone, address: address, company: company
            )
        case .note:
            payload = ItemPayloadNote(title: title, notes: secretNotes)
        default:
            payload = ItemPayloadLogin(
                title: title, notes: secretNotes,
                username: username, address: address,
                password: SecretText.companion.of(text: password)
            )
        }

        // An edit keeps the identity, the creation time, and the modification time of an
        // entry nothing was actually typed into. All three rules live on the item, shared.
        let item = existing?.edited(payload: payload, now: now)
            ?? VaultItem(id: ItemId.companion.random(), createdAt: now, updatedAt: now, payload: payload)
        session.save(item)
        dismiss()
    }

    /// The cards this account issues, chosen from the cards the vault already holds.
    ///
    /// No free-text field, because a link is an identifier and one typed by hand would name
    /// nothing. A bank can therefore only be linked to a card that has already been entered.
    private var cardLinks: some View {
        let cards = session.items.filter { $0.category == .card }
        let linked = cardIds.compactMap { id in cards.first { $0.id == id } }
        let unlinked = cards.filter { card in !cardIds.contains { $0 == card.id } }

        return VStack(alignment: .leading, spacing: 8) {
            Text("Cards")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onSurfaceVariant)

            if cards.isEmpty {
                Text("Add a card entry first, then it can be linked to this account.")
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
            } else {
                PanelCard {
                    ForEach(Array(linked.enumerated()), id: \.element.id.value) { index, card in
                        linkRow(card.payload.title, action: "Unlink", muted: false) {
                            cardIds.removeAll { $0 == card.id }
                        }
                        if index < linked.count - 1 { Divider().padding(.leading, 14) }
                    }
                    if !linked.isEmpty && !unlinked.isEmpty { Divider().padding(.leading, 14) }
                    ForEach(Array(unlinked.enumerated()), id: \.element.id.value) { index, card in
                        linkRow(card.payload.title, action: "Link", muted: true) {
                            cardIds.append(card.id)
                        }
                        if index < unlinked.count - 1 { Divider().padding(.leading, 14) }
                    }
                }
            }
        }
    }

    private func linkRow(
        _ title: String,
        action: String,
        muted: Bool,
        tap: @escaping () -> Void
    ) -> some View {
        HStack {
            Text(title)
                .foregroundStyle(muted ? Palette.onSurfaceVariant : Palette.onSurface)
            Spacer()
            Button(action, action: tap)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }
}
