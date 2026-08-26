import SwiftUI

struct SettingsView: View {

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var showingChangePassphrase = false

    /// Enrolling needs the raw vault key, so it can only ever happen while
    /// unlocked. The toggle reflects what the Keychain actually holds, not a
    /// stored boolean — the system can destroy the item behind our back.
    private var biometricBinding: Binding<Bool> {
        return Binding(
            get: { session.biometricEnabled },
            set: { wanted in
                if wanted {
                    session.enableBiometrics()
                } else {
                    session.disableBiometrics()
                }
            }
        )
    }

    /// Dismisses this sheet, THEN triggers the state change that presents the
    /// next one.
    ///
    /// UIKit will not present a sheet while another is still dismissing — the
    /// request is dropped and nothing happens, which looks exactly like a dead
    /// button. Doing both in the same runloop turn is the classic way to hit
    /// that. The transfer sheets are hosted by RootView (so an auto-lock cannot
    /// tear them down with this view), which makes this hand-off unavoidable, so
    /// it is sequenced explicitly rather than left to luck.
    private func dismissThen(_ action: @escaping () -> Void) {
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            action()
        }
    }

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

                    Toggle("Unlock with Face ID", isOn: biometricBinding)
                    if session.biometricNeedsReEnrolment {
                        Text("Face ID changed on this device, so the saved key was discarded. Turn it back on to enrol again.")
                            .font(.caption)
                            .foregroundStyle(AppColor.onSurfaceVariant)
                    } else {
                        Text("Stores the vault key in the Keychain behind Face ID. It is discarded automatically if the enrolled biometrics change, or if you change your passphrase.")
                            .font(.caption)
                            .foregroundStyle(AppColor.onSurfaceVariant)
                    }
                }

                Section {
                    if let reminder = session.backupStatus.message {
                        Label {
                            Text(reminder)
                                .font(.caption)
                                .foregroundStyle(AppColor.onSurfaceVariant)
                        } icon: {
                            Image(systemName: "exclamationmark.triangle")
                                .foregroundStyle(AppColor.strengthFair)
                        }
                    }

                    // Both of these only set state on the session. The sheets and
                    // the system pickers are hosted by RootView, because THIS
                    // view is torn down if the vault auto-locks mid-transfer and
                    // anything presented from here would go with it.
                    Button("Export vault…") {
                        dismissThen { session.beginExport() }
                    }
                    .disabled(session.itemCount == 0)

                    Button("Import vault…") {
                        dismissThen { session.requestImportPicker() }
                    }
                } header: {
                    Text("Transfer")
                } footer: {
                    Text("An export is one encrypted .pmvault file with its own passphrase. It is how this vault moves to another device, and the only way back if this one is lost.")
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
