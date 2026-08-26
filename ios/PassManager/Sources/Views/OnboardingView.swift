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
                VStack(spacing: 16) {
                    ShieldMark(size: 88)
                    Text("PassManager")
                        .font(.largeTitle.bold())
                        .foregroundStyle(AppColor.onBackground)
                    Text("Choose a master passphrase. It is the only way into this vault — it is never stored and cannot be recovered.")
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }
                .padding(.top, 48)

                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        SecureField("Master passphrase", text: $passphrase)
                            .textContentType(.newPassword)
                            .textFieldStyle(.roundedBorder)
                        if !passphrase.isEmpty {
                            StrengthBar(strength: strength)
                        }
                        Text(isTooShort ? "At least 8 characters" : "Longer is stronger.")
                            .font(.caption)
                            .foregroundStyle(isTooShort && !passphrase.isEmpty
                                             ? AppColor.error
                                             : AppColor.onSurfaceVariant)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        SecureField("Confirm passphrase", text: $confirmation)
                            .textContentType(.newPassword)
                            .textFieldStyle(.roundedBorder)
                        if isMismatched {
                            Text("Passphrases do not match")
                                .font(.caption)
                                .foregroundStyle(AppColor.error)
                        }
                    }
                }

                Button {
                    let value = passphrase
                    Task { @MainActor in
                        await session.createVault(passphrase: value)
                    }
                } label: {
                    HStack {
                        if session.isBusy {
                            ProgressView()
                                .tint(AppColor.onPrimary)
                        }
                        Text(session.isBusy ? "Creating…" : "Create vault")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(canCreate ? AppColor.primary : AppColor.outlineVariant)
                    .foregroundStyle(canCreate ? AppColor.onPrimary : AppColor.onSurfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(!canCreate)

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 24)
        }
        .background(AppColor.background.ignoresSafeArea())
    }
}
