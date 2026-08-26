import SwiftUI

/// Passphrase unlock. The Face ID button is a placeholder until B4 wires up the
/// Keychain item behind `.biometryCurrentSet`.
struct LockView: View {

    @EnvironmentObject private var session: AppSession
    @State private var passphrase: String = ""

    private var canUnlock: Bool {
        return !passphrase.isEmpty && !session.isBusy
    }

    var body: some View {
        VStack(spacing: 28) {
            Spacer()

            VStack(spacing: 14) {
                ShieldMark(size: 76)
                Text("Vault locked")
                    .font(.title2.bold())
                    .foregroundStyle(AppColor.onBackground)
                Text(session.lockState == .coldLocked
                     ? "Enter your master passphrase to unlock."
                     : "Locked after inactivity.")
                    .font(.subheadline)
                    .foregroundStyle(AppColor.onSurfaceVariant)
            }

            VStack(spacing: 12) {
                SecureField("Master passphrase", text: $passphrase)
                    .textContentType(.password)
                    .textFieldStyle(.roundedBorder)
                    .submitLabel(.go)
                    .onSubmit(unlock)

                Button(action: unlock) {
                    HStack {
                        if session.isBusy {
                            ProgressView()
                                .tint(AppColor.onPrimary)
                        }
                        Text(session.isBusy ? "Unlocking…" : "Unlock")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(canUnlock ? AppColor.primary : AppColor.outlineVariant)
                    .foregroundStyle(canUnlock ? AppColor.onPrimary : AppColor.onSurfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(!canUnlock)

                // Cold start always demands the passphrase; only a warm lock may
                // offer biometrics.
                if session.lockState.allowsBiometrics && session.biometricEnabled {
                    Button {
                        // B4: LAContext + the .biometryCurrentSet Keychain item.
                    } label: {
                        Label("Unlock with Face ID", systemImage: "faceid")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .disabled(true)
                }
            }

            if let message = session.errorMessage {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(AppColor.error)
                    .multilineTextAlignment(.center)
            }

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 28)
        .background(AppColor.background.ignoresSafeArea())
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
