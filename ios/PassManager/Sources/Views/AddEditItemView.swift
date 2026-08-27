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
                    Section {
                        Picker("Category", selection: $form.category) {
                            ForEach(ItemCategory.allCases, id: \.self) { category in
                                Label(category.label, systemImage: category.symbolName)
                                    .tag(category)
                            }
                        }
                        .pickerStyle(.menu)
                    } header: {
                        SectionHeader("Category")
                    }
                }

                Section {
                    TextField("Title", text: $form.title)
                        .textInputAutocapitalization(.words)
                } header: {
                    SectionHeader("Details")
                }

                categoryFields

                Section {
                    TextField("Notes", text: $form.notes, axis: .vertical)
                        .lineLimit(3...8)
                } header: {
                    SectionHeader("Notes")
                }

                if let failure = failure {
                    Section {
                        Label {
                            Text(failure.message)
                                .font(AppFont.footnote)
                                .foregroundStyle(AppColor.error)
                                .fixedSize(horizontal: false, vertical: true)
                        } icon: {
                            Image(systemName: "exclamationmark.circle.fill")
                                .foregroundStyle(AppColor.error)
                        }
                        .accessibilityElement(children: .combine)
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
            Section {
                TextField("Username", text: $form.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Address", text: $form.address)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                passwordField(text: $form.password, label: "Password")
            } header: {
                SectionHeader("Login")
            }
        case .card:
            Section {
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
                        .font(AppFont.footnote)
                        .foregroundStyle(AppColor.strengthFair)
                }
            } header: {
                SectionHeader("Card")
            }
        case .bank:
            Section {
                TextField("Account number", text: $form.accountNumber)
                    .textInputAutocapitalization(.never)
                TextField("Bank name", text: $form.bankName)
                    .textInputAutocapitalization(.words)
                passwordField(text: $form.bankPassword, label: "Bank password")
            } header: {
                SectionHeader("Bank")
            }
            Section {
                ForEach(BankPasswordRules.Violation.allCases, id: \.self) { rule in
                    let broken = bankViolations.contains(rule)
                    Label {
                        Text(rule.message)
                            .font(AppFont.footnote)
                            .foregroundStyle(broken ? AppColor.error : AppColor.onSurfaceVariant)
                    } icon: {
                        Image(systemName: broken ? "xmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundStyle(broken ? AppColor.error : AppColor.primary)
                    }
                }
            } header: {
                SectionHeader("Password rules")
            }
        case .identity:
            Section {
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
            } header: {
                SectionHeader("Identity")
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
                    Image(systemName: "wand.and.rays")
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
