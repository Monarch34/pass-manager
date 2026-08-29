import Foundation
import PassManagerKit

/// The seam between Swift and the Kotlin core.
///
/// Everything that knows how a `KotlinByteArray` differs from `Data`, or how a Kotlin
/// sealed hierarchy arrives in Swift, lives here. The views and the session talk in Swift
/// types and never import the bridge's vocabulary.
enum VaultKit {

    /// What happened when a file was opened. Mirrors the Kotlin outcomes rather than
    /// collapsing them, because the whole point of the container's design is that most
    /// failures are knowable *without* a key and must not be reported as a bad passphrase.
    enum OpenFailure: Equatable {
        /// The passphrase was wrong, or someone edited the file. Genuinely indistinguishable.
        case unopenable
        /// Provably broken, proved without a key.
        case damaged(String, Int)
        /// Written by a version this build cannot read.
        case unsupported(container: Int, minSchema: Int)
        /// Not a vault at all.
        case notAVault

        var message: String {
            switch self {
            case .unopenable:
                return "That passphrase does not open this vault."
            case .damaged(let what, let offset):
                return "This file is damaged at byte \(offset): \(what)."
            case .unsupported(_, let minSchema):
                return "This vault was written by a newer version (needs schema \(minSchema))."
            case .notAVault:
                return "That file is not a PassManager vault."
            }
        }
    }

    /// Opens a vault file. The returned key belongs to the caller and must be destroyed.
    static func open(
        _ file: Data,
        passphrase: String
    ) -> Result<(key: Secret, items: [VaultItem]), OpenFailure> {
        let parsed = PmVault.shared.parse(bytes: file.kotlinBytes)

        guard let sealed = parsed as? VaultParseSealed else {
            if let damaged = parsed as? VaultParseDamaged {
                return .failure(.damaged(damaged.what, Int(damaged.offset)))
            }
            if let unsupported = parsed as? VaultParseUnsupported {
                return .failure(.unsupported(
                    container: Int(unsupported.container),
                    minSchema: Int(unsupported.minSchema)
                ))
            }
            return .failure(.notAVault)
        }

        let secret = Secret.companion.copyOfUtf8(text: passphrase)
        defer { secret.destroy() }

        guard let opened = sealed.openWithPassphrase(passphrase: secret, pepper: nil) as? VaultOpenOpened else {
            return .failure(.unopenable)
        }
        return .success((opened.vaultKey, opened.contents.items))
    }

    /// Creates a brand new vault file from a passphrase.
    static func create(items: [VaultItem], passphrase: String) -> Data {
        let secret = Secret.companion.copyOfUtf8(text: passphrase)
        defer { secret.destroy() }
        let bytes = PmVault.shared.create(
            contents: VaultContents(items: items, deletions: []),
            passphrase: secret,
            parameters: Self.cost,
            pepper: nil
        )
        return bytes.swiftData
    }

    /// Rewrites an existing vault under the key it is already sealed with, so saving an
    /// edit never re-derives from the passphrase and never asks for it again.
    static func rewrite(_ file: Data, key: Secret, items: [VaultItem]) -> Data? {
        guard let sealed = PmVault.shared.parse(bytes: file.kotlinBytes) as? VaultParseSealed else {
            return nil
        }
        let bytes = PmVault.shared.write(
            descriptor: sealed.descriptor,
            slots: sealed.slots,
            contents: VaultContents(items: items, deletions: []),
            vaultKey: key
        )
        return bytes.swiftData
    }

    /// 64 MiB and three passes: RFC 9106's memory-constrained recommendation, at one lane
    /// because the implementation is sequential and extra lanes would cost the phone what
    /// they cost it while saving a threaded attacker real time.
    static let cost = Argon2Parameters(memoryKib: 64 * 1024, iterations: 3, parallelism: 1)
}

// MARK: - Byte bridging

extension Data {
    /// Copies into a Kotlin array, one element at a time.
    ///
    /// Each element crosses the bridge individually, which is slow in principle. A vault is
    /// tens of kilobytes and this happens on unlock and on save, so it is not worth a
    /// faster and less obvious approach until attachments arrive.
    var kotlinBytes: KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}

extension KotlinByteArray {
    var swiftData: Data {
        var data = Data(count: Int(size))
        for index in 0..<Int(size) {
            data[index] = UInt8(bitPattern: get(index: Int32(index)))
        }
        return data
    }
}
