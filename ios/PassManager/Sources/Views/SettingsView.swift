import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var session: AppSession
    @Environment(\.dismiss) private var dismiss
    @State private var biometricsOn = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if session.biometricsAvailable {
                        PanelCard {
                            Toggle(isOn: $biometricsOn) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Unlock with \(session.biometricName)")
                                        .foregroundStyle(Palette.onSurface)
                                    Text("A second way into this vault. Your passphrase keeps working.")
                                        .font(.footnote)
                                        .foregroundStyle(Palette.onSurfaceVariant)
                                }
                            }
                            .tint(Palette.primary)
                            .padding(14)
                        }

                        // The part people are entitled to know before turning it on, said
                        // where the switch is rather than in a help page nobody opens.
                        Text("The key is kept in this device's keychain, never in a backup and never on another device. Adding or removing a face or fingerprint discards it, and your passphrase is the way back in.")
                            .font(.footnote)
                            .foregroundStyle(Palette.onSurfaceVariant)
                    } else {
                        PanelCard {
                            Text("This device has no face or fingerprint set up, so there is nothing to unlock with.")
                                .font(.subheadline)
                                .foregroundStyle(Palette.onSurfaceVariant)
                                .padding(14)
                        }
                    }

                    if let failure = session.failure {
                        Text(failure).font(.footnote).foregroundStyle(Palette.error)
                    }
                }
                .padding(16)
            }
            .background(Palette.background.ignoresSafeArea())
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear { biometricsOn = session.biometricsEnabled }
            .onChange(of: biometricsOn) { on in
                // Only act on a real change. Assigning the initial value in `onAppear` also
                // fires this, and enabling on appear would store the key without asking.
                guard on != session.biometricsEnabled else { return }
                if on { session.enableBiometrics() } else { session.disableBiometrics() }
                biometricsOn = session.biometricsEnabled
            }
        }
    }
}
