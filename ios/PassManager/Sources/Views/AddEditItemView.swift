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
                        PanelField(label: "Password", text: $password, secure: true)
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
                        PanelField(label: "Password", text: $password, secure: true)
                    }

                    PanelField(label: "Notes", text: $notes)
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
            payload = ItemPayloadBank(
                title: title, notes: secretNotes,
                bankName: bankName,
                accountNumber: SecretText.companion.of(text: accountNumber),
                password: SecretText.companion.of(text: password),
                previousPasswords: [],
                // Links are not editable yet; an edit must carry forward what it did not
                // show, or saving a bank would silently delete every card it names.
                cardIds: (existing?.payload as? ItemPayloadBank)?.cardIds ?? []
            )
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

        let item = VaultItem(
            id: existing?.id ?? ItemId.companion.random(),
            // An edit keeps the original creation time; only a new entry takes now.
            createdAt: existing?.createdAt ?? now,
            updatedAt: now,
            payload: payload
        )
        session.save(item)
        dismiss()
    }
}
