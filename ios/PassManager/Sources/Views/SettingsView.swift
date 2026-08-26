import SwiftUI

struct SettingsView: View {

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var showingChangePassphrase = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Auto-lock after", selection: $session.autoLockTimeout) {
                        ForEach(AutoLockTimeout.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }
                } header: {
                    Text("Security")
                } footer: {
                    Text("The vault locks this long after the app goes to the background. Locking discards the key held in memory.")
                }

                Section {
                    Button("Change master passphrase") {
                        showingChangePassphrase = true
                    }

                    Toggle("Unlock with Face ID", isOn: $session.biometricEnabled)
                        .disabled(true)
                    Text("Face ID unlock arrives with the Keychain work in B4.")
                        .font(.caption)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }

                Section {
                    Button("Export vault…") {}
                        .disabled(true)
                    Button("Import vault…") {}
                        .disabled(true)
                } header: {
                    Text("Transfer")
                } footer: {
                    Text("Encrypted .pmvault export and import are wired up in B5. The container and its merge rules are already implemented and tested.")
                }

                Section {
                    Button(role: .destructive) {
                        session.lock(to: .warmLocked)
                        dismiss()
                    } label: {
                        Label("Lock now", systemImage: "lock")
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showingChangePassphrase) {
                ChangePassphraseView()
            }
        }
    }
}

struct ChangePassphraseView: View {

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var current: String = ""
    @State private var next: String = ""
    @State private var confirmation: String = ""
    @State private var failed = false

    private var canSubmit: Bool {
        return !current.isEmpty
            && next.count >= 8
            && next == confirmation
            && !session.isBusy
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Current") {
                    SecureField("Current passphrase", text: $current)
                        .textContentType(.password)
                }

                Section {
                    SecureField("New passphrase", text: $next)
                        .textContentType(.newPassword)
                    SecureField("Confirm new passphrase", text: $confirmation)
                        .textContentType(.newPassword)
                    if !next.isEmpty {
                        StrengthBar(strength: PasswordStrength.evaluate(next))
                    }
                } header: {
                    Text("New")
                } footer: {
                    Text("At least 8 characters. Changing the passphrase re-wraps the vault key with a fresh salt; your items are not re-encrypted, and Face ID must be re-enrolled.")
                }

                if failed {
                    Section {
                        Text("Current passphrase is wrong.")
                            .font(.footnote)
                            .foregroundStyle(AppColor.error)
                    }
                }
            }
            .navigationTitle("Change passphrase")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        submit()
                    }
                    .fontWeight(.semibold)
                    .disabled(!canSubmit)
                }
            }
        }
    }

    private func submit() {
        let currentValue = current
        let newValue = next
        // Explicitly @MainActor: this closure resumes after an await and then
        // touches `dismiss()` and `@State`, both of which belong on the main
        // actor.
        Task { @MainActor in
            let didChange = await session.changePassphrase(current: currentValue, new: newValue)
            if didChange {
                dismiss()
            } else {
                failed = true
            }
        }
    }
}
