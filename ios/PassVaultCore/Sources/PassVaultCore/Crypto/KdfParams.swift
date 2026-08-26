import Foundation

/// Argon2id cost parameters.
///
/// Mirrors Android `com.passmanager.crypto.model.KdfParams`. The defaults are the
/// values pinned by `docs/FORMAT.md` for newly created vaults and for every
/// `.pmvault` export: m = 65536 KiB, t = 3, p = 4, out = 32 bytes.
///
/// An existing vault is always unlocked with the parameters stored in its own
/// metadata, so changing the defaults here never re-derives an old vault's key at
/// the wrong cost.
///
/// Unlike the Kotlin data class, this type does NOT validate in its initialiser.
/// Swift has no throwing memberwise init and trapping on a value that came off
/// disk or out of an untrusted file would turn a malformed input into a crash.
/// Validation is explicit instead — see `importRejectionReason(saltLength:)`,
/// which implements the pre-KDF gate from `docs/FORMAT.md`.
public struct KdfParams: Codable, Equatable, Sendable {

    /// Memory cost in KiB.
    public var memory: Int
    /// Time cost (number of passes).
    public var iterations: Int
    /// Number of lanes / threads.
    public var parallelism: Int
    /// Derived key length in bytes.
    public var hashLength: Int

    /// The pinned defaults from `docs/FORMAT.md`.
    public static let standard = KdfParams()

    public init(
        memory: Int = 65536,
        iterations: Int = 3,
        parallelism: Int = 4,
        hashLength: Int = 32
    ) {
        self.memory = memory
        self.iterations = iterations
        self.parallelism = parallelism
        self.hashLength = hashLength
    }

    enum CodingKeys: String, CodingKey {
        case memory
        case iterations
        case parallelism
        case hashLength
    }

    /// Tolerant decoding: a missing field falls back to the pinned default rather
    /// than failing the whole file. `encode(to:)` is synthesised and always writes
    /// all four keys, which is what `docs/FORMAT.md` requires a writer to emit.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.memory = try container.decodeIfPresent(Int.self, forKey: .memory) ?? 65536
        self.iterations = try container.decodeIfPresent(Int.self, forKey: .iterations) ?? 3
        self.parallelism = try container.decodeIfPresent(Int.self, forKey: .parallelism) ?? 4
        self.hashLength = try container.decodeIfPresent(Int.self, forKey: .hashLength) ?? 32
    }
}

/// The bounds of the mandatory pre-KDF validation gate in `docs/FORMAT.md`.
///
/// These are deliberately much tighter than Android's `KdfParams` companion
/// bounds. Android's bounds guard *its own* stored metadata; these guard an
/// attacker-supplied file, where the job is to stop a crafted header demanding a
/// 1 GiB derivation and OOM-killing the app before any authenticity check runs.
public enum KdfImportBounds {
    /// KiB — 256 MiB ceiling.
    public static let maximumMemoryKiB = 262144
    public static let minimumIterations = 1
    public static let maximumIterations = 16
    public static let minimumParallelism = 1
    public static let maximumParallelism = 8
    public static let requiredHashLength = 32
    public static let requiredSaltLength = 16
    public static let maximumHeaderLength = 4096
}

extension KdfParams {

    /// Reason a set of imported KDF parameters was rejected, if it was.
    ///
    /// Returns `nil` when the parameters pass the gate. The caller runs this
    /// BEFORE deriving anything.
    public func importRejectionReason(saltLength: Int) -> String? {
        // The checks listed as mandatory by docs/FORMAT.md, in that order.
        if memory > KdfImportBounds.maximumMemoryKiB {
            return "memory \(memory) exceeds the \(KdfImportBounds.maximumMemoryKiB) KiB ceiling"
        }
        if iterations < KdfImportBounds.minimumIterations || iterations > KdfImportBounds.maximumIterations {
            return "iterations \(iterations) outside "
                + "\(KdfImportBounds.minimumIterations)...\(KdfImportBounds.maximumIterations)"
        }
        if parallelism < KdfImportBounds.minimumParallelism || parallelism > KdfImportBounds.maximumParallelism {
            return "parallelism \(parallelism) outside "
                + "\(KdfImportBounds.minimumParallelism)...\(KdfImportBounds.maximumParallelism)"
        }
        if hashLength != KdfImportBounds.requiredHashLength {
            return "hashLength \(hashLength) is not \(KdfImportBounds.requiredHashLength)"
        }
        if saltLength != KdfImportBounds.requiredSaltLength {
            return "salt length \(saltLength) is not \(KdfImportBounds.requiredSaltLength) bytes"
        }
        // Not in the FORMAT.md list, because FORMAT.md only enumerates the DoS
        // ceilings. A zero or negative memory cost is not a cheap derivation, it
        // is not a derivation at all — Argon2 rejects it downstream. Catching it
        // here keeps every bad-header outcome a single typed error instead of
        // leaking an Argon2 error code out of the importer.
        if memory < 1 {
            return "memory \(memory) is not a positive KiB count"
        }
        return nil
    }
}
