import Foundation
import SwiftUI
import PassVaultCore
import PassVaultStorage

/// The one place that owns the vault key, the lock state and the database.
///
/// REFRESH MODEL: explicit reload, not GRDB `ValueObservation`.
///
/// Every write in this app goes through this object — there is no second writer,
/// no app extension and no background sync — so "reload after a mutation, and on
/// unlock" is complete by construction. `ValueObservation` would mean plumbing
/// GRDB into the app target purely to observe a database only this object
/// touches, and would need a scheduler story for the decrypt pass that follows
/// each change. If a share extension or desktop sync ever writes to this file,
/// this is the decision to revisit.
@MainActor
final class AppSession: ObservableObject {

    @Published private(set) var lockState: LockState = .needsSetup
    @Published private(set) var headers: [VaultItemHeaderRow] = []
    @Published private(set) var isBusy: Bool = false
    @Published var errorMessage: String?

    @Published var autoLockTimeout: AutoLockTimeout = .default {
        didSet {
            UserDefaults.standard.set(autoLockTimeout.rawValue, forKey: Self.timeoutKey)
        }
    }

    /// Placeholder until B4 wires up the Keychain and `LAContext`.
    @Published var biometricEnabled: Bool = false

    private static let timeoutKey = "autoLockTimeoutSeconds"

    private var store: VaultStore?
    private var vaultKey: Data?
    private let headerCache = VaultHeaderCache()
    private var backgroundedAt: Date?

    /// Non-nil only while unlocked. Views use this to decrypt on demand.
    var currentVaultKey: Data? {
        return vaultKey
    }

    var decryptedTitles: [String: String] {
        return headerCache.titles
    }

    // MARK: - Lifecycle

    init(store: VaultStore? = nil) {
        let stored = UserDefaults.standard.integer(forKey: Self.timeoutKey)
        if stored > 0 {
            self.autoLockTimeout = AutoLockTimeout.from(rawValue: stored)
        }
        if let store = store {
            self.store = store
        }
    }

    func bootstrap() {
        if store == nil {
            do {
                store = try VaultStore.open(path: Self.databasePath())
                Self.protectDatabaseFile()
            } catch {
                errorMessage = "Could not open the vault database."
                return
            }
        }
        var hasVault = false
        if let store = store {
            // `try?` on a throwing function returning an Optional flattens, so
            // this is exactly "a metadata row exists and could be read".
            hasVault = (try? store.metadata()) != nil
        }
        lockState = LockStateMachine.stateAtLaunch(hasVault: hasVault)
    }

    static func databasePath() -> String {
        let manager = FileManager.default
        let base = manager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        try? manager.createDirectory(at: base, withIntermediateDirectories: true)
        return base.appendingPathComponent("passmanager.sqlite").path
    }

    /// Keep the file unreadable until the device has been unlocked once since
    /// boot. The vault key itself never lands here in the clear, but the
    /// ciphertext still does not need to be readable to a device that has never
    /// been unlocked.
    private static func protectDatabaseFile() {
        let path = databasePath()
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: path
        )
    }

    // MARK: - Scene phase

    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .background:
            if lockState == .unlocked && backgroundedAt == nil {
                backgroundedAt = Date()
            }
        case .inactive:
            // Deliberately NOT the start of the timer. `docs/IOS_PARITY.md` says
            // background transitions, and `.inactive` also fires for the app
            // switcher and for a system auth prompt — starting the clock there
            // would punish the user for the biometric sheet B4 is about to add.
            break
        case .active:
            let next = LockStateMachine.stateOnForeground(
                current: lockState,
                backgroundedAt: backgroundedAt,
                now: Date(),
                timeout: autoLockTimeout.seconds
            )
            backgroundedAt = nil
            if next != lockState {
                if next == .warmLocked {
                    lock(to: .warmLocked)
                } else {
                    lockState = next
                }
            }
        @unknown default:
            break
        }
    }

    // MARK: - Locking

    /// Locking is zeroing the in-memory key. Nothing on disk changes.
    func lock(to state: LockState = .warmLocked) {
        if var key = vaultKey {
            // Drop the property's reference FIRST. `Data` is copy-on-write, so
            // zeroing while two references exist would wipe a fresh copy and
            // leave the real buffer intact.
            vaultKey = nil
            SecureBytes.zero(&key)
        }
        headerCache.clear()
        headers = []
        lockState = state
    }

    // MARK: - Setup and unlock

    func createVault(passphrase: String) async {
        guard let store = store else {
            return
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let metadata = try await Task.detached(priority: .userInitiated) {
                try VaultCore.createVault(passphrase: passphrase)
            }.value
            try store.saveMetadata(StoredVaultMetadata(metadata))
            Self.protectDatabaseFile()
            await unlock(passphrase: passphrase)
        } catch {
            errorMessage = "Could not create the vault."
        }
    }

    func unlock(passphrase: String) async {
        guard let store = store else {
            return
        }
        guard let stored = try? store.metadata() else {
            errorMessage = "This vault has no metadata."
            return
        }
        isBusy = true
        defer { isBusy = false }
        let core = stored.coreMetadata
        do {
            let key = try await Task.detached(priority: .userInitiated) {
                try VaultCore.unlock(passphrase: passphrase, metadata: core)
            }.value
            vaultKey = key
            lockState = .unlocked
            backgroundedAt = nil
            reload()
        } catch {
            // GCM cannot distinguish a wrong passphrase from corruption, and
            // neither does the message.
            errorMessage = "Wrong passphrase."
        }
    }

    func changePassphrase(current: String, new: String) async -> Bool {
        guard let store = store, let stored = try? store.metadata() else {
            return false
        }
        isBusy = true
        defer { isBusy = false }
        let core = stored.coreMetadata
        do {
            let updated = try await Task.detached(priority: .userInitiated) {
                try VaultCore.changePassphrase(
                    currentPassphrase: current,
                    newPassphrase: new,
                    metadata: core
                )
            }.value
            var next = StoredVaultMetadata(updated)
            // A passphrase change invalidates biometric unlock; it must be
            // re-enrolled. B4 will also delete the Keychain item here.
            next.biometricEnabled = false
            next.biometricWrappedKey = nil
            next.biometricWrapperIv = nil
            try store.saveMetadata(next)
            biometricEnabled = false
            return true
        } catch {
            errorMessage = "Current passphrase is wrong."
            return false
        }
    }

    // MARK: - Items

    func reload() {
        guard let store = store, let vaultKey = vaultKey else {
            headers = []
            return
        }
        do {
            let rows = try store.headers()
            _ = headerCache.refresh(headers: rows, vaultKey: vaultKey)
            headers = rows
        } catch {
            errorMessage = "Could not read the vault."
        }
    }

    func filteredHeaders(query: String, category: ItemCategory?) -> [VaultItemHeaderRow] {
        return VaultSearch.filter(
            headers: headers,
            query: query,
            cache: headerCache,
            category: category
        )
    }

    func title(for id: String) -> String {
        return headerCache.title(for: id)
    }

    func subtitle(for id: String) -> String {
        return headerCache.address(for: id)
    }

    func payload(for id: String) -> ItemPayload? {
        guard let store = store, let vaultKey = vaultKey else {
            return nil
        }
        guard let row = try? store.item(id: id) else {
            return nil
        }
        return try? ItemCrypto.decryptPayload(row: row, vaultKey: vaultKey)
    }

    /// Insert a brand new item. `createdAt` and `updatedAt` are both "now" here —
    /// which is correct for something the user just typed, and is exactly why the
    /// store takes them separately rather than deriving one from the other.
    func createItem(_ payload: ItemPayload) {
        guard let store = store, let vaultKey = vaultKey else {
            return
        }
        let now = Self.nowMillis()
        do {
            let row = try ItemCrypto.makeRow(
                payload: payload,
                vaultKey: vaultKey,
                keyVersion: 1,
                createdAt: now,
                updatedAt: now
            )
            try store.insert(row)
            reload()
        } catch {
            errorMessage = "Could not save the item."
        }
    }

    func updateItem(_ payload: ItemPayload) {
        guard let store = store, let vaultKey = vaultKey else {
            return
        }
        do {
            let existing = try store.item(id: payload.id)
            let row = try ItemCrypto.makeRow(
                payload: payload,
                vaultKey: vaultKey,
                keyVersion: 1,
                createdAt: existing?.createdAt ?? Self.nowMillis(),
                updatedAt: Self.nowMillis()
            )
            try store.update(row)
            reload()
        } catch {
            errorMessage = "Could not save the item."
        }
    }

    func deleteItem(id: String) {
        guard let store = store else {
            return
        }
        do {
            try store.delete(id: id)
            reload()
        } catch {
            errorMessage = "Could not delete the item."
        }
    }

    var itemCount: Int {
        return headers.count
    }

    static func nowMillis() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000.0)
    }
}
