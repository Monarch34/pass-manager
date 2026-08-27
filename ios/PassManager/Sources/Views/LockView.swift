import SwiftUI

/// Passphrase unlock. The Face ID button is a placeholder until B4 wires up the
/// Keychain item behind `.biometryCurrentSet`.
struct LockView: View {

    @EnvironmentObject private var session: AppSession
    @State private var passphrase: String = ""
    @State private var didAutoPrompt = false

    private var canUnlock: Bool {
        return !passphrase.isEmpty && !session.isBusy
    }

    var body: some View {
        VStack(spacing: 28) {
            Spacer()

            VStack(spacing: 12) {
                ShieldMark(size: 76)
                Text("Vault locked")
                    .font(.title2.bold())
                    .foregroundStyle(AppColor.onBackground)
                Text(session.lockState == .coldLocked
                     ? "Enter your master passphrase to unlock."
                     : "Locked after inactivity.")
                    .font(AppFont.rowSubtitle)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(AppColor.onSurfaceVariant)
            }

            VStack(spacing: 14) {
                SecureField("Master passphrase", text: $passphrase)
                    .textContentType(.password)
                    .passphraseFieldChrome()
                    // Return unlocks, so the flow completes without reaching for
                    // a button the keyboard may be covering.
                    .submitLabel(.go)
                    .onSubmit(unlock)

                PrimaryActionButton(
                    title: "Unlock",
                    busyTitle: "Unlocking…",
                    isBusy: session.isBusy,
                    action: unlock
                )
                .disabled(!canUnlock)

                // Cold start always demands the passphrase; only a warm lock may
                // offer biometrics. Secondary action, so plain rather than
                // prominent — there is only ever one primary on this screen.
                if session.lockState.allowsBiometrics && session.biometricEnabled {
                    Button {
                        Task { @MainActor in
                            await session.unlockWithBiometrics()
                        }
                    } label: {
                        Label("Unlock with Face ID", systemImage: "faceid")
                            .frame(maxWidth: .infinity, minHeight: 30)
                    }
                    .buttonStyle(.borderless)
                    .controlSize(.large)
                    .disabled(session.isBusy)
                }
            }

            if let message = session.errorMessage, !message.isEmpty {
                Text(message)
                    .font(AppFont.footnote)
                    // A permanently invalidated enrolment is not something the
                    // user did wrong, so it is an instruction rather than an
                    // error shouted in red.
                    .foregroundStyle(session.biometricNeedsReEnrolment
                                     ? AppColor.onSurfaceVariant
                                     : AppColor.error)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 28)
        .background(AppColor.background.ignoresSafeArea())
        .onAppear(perform: autoPromptIfEnrolled)
    }

    /// Offer Face ID immediately on a warm lock, which is what the user expects
    /// from a password manager — but only once per appearance, so a cancel or a
    /// failed match does not re-present the sheet in a loop.
    private func autoPromptIfEnrolled() {
        guard !didAutoPrompt, session.lockState.allowsBiometrics, session.biometricEnabled else {
            return
        }
        didAutoPrompt = true
        Task { @MainActor in
            await session.unlockWithBiometrics()
        }
    }

    private func unlock() {
        guard canUnlock else {
            return
        }
        session.errorMessage = nil
        let value = passphrase
        passphrase = ""
        Task { @MainActor in
            await session.unlock(passphrase: value)
        }
    }
}
