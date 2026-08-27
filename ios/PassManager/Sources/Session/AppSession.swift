import Foundation
import SwiftUI
import Security
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

    /// Site icons. OFF unless the user says otherwise, mirroring Android's
    /// `AppSettingsDefaults.USE_GOOGLE_FAVICONS = false`.
    ///
    /// This is the ONLY switch in the app that can cause a network request, so it
    /// is off by default and it is the user's to turn on — see `SiteIcon` for
    /// what a request looks like when they do, and Settings for the copy that
    /// says so before they decide.
    @Published var useSiteIcons: Bool = false {
        didSet {
            guard useSiteIcons != oldValue else {
                return
            }
            UserDefaults.standard.set(useSiteIcons, forKey: Self.siteIconsKey)
            SiteIconLoader.shared.settingChanged(enabled: useSiteIcons)
        }
    }

    /// Reflects whether a `.biometryCurrentSet` Keychain item actually exists —
    /// read without prompting, so the settings toggle can render honestly.
    @Published private(set) var biometricEnabled: Bool = false
    /// Set when biometrics are permanently invalidated, so the UI can offer
    /// re-enrolment instead of just reporting a failure.
    @Published var biometricNeedsReEnrolment: Bool = false

    /// Where an export or import has got to. Survives a lock; see
    /// ``TransferLockPolicy``.
    @Published private(set) var transferStage: TransferStage = .idle

    private static let timeoutKey = "autoLockTimeoutSeconds"
    /// Internal rather than private so the UI-test reset can clear it and the
    /// screenshot tour stays deterministic across relaunches.
    static let siteIconsKey = "useSiteIcons"

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
        // `bool(forKey:)` answers false for a key that was never written, which is
        // exactly the default this setting wants — an install that has never been
        // near Settings makes no requests.
        self.useSiteIcons = UserDefaults.standard.bool(forKey: Self.siteIconsKey)
        if let store = store {
            self.store = store
        }
    }

    func bootstrap() {
        // No-op in release: this call's entire body sits inside `#if DEBUG`.
        UITestMode.resetIfRequested(databasePath: Self.databasePath())
        if store == nil {
            do {
                store = try VaultStore.open(path: Self.databasePath())
                Self.protectDatabaseFile()
            } catch {
                errorMessage = "Could not open the vault database."
                return
            }
        }
        // A vault exists only if BOTH halves are present: the non-secret
        // parameters in SQLite and the wrapped key in the Keychain. Either alone
        // is unusable, which is the point — see `persist(_:)`.
        var hasVault = false
        if let store = store {
            // `try?` on a throwing function returning an Optional flattens, so
            // this is exactly "a metadata row exists and could be read".
            let hasRow = (try? store.metadata()) != nil
            hasVault = hasRow && (loadWrappedKeyBlob() != nil)
        }
        lockState = LockStateMachine.stateAtLaunch(hasVault: hasVault)
        refreshBiometricState()
        loadLastExportDate()
    }

    /// Reads whether an enrolment exists WITHOUT prompting for a face.
    func refreshBiometricState() {
        biometricEnabled = KeychainVaultStore.hasBiometricEnrolment()
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
        // Site icons are keyed by the domains those headers named, which is the
        // same plaintext under a different shape. See `SiteIconLoader.clear()`.
        SiteIconLoader.shared.clear()
        // A lock that left a built export document or a decrypted import body in
        // memory would be a lock in name only. The user's INTENT survives; the
        // plaintext does not.
        discardTransferPlaintext()
        transferStage = TransferLockPolicy.stageAfterLock(transferStage)
        lockState = state
    }

    // MARK: - Setup and unlock

    func createVault(passphrase: String) async {
        guard store != nil else {
            return
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let metadata = try await Task.detached(priority: .userInitiated) {
                try VaultCore.createVault(passphrase: passphrase)
            }.value
            if let failure = persist(metadata) {
                errorMessage = failure.message
                return
            }
            Self.protectDatabaseFile()
            await unlock(passphrase: passphrase)
            seedForUITestsIfRequested()
        } catch {
            errorMessage = "Could not create the vault."
        }
    }

    /// Fills a freshly created vault with sample items so the screenshot tour has
    /// something to show. Does nothing unless this is a DEBUG build launched with
    /// the seed flag — `UITestMode.wantsSeed` is a compile-time `false` in
    /// release.
    private func seedForUITestsIfRequested() {
        guard UITestMode.wantsSeed, let store = store, let vaultKey = vaultKey else {
            return
        }
        guard (try? store.count()) == 0 else {
            return
        }
        // Backdated deliberately: the import fixture carries a NEWER timestamp
        // for one of these ids, which is what makes the import summary show a
        // real overwrite instead of "everything skipped".
        let now = Self.nowMillis()
        let day: Int64 = 86_400_000
        var offset: Int64 = 0
        for payload in UITestMode.samplePayloads() {
            offset += 1
            let row = try? ItemCrypto.makeRow(
                payload: payload,
                vaultKey: vaultKey,
                keyVersion: 1,
                createdAt: now - (120 * day) - offset,
                updatedAt: now - (30 * day) - offset
            )
            if let row = row {
                try? store.insert(row)
            }
        }
        reload()
    }

    func unlock(passphrase: String) async {
        guard let core = loadVaultMetadata() else {
            errorMessage = "This vault's key could not be read from secure storage."
            return
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let key = try await Task.detached(priority: .userInitiated) {
                try VaultCore.unlock(passphrase: passphrase, metadata: core)
            }.value
            finishUnlock(with: key)
        } catch {
            // GCM cannot distinguish a wrong passphrase from corruption, and
            // neither does the message.
            errorMessage = "Wrong passphrase."
        }
    }

    /// Unlock with Face ID / Touch ID.
    ///
    /// Reads the RAW vault key straight out of the Keychain — there is no
    /// Argon2 pass here, which is the whole point of the second item.
    func unlockWithBiometrics() async {
        errorMessage = nil
        let result = KeychainVaultStore.loadBiometricKey(
            prompt: "Unlock your vault"
        )
        switch result {
        case .success(let key):
            guard key.count == VaultCore.vaultKeyByteCount else {
                // A wrong-sized key is a corrupted enrolment, not a bad face.
                disableBiometrics()
                biometricNeedsReEnrolment = true
                errorMessage = DeviceKeyError
                    .permanentlyInvalidated(reason: .biometricsChanged).message
                return
            }
            finishUnlock(with: key)
        case .failure(let error):
            handleBiometricFailure(error)
        }
    }

    /// Store the raw vault key behind `.biometryCurrentSet`. Only possible while
    /// unlocked, because that is the only time the raw key exists.
    @discardableResult
    func enableBiometrics() -> Bool {
        guard let key = vaultKey else {
            return false
        }
        if case .failure(let error) = KeychainVaultStore.biometryAvailability() {
            errorMessage = error.message
            return false
        }
        switch KeychainVaultStore.enrolBiometrics(vaultKey: key) {
        case .success:
            biometricEnabled = true
            biometricNeedsReEnrolment = false
            persistBiometricFlag(true)
            return true
        case .failure(let error):
            errorMessage = error.message
            refreshBiometricState()
            return false
        }
    }

    func disableBiometrics() {
        KeychainVaultStore.removeBiometricEnrolment()
        biometricEnabled = false
        persistBiometricFlag(false)
    }

    func changePassphrase(current: String, new: String) async -> Bool {
        guard let core = loadVaultMetadata() else {
            return false
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let updated = try await Task.detached(priority: .userInitiated) {
                try VaultCore.changePassphrase(
                    currentPassphrase: current,
                    newPassphrase: new,
                    metadata: core
                )
            }.value
            // A passphrase change re-wraps the vault key, so the OLD biometric
            // item now guards a key wrapped under a passphrase that no longer
            // opens anything. Android disables biometric unlock here; so does
            // this, and the user re-enrols from Settings.
            disableBiometrics()
            if let failure = persist(updated) {
                errorMessage = failure.message
                return false
            }
            return true
        } catch {
            errorMessage = "Current passphrase is wrong."
            return false
        }
    }

    private func finishUnlock(with key: Data) {
        vaultKey = key
        lockState = .unlocked
        backgroundedAt = nil
        biometricNeedsReEnrolment = false
        errorMessage = nil
        reload()
        // Resume a transfer the lock interrupted: the picker's result is still
        // here, so the user does not have to find the file again.
        transferStage = TransferLockPolicy.stageAfterUnlock(transferStage)
    }

    /// The error split that matters: a changed enrolment throws the item away and
    /// asks for re-enrolment; a failed match just says "try again". Treating the
    /// second like the first would discard a working enrolment over one bad
    /// scan.
    private func handleBiometricFailure(_ error: DeviceKeyError) {
        if error.isCancellation {
            return
        }
        if error.requiresReEnrolment {
            disableBiometrics()
            biometricNeedsReEnrolment = true
        }
        errorMessage = error.message
    }

    // MARK: - Where the key material lives

    /// SPLIT STORAGE, and the reason for it.
    ///
    /// The wrapped vault key goes to the Keychain under
    /// `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`; SQLite keeps only the
    /// salt, the KDF cost and the key version, none of which is secret.
    ///
    /// So a copy of the database file on its own cannot even be attacked
    /// offline: there is no wrapped key in it to run a dictionary against. And
    /// the Keychain item is device-bound, passcode-gated and excluded from
    /// backups, which is the threat-model equivalent of Android's Keystore
    /// pepper wrap — `docs/IOS_PARITY.md` asks for equivalence, not API parity.
    ///
    /// The `wrapped_vault_key` / `wrapper_iv` columns stay in the schema for
    /// Android parity and are written EMPTY.
    private func persist(_ metadata: VaultMetadata) -> DeviceKeyError? {
        guard let store = store else {
            return .unexpected(status: errSecNotAvailable)
        }
        var blob = Data()
        blob.append(metadata.wrapNonce)
        blob.append(metadata.wrappedVaultKey)
        if let error = saveWrappedKeyBlob(blob) {
            return error
        }

        var record = StoredVaultMetadata(metadata)
        record.wrappedVaultKey = Data()
        record.wrapperIv = Data()
        record.biometricEnabled = biometricEnabled
        do {
            try store.saveMetadata(record)
        } catch {
            return .unexpected(status: errSecIO)
        }
        return nil
    }

    /// Write the wrapped key to secure storage. The Keychain is the only place it
    /// is ever written, in every configuration this app is built in — a failure
    /// here is reported, never absorbed by a second store.
    private func saveWrappedKeyBlob(_ blob: Data) -> DeviceKeyError? {
        if case .failure(let error) = KeychainVaultStore.saveWrappedKey(blob) {
            return error
        }
        return nil
    }

    private func loadWrappedKeyBlob() -> Data? {
        if case .success(let blob) = KeychainVaultStore.loadWrappedKey() {
            return blob
        }
        return nil
    }

    /// Recombine the two halves into the metadata the pure core expects.
    private func loadVaultMetadata() -> VaultMetadata? {
        guard let store = store, let record = try? store.metadata() else {
            return nil
        }
        guard let blob = loadWrappedKeyBlob() else {
            return nil
        }
        let bytes = [UInt8](blob)
        guard bytes.count > AesGcm.nonceByteCount else {
            return nil
        }
        return VaultMetadata(
            keyVersion: record.currentKeyVersion,
            wrappedVaultKey: Data(bytes[AesGcm.nonceByteCount..<bytes.count]),
            wrapNonce: Data(bytes[0..<AesGcm.nonceByteCount]),
            kdfSalt: record.kdfSalt,
            kdfParams: record.kdfParams
        )
    }

    /// Mirrors the flag into the row so the schema stays meaningful, but the
    /// Keychain remains the source of truth — a stale `1` here can never make the
    /// app believe in an enrolment the system already destroyed.
    private func persistBiometricFlag(_ enabled: Bool) {
        guard let store = store, var record = try? store.metadata() else {
            return
        }
        record.biometricEnabled = enabled
        try? store.saveMetadata(record)
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

    // MARK: - Export and import

    /// Ciphertext read from the picker. Survives a lock deliberately: it is no
    /// more sensitive than the file sitting in Files, and keeping it is what
    /// spares the user hunting for it again after an auto-lock.
    private var pendingImportFile: Data?
    /// Decrypted import body. Vault plaintext — discarded on lock.
    private var pendingImportBody: PmVaultBody?
    /// A built `.pmvault`. A complete copy of the vault — discarded on lock.
    @Published private(set) var pendingExportDocument: Data?
    @Published private(set) var importPlan: ImportPlan?
    @Published var importMode: ImportMode = .merge
    @Published private(set) var lastExportAt: Int64?
    /// Drives the Files picker for import. Owned by the session rather than a
    /// view so Settings can dismiss itself and still have the picker appear.
    @Published var isPickingImportFile: Bool = false

    func requestImportPicker() {
        errorMessage = nil
        // The screenshot tour cannot drive the system Files picker, so in a DEBUG
        // build launched with the fixture flag it is handed a container built in
        // memory instead. Everything downstream — decrypt, plan, summary, apply —
        // is the real code path.
        if UITestMode.wantsImportFixture, let fixture = UITestMode.makeImportFixture() {
            beginImport(fileData: fixture)
            return
        }
        isPickingImportFile = true
    }

    var backupStatus: BackupReminder.Status {
        return BackupReminder.status(
            lastExportAt: lastExportAt,
            now: Self.nowMillis(),
            itemCount: itemCount
        )
    }

    func loadLastExportDate() {
        let stored = UserDefaults.standard.object(forKey: BackupReminder.defaultsKey) as? NSNumber
        lastExportAt = stored?.int64Value
    }

    // MARK: Export

    /// Begin an export. If the vault is locked, this only records the intent —
    /// the passphrase is asked once the user is back in.
    func beginExport() {
        errorMessage = nil
        transferStage = lockState.isUnlocked ? .awaitingExportPassphrase : .awaitingUnlockForExport
    }

    /// Encrypt every item under a fresh export passphrase.
    func prepareExportDocument(passphrase: String) async -> Bool {
        guard let body = buildExportBody() else {
            errorMessage = "Could not read the vault to export it."
            return false
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let document = try await Task.detached(priority: .userInitiated) {
                try PmVaultFile.write(body: body, passphrase: passphrase)
            }.value
            pendingExportDocument = document
            transferStage = .exporting
            return true
        } catch {
            errorMessage = "Could not create the export."
            return false
        }
    }

    /// Called once the system picker has written the file.
    func completeExport(success: Bool) {
        if success {
            let now = Self.nowMillis()
            UserDefaults.standard.set(NSNumber(value: now), forKey: BackupReminder.defaultsKey)
            lastExportAt = now
        }
        discardTransferPlaintext()
        transferStage = .idle
    }

    private func buildExportBody() -> PmVaultBody? {
        guard let store = store, let vaultKey = vaultKey else {
            return nil
        }
        var items: [PmVaultItem] = []
        for header in headers {
            guard
                let row = try? store.item(id: header.id),
                let payload = try? ItemCrypto.decryptPayload(row: row, vaultKey: vaultKey)
            else {
                continue
            }
            // createdAt and updatedAt are carried through from the row, never
            // stamped with "now" — an export is a copy, not a new record.
            items.append(PmVaultItem(
                id: row.id,
                category: row.category,
                createdAt: row.createdAt,
                updatedAt: row.updatedAt,
                payload: payload
            ))
        }
        return PmVaultBody(version: 1, exportedAt: Self.nowMillis(), items: items)
    }

    // MARK: Import

    /// Take the bytes the picker handed us. Reading the file needs no vault key,
    /// so this works whether or not the vault happens to be locked.
    func beginImport(fileData: Data) {
        errorMessage = nil
        pendingImportFile = fileData
        transferStage = lockState.isUnlocked ? .awaitingImportPassphrase : .awaitingUnlockForImport
    }

    /// Decrypt the file and compute what applying it WOULD do. Writes nothing.
    func planImport(passphrase: String) async -> Bool {
        guard let fileData = pendingImportFile, let store = store else {
            errorMessage = "No file to import."
            return false
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let body = try await Task.detached(priority: .userInitiated) {
                try PmVaultFile.read(fileData, passphrase: passphrase)
            }.value
            pendingImportBody = body
            let existing = try store.updatedAtById()
            importPlan = ImportMerge.plan(
                fileItems: body.items,
                existingUpdatedAt: existing,
                titles: headerCache.titles,
                now: Self.nowMillis(),
                mode: importMode
            )
            transferStage = .reviewingImport
            return true
        } catch let error as PmVaultError {
            errorMessage = error.kind == .wrongPassphraseOrCorrupt
                ? "The passphrase is wrong or the file is corrupted."
                : "This file could not be read: \(error)"
            return false
        } catch {
            errorMessage = "This file could not be read."
            return false
        }
    }

    /// Re-plan when the user flips "add only". No decryption is repeated — the
    /// body is already in hand, and planning is pure.
    func replanImport() {
        guard let body = pendingImportBody, let store = store else {
            return
        }
        guard let existing = try? store.updatedAtById() else {
            return
        }
        importPlan = ImportMerge.plan(
            fileItems: body.items,
            existingUpdatedAt: existing,
            titles: headerCache.titles,
            now: Self.nowMillis(),
            mode: importMode
        )
    }

    /// Apply a plan the user has seen and confirmed. Nothing is written before
    /// this is called.
    @discardableResult
    func applyImport() -> ImportOutcome? {
        guard let plan = importPlan, let store = store, let vaultKey = vaultKey else {
            return nil
        }
        do {
            let outcome = try store.applyImport(plan, vaultKey: vaultKey, keyVersion: 1)
            reload()
            cancelTransfer()
            return outcome
        } catch {
            errorMessage = "Could not apply the import."
            return nil
        }
    }

    func cancelTransfer() {
        pendingImportFile = nil
        discardTransferPlaintext()
        transferStage = .idle
    }

    /// Everything derived from the vault key. Called on lock and at the end of
    /// every flow.
    private func discardTransferPlaintext() {
        if var document = pendingExportDocument {
            pendingExportDocument = nil
            SecureBytes.zero(&document)
        }
        pendingImportBody = nil
        importPlan = nil
    }
}
