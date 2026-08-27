import SwiftUI
import PassVaultCore

/// The generator as a SHEET, opened from the add/edit form to fill a password
/// field. Keeps the Cancel/Use pair, because here it is a picker with a result.
struct GeneratorView: View {

    /// When the generator is opened from a form, the form's category constrains
    /// what it may produce — otherwise it would hand the bank form a 16-character
    /// password the form then rejects on arrival.
    let constraintCategory: ItemCategory?
    /// `nil` when opened standalone.
    let onUse: ((String) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var password: String = ""

    var body: some View {
        NavigationStack {
            GeneratorForm(constraintCategory: constraintCategory, password: $password)
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
    }
}

/// The generator as a TAB. No dismiss chrome — a tab is a place, not a modal.
struct GeneratorTabView: View {

    @State private var password: String = ""

    var body: some View {
        NavigationStack {
            GeneratorForm(constraintCategory: nil, password: $password)
                .navigationTitle("Generator")
                .navigationBarTitleDisplayMode(.large)
        }
    }
}

/// The shared body. Owns the options; the produced password is lifted to the
/// wrapper so the sheet's "Use" button can read it.
struct GeneratorForm: View {

    let constraintCategory: ItemCategory?
    @Binding var password: String

    @State private var options = PasswordGenerator.Options()
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

    private var strength: PasswordStrength {
        return PasswordStrength.evaluate(password)
    }

    var body: some View {
        Form {
            Section {
                hero
            }

            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Length")
                            .font(AppFont.fieldValue)
                            .foregroundStyle(AppColor.onSurface)
                        Spacer()
                        // Monospaced digits: without them the number changes
                        // width as it passes 9 → 10 and the whole label twitches
                        // while the slider is being dragged.
                        Text("\(options.length)")
                            .font(AppFont.steadyDigits)
                            .foregroundStyle(AppColor.onSurfaceVariant)
                    }
                    Slider(value: lengthBinding, in: lengthRange, step: 1)
                        .accessibilityLabel("Password length")
                        .accessibilityValue("\(options.length) characters")
                }
                .padding(.vertical, 2)
            } header: {
                SectionHeader("Password length")
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
                SectionHeader("Character sets")
            } footer: {
                Text("At least one set stays on.")
            }
        }
        .scrollContentBackground(.hidden)
        .background(AppColor.background)
        .onAppear {
            applyConstraint()
            if password.isEmpty {
                regenerate()
            }
        }
    }

    // MARK: - Hero

    /// The password is the reason this screen exists, so it is the largest thing
    /// on it.
    ///
    /// Character classes are tinted because the expensive mistakes when reading a
    /// generated password back are at the class boundaries — a digit taken for a
    /// letter, a symbol dropped. Password managers colour these for that reason,
    /// not for decoration.
    private var hero: some View {
        VStack(alignment: .leading, spacing: 14) {
            Group {
                if password.isEmpty {
                    Text("Generating…")
                        .font(AppFont.heroPassword)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                } else {
                    SecretText.colorized(password)
                        .font(AppFont.heroPassword)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .textSelection(.enabled)
            .accessibilityLabel("Generated password")
            .accessibilityValue(password)

            VStack(alignment: .leading, spacing: 6) {
                StrengthBar(strength: strength, showsLabel: false)
                // One line, not two stacked blocks: these are two readings of
                // the same thing and belong side by side.
                HStack(alignment: .firstTextBaseline) {
                    Text(strength.label)
                        .font(AppFont.footnote)
                        .foregroundStyle(strength.color)
                    Spacer(minLength: 8)
                    Text("≈ \(PasswordGenerator.entropyBits(options)) bits")
                        .font(AppFont.entropyValue)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }
            }

            // Text only, no glyphs.
            //
            // "Regenerate" plus an icon does not fit in half the screen once
            // `.controlSize(.large)` has taken its padding: with a wrapping
            // label it broke as "Regen-/erate", and with `.lineLimit(1)` it
            // truncated to "Regenera…" — `minimumScaleFactor` does not reach
            // the text inside a `Label`. Dropping the glyphs gives the words the
            // whole button, and costs nothing: the copy confirmation below
            // already carries a tick.
            HStack(spacing: 10) {
                Button(action: regenerate) {
                    Text("Regenerate")
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: 30)
                }
                .buttonStyle(.bordered)

                Button(action: copyPassword) {
                    Text(copied ? "Copied" : "Copy")
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: 30)
                }
                .buttonStyle(.borderedProminent)
                .disabled(password.isEmpty)
            }
            .controlSize(.large)

            if copied {
                CopyConfirmation()
            }
        }
        .padding(.vertical, 6)
    }

    // MARK: - Pieces

    private func classToggle(_ label: String, isOn: Binding<Bool>) -> some View {
        // The last enabled class is locked ON rather than silently refusing the
        // tap: a control that ignores you looks broken, a disabled one explains
        // itself.
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

    private func copyPassword() {
        Clipboard.copySecret(password)
        copied = true
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            copied = false
        }
    }
}
