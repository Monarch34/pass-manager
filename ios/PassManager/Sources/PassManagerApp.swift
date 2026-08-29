import SwiftUI

@main
struct PassManagerApp: App {
    @StateObject private var session = AppSession()

    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(session)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var session: AppSession
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            switch session.phase {
            case .empty: CreateVaultView()
            case .locked: LockView()
            case .unlocked: VaultListView()
            }
        }
        .onChange(of: scenePhase) { phase in
            // Leaving the app locks it. The vault key lives in memory only while the app
            // is in front, so backgrounding it destroys the key rather than merely hiding
            // the screen.
            if phase == .background { session.lock() }
        }
    }
}

/// First run: there is no vault, so one is created.
struct CreateVaultView: View {
    @EnvironmentObject private var session: AppSession
    @State private var passphrase = ""
    @State private var confirmation = ""

    private var canCreate: Bool {
        passphrase.count >= 8 && passphrase == confirmation
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Create your vault")
                        .font(.largeTitle.weight(.semibold))
                    Text("Everything stays on this device. There is no account and no server.")
                        .font(.subheadline)
                        .foregroundStyle(Palette.onSurfaceVariant)
                }
                .padding(.top, 32)

                PanelField(label: "Passphrase", text: $passphrase, secure: true)
                PanelField(label: "Repeat it", text: $confirmation, secure: true)

                // Said plainly, once, before anything exists to lose. There is genuinely no
                // recovery: the key is derived from this and stored nowhere.
                Text("If you forget this passphrase, the vault cannot be opened by anyone, including us. There is no reset.")
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)

                if passphrase.count > 0 && passphrase.count < 8 {
                    Text("At least 8 characters.")
                        .font(.footnote)
                        .foregroundStyle(Palette.error)
                } else if !confirmation.isEmpty && passphrase != confirmation {
                    Text("The two do not match.")
                        .font(.footnote)
                        .foregroundStyle(Palette.error)
                }

                PillButton(title: "Create vault", enabled: canCreate) {
                    session.create(passphrase: passphrase)
                    passphrase = ""
                    confirmation = ""
                }

                if let failure = session.failure {
                    Text(failure).font(.footnote).foregroundStyle(Palette.error)
                }
            }
            .padding(20)
        }
        .background(Palette.background.ignoresSafeArea())
    }
}

struct LockView: View {
    @EnvironmentObject private var session: AppSession
    @State private var passphrase = ""
    @State private var confirmingReset = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Palette.primary)
                    .padding(.top, 48)

                Text("Vault locked")
                    .font(.largeTitle.weight(.semibold))

                PanelField(label: "Passphrase", text: $passphrase, secure: true)

                PillButton(title: "Unlock", enabled: !passphrase.isEmpty) {
                    session.unlock(passphrase: passphrase)
                    passphrase = ""
                }

                if let failure = session.failure {
                    Text(failure).font(.footnote).foregroundStyle(Palette.error)
                }

                Button("Forgotten passphrase") { confirmingReset = true }
                    .font(.footnote)
                    .foregroundStyle(Palette.onSurfaceVariant)
            }
            .padding(20)
        }
        .background(Palette.background.ignoresSafeArea())
        .alert("Delete this vault?", isPresented: $confirmingReset) {
            Button("Delete", role: .destructive) { session.startOver() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("A vault cannot be opened without its passphrase. Deleting it and starting again is the only option, and everything in it is lost.")
        }
    }
}
