import SwiftUI

struct SettingsView: View {

    @EnvironmentObject private var session: AppSession

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
                    SectionHeader("Security")
                } footer: {
                    // One sentence. These footers used to be paragraphs set
                    // nearly as loud as the settings themselves, which made the
                    // screen read as prose with controls buried in it.
                    Text("The vault locks this long after the app goes to the background.")
                }

                Section {
                    Button("Change master passphrase") {
                        showingChangePassphrase = true
                    }

                    Toggle("Unlock with Face ID", isOn: biometricBinding)
                } footer: {
                    Text(session.biometricNeedsReEnrolment
                         ? "Face ID changed, so the saved key was discarded — turn it back on to enrol again."
                         : "The saved key is discarded if your biometrics or passphrase change.")
                }

                // Headed "Network" rather than "Preferences", which is what
                // Android calls the section it sits in. The header is doing real
                // work here: this toggle is the ONLY thing in the app that can
                // cause a request, so a section named for that says something
                // true about everything else on the screen.
                Section {
                    Toggle("Site icons", isOn: $session.useSiteIcons)
                } header: {
                    SectionHeader("Network")
                } footer: {
                    // The promise, in the place where the user is deciding. Both
                    // halves carry the meaning of Android's
                    // settings_site_icons_subtitle_on / _off, compressed to the
                    // one sentence per footer this screen settled on: what leaves
                    // the device, where it goes, and that it goes nowhere else.
                    Text(session.useSiteIcons
                         ? "The site's domain — nothing else — goes to Google's icon service at t0.gstatic.com, the only host this app ever contacts, and redirects away from it are refused."
                         : "No icon is downloaded and no network request of any kind is made; entries show their category.")
                }

                Section {
                    if let reminder = session.backupStatus.message {
                        WarningRow(message: reminder)
                    }

                    // Both of these only set state on the session. The sheets and
                    // the system pickers are hosted by RootView, because THIS
                    // view is torn down if the vault auto-locks mid-transfer and
                    // anything presented from here would go with it.
                    //
                    // Settings is a tab rather than a sheet now, so there is
                    // nothing to dismiss first and the old 0.4s hand-off delay is
                    // gone with it.
                    Button("Export vault…") {
                        session.beginExport()
                    }
                    .disabled(session.itemCount == 0)

                    Button("Import vault…") {
                        session.requestImportPicker()
                    }
                } header: {
                    SectionHeader("Transfer")
                } footer: {
                    Text("An export is one encrypted .pmvault file with its own passphrase.")
                }

                Section {
                    lockNowRow
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppColor.background)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showingChangePassphrase) {
                ChangePassphraseView()
            }
        }
    }

    /// Glyph and label both pinned to `AppColor.error`.
    ///
    /// `Button(role: .destructive)` reddens only the label; the lock icon kept
    /// inheriting the teal accent, so the row read as two different intentions
    /// at once.
    private var lockNowRow: some View {
        Button(role: .destructive) {
            session.lock(to: .warmLocked)
        } label: {
            Label {
                Text("Lock now")
                    .foregroundStyle(AppColor.error)
            } icon: {
                Image(systemName: "lock.fill")
                    .foregroundStyle(AppColor.error)
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
                Section {
                    SecureField("Current passphrase", text: $current)
                        .textContentType(.password)
                } header: {
                    SectionHeader("Current")
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
                    SectionHeader("New")
                } footer: {
                    Text("At least 8 characters. Face ID must be re-enrolled afterwards.")
                }

                if failed {
                    Section {
                        Text("Current passphrase is wrong.")
                            .font(AppFont.footnote)
                            .foregroundStyle(AppColor.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppColor.background)
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
