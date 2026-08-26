import SwiftUI
import PassVaultCore
import PassVaultStorage

/// Collects a fresh export passphrase.
///
/// Only ever shown while unlocked — `TransferLockPolicy` rewinds to
/// `.awaitingUnlockForExport` if the vault locks, so a passphrase can never be
/// typed into a screen behind a lock the user has already been thrown out of.
struct ExportPassphraseView: View {

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var passphrase = ""
    @State private var confirmation = ""
    @State private var acknowledgedReuse = false

    private var rejection: ExportPassphrasePolicy.Rejection? {
        return ExportPassphrasePolicy.rejection(
            for: passphrase,
            confirmation: confirmation,
            masterPassphrase: nil
        )
    }

    private var canExport: Bool {
        return rejection == nil && acknowledgedReuse && !session.isBusy
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("Export passphrase", text: $passphrase)
                        .textContentType(.newPassword)
                    SecureField("Confirm passphrase", text: $confirmation)
                        .textContentType(.newPassword)
                    if !passphrase.isEmpty {
                        StrengthBar(strength: PasswordStrength.evaluate(passphrase))
                    }
                    if let rejection = rejection, !passphrase.isEmpty {
                        Text(rejection.message)
                            .font(.caption)
                            .foregroundStyle(AppColor.error)
                    }
                } header: {
                    Text("Protect this export")
                } footer: {
                    Text("The file is encrypted with this passphrase and nothing else. Lose it and the backup is unrecoverable — there is no reset.")
                }

                Section {
                    Toggle(isOn: $acknowledgedReuse) {
                        Text("This is not my master passphrase")
                            .font(.subheadline)
                    }
                } footer: {
                    Text("An export passphrase should be independent of your master passphrase. A backup file travels — to iCloud Drive, to a laptop, to wherever it is stored — and one leaked passphrase should not open both it and your device.")
                }
            }
            .navigationTitle("Export vault")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        session.cancelTransfer()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Continue") {
                        let value = passphrase
                        Task { @MainActor in
                            // On success the stage becomes `.exporting`, which is
                            // what brings up the system picker from RootView —
                            // no callback needed, and nothing to go stale if the
                            // vault locks in between.
                            if await session.prepareExportDocument(passphrase: value) {
                                dismiss()
                            }
                        }
                    }
                    .fontWeight(.semibold)
                    .disabled(!canExport)
                }
            }
        }
        .interactiveDismissDisabled(session.isBusy)
    }
}

/// Asks for the file's passphrase, then shows what applying it would do.
///
/// The summary is not decoration: `docs/FORMAT.md` requires the user be told how
/// many inserts and how many overwrites — with the titles being overwritten —
/// and be offered an add-only mode BEFORE anything is written.
struct ImportReviewView: View {

    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var passphrase = ""
    @State private var outcome: ImportOutcome?

    var body: some View {
        NavigationStack {
            Group {
                if let outcome = outcome {
                    resultView(outcome)
                } else if let plan = session.importPlan {
                    summaryView(plan)
                } else {
                    passphraseView
                }
            }
            .navigationTitle("Import vault")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(outcome == nil ? "Cancel" : "Done") {
                        session.cancelTransfer()
                        dismiss()
                    }
                }
            }
        }
        .interactiveDismissDisabled(session.isBusy)
    }

    // MARK: - Steps

    private var passphraseView: some View {
        Form {
            Section {
                SecureField("File passphrase", text: $passphrase)
                    .textContentType(.password)
                    .submitLabel(.go)
                    .onSubmit(plan)
            } header: {
                Text("Unlock the file")
            } footer: {
                Text("This is the passphrase the export was created with, which may not be your master passphrase.")
            }

            if let message = session.errorMessage, !message.isEmpty {
                Section {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(AppColor.error)
                }
            }

            Section {
                Button(action: plan) {
                    HStack {
                        if session.isBusy {
                            ProgressView()
                        }
                        Text(session.isBusy ? "Reading…" : "Read file")
                    }
                }
                .disabled(passphrase.isEmpty || session.isBusy)
            }
        }
    }

    private func summaryView(_ plan: ImportPlan) -> some View {
        Form {
            Section {
                row("New items", "\(plan.insertCount)", tint: AppColor.primary)
                row("Will be overwritten", "\(plan.overwriteCount)",
                    tint: plan.overwriteCount > 0 ? AppColor.strengthFair : AppColor.onSurfaceVariant)
                row("Skipped", "\(plan.skippedCount)", tint: AppColor.onSurfaceVariant)
            } header: {
                Text("What this will do")
            } footer: {
                Text("Nothing is ever deleted. Where both copies exist, the newer one wins.")
            }

            if !plan.overwrittenTitles.isEmpty {
                Section("These will be replaced") {
                    ForEach(Array(plan.overwrittenTitles.enumerated()), id: \.offset) { entry in
                        Text(entry.element.isEmpty ? "(untitled)" : entry.element)
                            .font(.subheadline)
                    }
                }
            }

            Section {
                Toggle("Add new items only", isOn: addOnlyBinding)
            } footer: {
                Text("Leaves every item you already have exactly as it is.")
            }

            Section {
                Button {
                    outcome = session.applyImport()
                } label: {
                    Text(plan.isEmpty ? "Nothing to import" : "Import \(plan.insertCount + plan.overwriteCount) items")
                        .fontWeight(.semibold)
                }
                .disabled(plan.isEmpty || session.isBusy)
            }
        }
    }

    private func resultView(_ outcome: ImportOutcome) -> some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundStyle(AppColor.primary)
            Text("Import complete")
                .font(.headline)
            Text("\(outcome.inserted) added, \(outcome.overwritten) updated, \(outcome.skipped) skipped.")
                .font(.subheadline)
                .foregroundStyle(AppColor.onSurfaceVariant)
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Pieces

    private var addOnlyBinding: Binding<Bool> {
        return Binding(
            get: { session.importMode == .addOnly },
            set: { addOnly in
                session.importMode = addOnly ? .addOnly : .merge
                // Re-planning repeats no decryption — the body is already in
                // hand and planning is pure.
                session.replanImport()
            }
        )
    }

    private func row(_ label: String, _ value: String, tint: Color) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
                .font(.body.weight(.semibold))
                .foregroundStyle(tint)
        }
    }

    private func plan() {
        let value = passphrase
        Task { @MainActor in
            _ = await session.planImport(passphrase: value)
        }
    }
}
