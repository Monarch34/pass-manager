import SwiftUI
import PassVaultCore

struct AddEditItemView: View {

    /// `nil` for a new item.
    let itemID: String?

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var form = ItemFormSnapshot()
    @State private var showingGenerator = false
    @State private var didLoad = false

    private var isEditing: Bool {
        return itemID != nil
    }

    private var failure: SaveFailure? {
        return ItemFormValidator.failure(for: form)
    }

    private var bankViolations: [BankPasswordRules.Violation] {
        return BankPasswordRules.violations(
            in: form.bankPassword,
            previousPasswords: form.previousPasswords
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                if !isEditing {
                    Section("Category") {
                        Picker("Category", selection: $form.category) {
                            ForEach(ItemCategory.allCases, id: \.self) { category in
                                Label(category.label, systemImage: category.symbolName)
                                    .tag(category)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                }

                Section("Details") {
                    TextField("Title", text: $form.title)
                        .textInputAutocapitalization(.words)
                }

                categoryFields

                Section("Notes") {
                    TextField("Notes", text: $form.notes, axis: .vertical)
                        .lineLimit(3...8)
                }

                if let failure = failure {
                    Section {
                        Text(failure.message)
                            .font(.footnote)
                            .foregroundStyle(AppColor.error)
                    }
                }
            }
            .navigationTitle(isEditing ? "Edit item" : "New item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(failure != nil)
                }
            }
            .sheet(isPresented: $showingGenerator) {
                GeneratorView(constraintCategory: form.category) { generated in
                    if form.category == .bank {
                        form.bankPassword = generated
                    } else {
                        form.password = generated
                    }
                }
            }
        }
        .onAppear(perform: loadIfNeeded)
    }

    // MARK: - Per-category fields

    @ViewBuilder
    private var categoryFields: some View {
        switch form.category {
        case .login:
            Section("Login") {
                TextField("Username", text: $form.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Address", text: $form.address)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                passwordField(text: $form.password, label: "Password")
            }
        case .card:
            Section("Card") {
                TextField("Cardholder name", text: $form.cardholderName)
                    .textInputAutocapitalization(.words)
                TextField("Card number", text: $form.cardNumber)
                    .keyboardType(.numberPad)
                    .onChange(of: form.cardNumber) { value in
                        let digits = CardRules.panDigitsOnly(value)
                        form.cardNumber = String(digits.prefix(CardRules.panDigits))
                    }
                HStack {
                    TextField("MM/YY", text: expiryBinding)
                        .keyboardType(.numberPad)
                    Divider()
                    TextField("CVC", text: $form.cardCvc)
                        .keyboardType(.numberPad)
                        .onChange(of: form.cardCvc) { value in
                            form.cardCvc = CardRules.sanitizeCvcDigits(value)
                        }
                }
                if CardRules.isCvcWeak(form.cardCvc) {
                    // A warning, not a save gate — Android does not block on it.
                    Text("A CVC is usually 3 or 4 digits.")
                        .font(.caption)
                        .foregroundStyle(AppColor.strengthFair)
                }
            }
        case .bank:
            Section("Bank") {
                TextField("Account number", text: $form.accountNumber)
                    .textInputAutocapitalization(.never)
                TextField("Bank name", text: $form.bankName)
                    .textInputAutocapitalization(.words)
                passwordField(text: $form.bankPassword, label: "Bank password")
            }
            Section("Password rules") {
                ForEach(BankPasswordRules.Violation.allCases, id: \.self) { rule in
                    let broken = bankViolations.contains(rule)
                    Label {
                        Text(rule.message)
                            .font(.caption)
                            .foregroundStyle(broken ? AppColor.error : AppColor.onSurfaceVariant)
                    } icon: {
                        Image(systemName: broken ? "xmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundStyle(broken ? AppColor.error : AppColor.primary)
                    }
                }
            }
        case .identity:
            Section("Identity") {
                TextField("First name", text: $form.firstName)
                TextField("Last name", text: $form.lastName)
                TextField("Email", text: $form.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                TextField("Phone", text: $form.phone)
                    .keyboardType(.phonePad)
                TextField("Address", text: $form.identityAddress)
                TextField("Company", text: $form.company)
            }
        case .note:
            EmptyView()
        }
    }

    private func passwordField(text: Binding<String>, label: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                SecureField(label, text: text)
                    .textContentType(.newPassword)
                Button {
                    showingGenerator = true
                } label: {
                    Image(systemName: "dice")
                }
                .buttonStyle(.plain)
                .foregroundStyle(AppColor.primary)
                .accessibilityLabel("Generate password")
            }
            if !text.wrappedValue.isEmpty {
                StrengthBar(strength: PasswordStrength.evaluate(text.wrappedValue), showsLabel: false)
            }
        }
    }

    /// The stored value is digits only; the field shows `MM/YY`.
    private var expiryBinding: Binding<String> {
        return Binding(
            get: {
                let digits = form.cardExpiry
                if digits.count <= 2 {
                    return digits
                }
                let index = digits.index(digits.startIndex, offsetBy: 2)
                let month = String(digits[..<index])
                let year = String(digits[index...])
                return month + "/" + year
            },
            set: { newValue in
                form.cardExpiry = CardRules.sanitizeExpiryDigits(newValue)
            }
        )
    }

    // MARK: - Actions

    private func loadIfNeeded() {
        guard !didLoad else {
            return
        }
        didLoad = true
        guard let itemID = itemID, let payload = session.payload(for: itemID) else {
            return
        }
        form = ItemFormValidator.makeForm(from: payload)
    }

    private func save() {
        let id = itemID ?? UUID().uuidString
        switch ItemFormValidator.makePayload(from: form, id: id) {
        case .success(let payload):
            if isEditing {
                session.updateItem(payload)
            } else {
                session.createItem(payload)
            }
            dismiss()
        case .failure:
            // The button is disabled while a failure exists, so this is
            // unreachable in practice; the inline message already says why.
            break
        }
    }
}
