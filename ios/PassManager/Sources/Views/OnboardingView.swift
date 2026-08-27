import SwiftUI

/// Create the vault: passphrase, confirmation, strength meter, minimum 8.
struct OnboardingView: View {

    @EnvironmentObject private var session: AppSession
    @State private var passphrase: String = ""
    @State private var confirmation: String = ""

    private var strength: PasswordStrength {
        return PasswordStrength.evaluate(passphrase)
    }

    private var isTooShort: Bool {
        return passphrase.count < 8
    }

    private var isMismatched: Bool {
        return !confirmation.isEmpty && confirmation != passphrase
    }

    private var canCreate: Bool {
        return !isTooShort && !passphrase.isEmpty && passphrase == confirmation && !session.isBusy
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 12) {
                    ShieldMark(size: 88)
                    Text("PassManager")
                        .font(.largeTitle.bold())
                        .foregroundStyle(AppColor.onBackground)
                    // One line saying what the app IS, before the wall of
                    // instructions about the passphrase.
                    Text("Your passwords, encrypted on this device.")
                        .font(AppFont.rowSubtitle)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                    Text("Choose a master passphrase. It is the only way into this vault — it is never stored and cannot be recovered.")
                        .font(AppFont.footnote)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                        .padding(.top, 4)
                }
                .padding(.top, 44)

                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        SecureField("Master passphrase", text: $passphrase)
                            .textContentType(.newPassword)
                            .textFieldStyle(.roundedBorder)
                        if !passphrase.isEmpty {
                            StrengthBar(strength: strength)
                        }
                        Text(isTooShort ? "At least 8 characters" : "Longer is stronger.")
                            .font(AppFont.footnote)
                            .foregroundStyle(isTooShort && !passphrase.isEmpty
                                             ? AppColor.error
                                             : AppColor.onSurfaceVariant)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        SecureField("Confirm passphrase", text: $confirmation)
                            .textContentType(.newPassword)
                            .textFieldStyle(.roundedBorder)
                            // Return creates the vault, so the flow completes
                            // without reaching for a button the keyboard may be
                            // covering.
                            .submitLabel(.go)
                            .onSubmit(create)
                        if isMismatched {
                            Text("Passphrases do not match")
                                .font(AppFont.footnote)
                                .foregroundStyle(AppColor.error)
                        }
                    }
                }

                PrimaryActionButton(
                    title: "Create vault",
                    busyTitle: "Creating…",
                    isBusy: session.isBusy,
                    action: create
                )
                .disabled(!canCreate)

                // Vault creation can fail for reasons the user can act on — no
                // device passcode, secure storage refusing the write — and it
                // used to fail SILENTLY here, leaving the button looking dead
                // with nothing on screen to explain it. Found by a UI test that
                // could not get past this screen and could not say why.
                if let message = session.errorMessage, !message.isEmpty {
                    Label {
                        Text(message)
                            .font(AppFont.footnote)
                            .foregroundStyle(AppColor.error)
                            .fixedSize(horizontal: false, vertical: true)
                    } icon: {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundStyle(AppColor.error)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityElement(children: .combine)
                }

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 24)
        }
        .background(AppColor.background.ignoresSafeArea())
        // Lets the user swipe the keyboard away to reach the button, rather than
        // being stuck behind it on a short screen.
        .scrollDismissesKeyboard(.interactively)
    }

    private func create() {
        guard canCreate else {
            return
        }
        let value = passphrase
        Task { @MainActor in
            await session.createVault(passphrase: value)
        }
    }
}
