import Foundation
import PassManagerKit
import SwiftUI

/// The whole of the app's state: whether the vault is open, and what is in it.
///
/// The vault key is held here for as long as the vault is unlocked and destroyed the
/// moment it is not. That is what makes saving an edit possible without asking for the
/// passphrase again — the key was never derived from it a second time.
@MainActor
final class AppSession: ObservableObject {

    enum Phase: Equatable {
        /// No vault on this device yet.
        case empty
        /// A vault exists and is sealed.
        case locked
        /// Open, with the key in memory.
        case unlocked
    }

    @Published private(set) var phase: Phase
    @Published private(set) var items: [VaultItem] = []
    @Published var failure: String?

    /// Held only while unlocked. Destroying it is what locking means.
    private var vaultKey: Secret?

    /// Whether a second way in exists. Read from a plain flag rather than by probing the
    /// Keychain, because probing a biometry-protected item would prompt for a face just to
    /// decide whether to draw a button.
    var biometricsEnabled: Bool { BiometricVaultKey.isEnabled }
    var biometricsAvailable: Bool { BiometricVaultKey.isAvailable }
    var biometricName: String { BiometricVaultKey.name }

    init() {
        phase = VaultStore.exists ? .locked : .empty
    }

    // MARK: - Opening and closing

    func create(passphrase: String) {
        do {
            let file = VaultKit.create(items: [], passphrase: passphrase)
            try VaultStore.write(file)
            // Opening what was just written rather than assuming: this is the only place
            // the reader and the writer are checked against each other on a real device.
            unlock(passphrase: passphrase)
        } catch {
            failure = error.localizedDescription
        }
    }

    func unlock(passphrase: String) {
        do {
            let file = try VaultStore.read()
            switch VaultKit.open(file, passphrase: passphrase) {
            case .success(let opened):
                vaultKey?.destroy()
                vaultKey = opened.key
                items = opened.items
                failure = nil
                phase = .unlocked
            case .failure(let reason):
                failure = reason.message
            }
        } catch {
            failure = error.localizedDescription
        }
    }

    /// Opens the vault with the key the Keychain holds, without a passphrase.
    ///
    /// The file is still parsed and its body still authenticated — this replaces only the
    /// derivation, not the verification. A tampered vault fails here exactly as it would on
    /// the passphrase path.
    func unlockWithBiometrics() {
        switch BiometricVaultKey.load() {
        case .success(let key):
            do {
                let file = try VaultStore.read()
                guard let sealed = PmVault.shared.parse(bytes: file.kotlinBytes) as? VaultParseSealed,
                      let contents = sealed.openWithVaultKey(vaultKey: key) else {
                    key.destroy()
                    // The stored key no longer opens this vault, so it is worse than
                    // useless: it would fail on every future attempt while looking like an
                    // option. This happens if the vault was replaced behind the app's back.
                    BiometricVaultKey.remove()
                    failure = "The saved key does not open this vault. Use your passphrase."
                    return
                }
                vaultKey?.destroy()
                vaultKey = key
                items = contents.items
                failure = nil
                phase = .unlocked
            } catch {
                key.destroy()
                failure = error.localizedDescription
            }
        case .failure(let reason):
            // A cancelled prompt is not a failure worth reporting; the user closed it.
            if reason != .cancelled { failure = reason.message }
        }
    }

    /// Stores the open vault's key behind biometry. Only reachable while unlocked, because
    /// there is nothing to store otherwise.
    func enableBiometrics() {
        guard let key = vaultKey else { return }
        if case .failure(let reason) = BiometricVaultKey.store(key) {
            failure = reason.message
        }
    }

    func disableBiometrics() {
        BiometricVaultKey.remove()
    }

    func lock() {
        vaultKey?.destroy()
        vaultKey = nil
        items = []
        failure = nil
        phase = .locked
    }

    /// Deletes the vault. The only answer to a forgotten passphrase, and it is a real one:
    /// nothing in this app can open a vault without its key, so pretending otherwise would
    /// be worse than saying so.
    func startOver() {
        vaultKey?.destroy()
        vaultKey = nil
        items = []
        VaultStore.destroy()
        // The stored key would otherwise outlive the vault it opens — a copy of a key to a
        // door that no longer exists, sitting in the Keychain until something reused it.
        BiometricVaultKey.remove()
        failure = nil
        phase = .empty
    }

    // MARK: - Editing

    func save(_ item: VaultItem) {
        var updated = items
        if let index = updated.firstIndex(where: { $0.id == item.id }) {
            updated[index] = item
        } else {
            updated.append(item)
        }
        persist(updated)
    }

    func delete(_ item: VaultItem) {
        persist(items.filter { $0.id != item.id })
    }

    private func persist(_ updated: [VaultItem]) {
        guard let key = vaultKey else {
            failure = "The vault is locked."
            return
        }
        do {
            let current = try VaultStore.read()
            guard let rewritten = VaultKit.rewrite(current, key: key, items: updated) else {
                failure = "The vault on disk could not be read back."
                return
            }
            try VaultStore.write(rewritten)
            items = updated
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }
}
