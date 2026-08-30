import Foundation
import PassManagerKit
import SwiftUI
import UniformTypeIdentifiers

/// The app's state, which is almost entirely `core:vault`'s state.
///
/// What a vault is — opening it, searching it, saving an item, attaching a file — is decided
/// once in the shared module and used identically here and on Android. This holds the open
/// session, turns its outcomes into something a screen can render, and decides when to let
/// go of the key.
@MainActor
final class AppSession: ObservableObject {

    enum Phase: Equatable {
        case empty
        case locked
        case unlocked
    }

    @Published private(set) var phase: Phase
    @Published private(set) var items: [VaultItem] = []
    @Published var failure: String?

    private let files = VaultFile()
    private let blobs = BlobStore()

    /// Held only while unlocked. Destroying it is what locking means.
    private var session: VaultSession?

    var biometricsEnabled: Bool { BiometricVaultKey.isEnabled }
    var biometricsAvailable: Bool { BiometricVaultKey.isAvailable }
    var biometricName: String { BiometricVaultKey.name }

    init() {
        phase = VaultFile().exists() ? .locked : .empty
    }

    // MARK: - Opening and closing

    func create(passphrase: String) {
        let secret = Secret.companion.ofUtf8(text: passphrase)
        defer { secret.destroy() }
        // Vault.create writes the file and then opens it by reading it back rather than
        // assuming, so a vault that cannot be reopened is found now and not later.
        adopt(Vault.shared.create(
            store: files,
            blobs: blobs,
            passphrase: secret,
            parameters: Argon2Parameters(memoryKib: 64 * 1024, iterations: 3, parallelism: 1)
        ))
    }

    func unlock(passphrase: String) {
        let secret = Secret.companion.ofUtf8(text: passphrase)
        defer { secret.destroy() }
        handle(Vault.shared.unlock(store: files, blobs: blobs, passphrase: secret))
    }

    /// Opens with the key the Keychain holds. The file is still parsed and its body still
    /// authenticated — this replaces the derivation, not the verification.
    func unlockWithBiometrics() {
        switch BiometricVaultKey.load() {
        case .success(let key):
            let result = Vault.shared.unlockWithVaultKey(store: files, blobs: blobs, vaultKey: key)
            if result is UnlockResultUnlocked {
                handle(result)
            } else {
                key.destroy()
                // A stored key that no longer opens this vault fails every future attempt
                // while still looking like an option, so it is removed rather than kept.
                BiometricVaultKey.remove()
                failure = "The saved key does not open this vault. Use your passphrase."
            }
        case .failure(let reason):
            if reason != .cancelled { failure = reason.message }
        }
    }

    func enableBiometrics() {
        guard let session else { return }
        // useVaultKey is generic, and a generic return crosses the Objective-C bridge as
        // `Any?`. Rather than cast the result back, the outcome is captured — the block's
        // parameter is concrete, which is the half that matters.
        var outcome: Result<Void, BiometricVaultKey.Failure>?
        _ = session.useVaultKey { key in
            outcome = BiometricVaultKey.store(key)
            return nil
        }
        if case .failure(let reason) = outcome { failure = reason.message }
    }

    func disableBiometrics() {
        BiometricVaultKey.remove()
    }

    func lock() {
        session?.lock()
        session = nil
        items = []
        failure = nil
        phase = .locked
    }

    /// Deletes the vault. The only answer to a forgotten passphrase, and a real one: nothing
    /// here can open a vault without its key.
    func startOver() {
        session?.lock()
        session = nil
        items = []
        files.delete()
        for id in blobs.list() { blobs.delete(id: id) }
        // The stored key would otherwise outlive the vault it opens.
        BiometricVaultKey.remove()
        failure = nil
        phase = .empty
    }

    // MARK: - Editing

    func save(_ item: VaultItem) {
        guard let session else { return }
        session.save(item: item)
        items = session.items
    }

    /// Which entries match what was typed.
    ///
    /// Asked of the shared session rather than answered here. This used to be a local
    /// function whose own comment claimed it searched "everything the item holds, secrets
    /// included" while actually matching a title, a username, an address, a bank name and
    /// three identity fields — no notes, no passwords, nothing on a card. It also folded case
    /// with `lowercased()`, which turns İ into an i and a combining dot and so fails to match
    /// exactly the alphabet this application's first users type in.
    func search(_ query: String) -> [VaultItem] {
        guard let session, !session.isLocked else { return [] }
        return session.search(query: query)
    }

    func delete(_ item: VaultItem) {
        guard let session else { return }
        session.delete(id: item.id, now: Int64(Date().timeIntervalSince1970 * 1000))
        items = session.items
    }

    // MARK: - Attachments

    func attachments(of item: VaultItem) -> [Attachment] {
        session?.attachments(itemId: item.id) ?? []
    }

    /// Seals a file the user picked onto an item. The vault itself is untouched.
    func attach(_ url: URL, to item: VaultItem) {
        guard let session else { return }
        // A file handed over by the document picker lives outside this app's sandbox, so it
        // has to be opened under a security scope that is given back afterwards.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let data = try? Data(contentsOf: url) else {
            failure = "That file could not be read."
            return
        }
        guard data.count <= Int(PmBlob.shared.MaxContentSize) else {
            failure = "Attachments are limited to 5 MB."
            return
        }

        // Made before the bytes are sealed away, because afterwards making one would mean
        // decrypting the whole attachment again just to draw a row.
        let thumbnail = Thumbnails.of(data)
        let content = Secret.companion.adopt(bytes: data.kotlinBytes)
        defer { content.destroy() }
        session.attach(
            itemId: item.id,
            filename: url.lastPathComponent,
            mimeType: Self.mimeType(of: url),
            content: content,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000),
            thumbnail: thumbnail?.kotlinBytes
        )
    }

    /// Decrypts an attachment so it can be drawn.
    ///
    /// The result is `Data` because every decoder on this platform takes `Data` and nothing
    /// takes a `Secret`. The secret is destroyed the moment its bytes have been copied out,
    /// so what remains is the one copy the viewer is showing — and it is never written down:
    /// no temporary file, no cache, no handing the document to another application.
    func openAttachment(_ id: String) -> Data? {
        guard let session, let secret = session.openAttachment(id: id) else { return nil }
        defer { secret.destroy() }
        var data: Data?
        // reveal borrows the live array rather than copying it, which for a five-megabyte
        // scan is the difference between one copy and three.
        _ = secret.reveal { bytes in
            data = bytes.swiftData
            return nil
        }
        return data
    }

    /// What the file actually is, as far as the system will say.
    ///
    /// The first version of this recorded `application/octet-stream` for everything, which
    /// made every attachment it stored unviewable by type. Those are still readable — the
    /// shared classifier looks at the bytes first — but recording the truth costs one line.
    private static func mimeType(of url: URL) -> String {
        let type = (try? url.resourceValues(forKeys: [.contentTypeKey]).contentType)
            ?? UTType(filenameExtension: url.pathExtension)
        return type?.preferredMIMEType ?? "application/octet-stream"
    }

    func deleteAttachment(_ id: String) {
        session?.deleteAttachment(id: id)
    }

    // MARK: - Internals

    private func handle(_ result: UnlockResult) {
        switch result {
        case let unlocked as UnlockResultUnlocked:
            adopt(unlocked.session)
        case let damaged as UnlockResultDamaged:
            failure = "This vault is damaged at byte \(damaged.offset): \(damaged.what)."
        case is UnlockResultUnsupported:
            failure = "This vault was written by a newer version of the app."
        case is UnlockResultNotAVault:
            failure = "That file is not a PassManager vault."
        case is UnlockResultNoVault:
            phase = .empty
        default:
            // Wrong passphrase and a tampered file are one outcome, and saying so is the
            // point: claiming to know which would tell an attacker whether their forgery
            // was structurally correct.
            failure = "That passphrase does not open this vault."
        }
    }

    private func adopt(_ opened: VaultSession) {
        // Files left by an interrupted delete are inert, but they are still the user's data
        // sitting on disk with nothing pointing at them. Cleared once, here.
        opened.sweepOrphanedAttachments()
        session = opened
        items = opened.items
        failure = nil
        phase = .unlocked
    }
}
