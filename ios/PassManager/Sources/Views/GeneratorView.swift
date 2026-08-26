import SwiftUI
import PassVaultCore

struct GeneratorView: View {

    /// When the generator is opened from a form, the form's category constrains
    /// what it may produce — otherwise it would hand the bank form a 16-character
    /// password the form then rejects on arrival.
    let constraintCategory: ItemCategory?
    /// `nil` when opened standalone from the vault list.
    let onUse: ((String) -> Void)?

    @Environment(\.dismiss) private var dismiss

    @State private var options = PasswordGenerator.Options()
    @State private var password: String = ""
    @State private var copied = false

    private var isBankConstrained: Bool {
        return constraintCategory == .bank
    }

    private var lengthRange: ClosedRange<Double> {
        if isBankConstrained {
            return Double(max(BankPasswordRules.minLength, PasswordGenerator.minLength))
                ... Double(BankPasswordRules.maxLength)
        }
        return Double(PasswordGenerator.minLength)...Double(PasswordGenerator.maxLength)
    }

    /// The last enabled class cannot be switched off — Android enforces the same
    /// rule, and without it the generator would have nothing to draw from.
    private var enabledClassCount: Int {
        var count = 0
        if options.includeUppercase { count += 1 }
        if options.includeLowercase { count += 1 }
        if options.includeDigits { count += 1 }
        if options.includeSymbols { count += 1 }
        return count
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(password.isEmpty ? "Generate a password below" : password)
                            .font(.system(.title3, design: .monospaced))
                            .foregroundStyle(AppColor.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)

                        StrengthBar(strength: PasswordStrength.evaluate(password))

                        Text("≈ \(PasswordGenerator.entropyBits(options)) bits")
                            .font(.caption)
                            .foregroundStyle(AppColor.onSurfaceVariant)

                        HStack(spacing: 10) {
                            Button(action: regenerate) {
                                Label("Regenerate", systemImage: "arrow.clockwise")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AppColor.surfaceVariant)
                                    .foregroundStyle(AppColor.onSurface)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)

                            Button {
                                Clipboard.copySecret(password)
                                copied = true
                            } label: {
                                Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AppColor.surfaceVariant)
                                    .foregroundStyle(AppColor.onSurface)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(password.isEmpty)
                        }
                    }
                    .padding(.vertical, 6)
                }

                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Length: \(options.length)")
                            .font(.subheadline)
                            .foregroundStyle(AppColor.onSurface)
                        Slider(
                            value: lengthBinding,
                            in: lengthRange,
                            step: 1
                        )
                        .accessibilityValue("\(options.length) characters")
                    }
                } header: {
                    Text("Password length")
                } footer: {
                    Text(isBankConstrained
                         ? "Bank passwords must be \(BankPasswordRules.minLength)–\(BankPasswordRules.maxLength) characters."
                         : "Longer is stronger. 8–64 characters.")
                }

                Section {
                    classToggle("Uppercase A-Z", isOn: $options.includeUppercase)
                    classToggle("Lowercase a-z", isOn: $options.includeLowercase)
                    classToggle("Digits 0-9", isOn: $options.includeDigits)
                    classToggle("Symbols", isOn: $options.includeSymbols)
                } header: {
                    Text("Character sets")
                } footer: {
                    Text("Keep at least one enabled.")
                }
            }
            .navigationTitle("Generator")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(onUse == nil ? "Done" : "Cancel") {
                        dismiss()
                    }
                }
                if onUse != nil {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Use") {
                            onUse?(password)
                            dismiss()
                        }
                        .fontWeight(.semibold)
                        .disabled(password.isEmpty)
                    }
                }
            }
        }
        .onAppear {
            applyConstraint()
            regenerate()
        }
    }

    // MARK: - Pieces

    private func classToggle(_ label: String, isOn: Binding<Bool>) -> some View {
        // The last enabled class is locked on rather than merely refused, so the
        // control shows the rule instead of silently ignoring a tap.
        let isLastEnabled = isOn.wrappedValue && enabledClassCount == 1
        return Toggle(label, isOn: Binding(
            get: { isOn.wrappedValue },
            set: { newValue in
                if !newValue && enabledClassCount == 1 {
                    return
                }
                isOn.wrappedValue = newValue
                regenerate()
            }
        ))
        .disabled(isLastEnabled)
    }

    private var lengthBinding: Binding<Double> {
        return Binding(
            get: { Double(options.length) },
            set: { newValue in
                let rounded = Int(newValue.rounded())
                if rounded != options.length {
                    options.length = rounded
                    regenerate()
                }
            }
        )
    }

    // MARK: - Actions

    /// Pulls length and character sets inside the category's rules BEFORE
    /// anything is generated, so a setting the category forbids never reaches the
    /// form. Mirrors Kotlin's `conformed()`.
    private func applyConstraint() {
        guard isBankConstrained else {
            return
        }
        let lower = max(BankPasswordRules.minLength, PasswordGenerator.minLength)
        let upper = BankPasswordRules.maxLength
        options.length = min(max(options.length, lower), upper)
        options.includeUppercase = true
        options.includeLowercase = true
        options.includeDigits = true
    }

    private func regenerate() {
        copied = false
        if let generated = PasswordGenerator.generate(options, constrainedTo: constraintCategory) {
            password = generated
        }
    }
}
