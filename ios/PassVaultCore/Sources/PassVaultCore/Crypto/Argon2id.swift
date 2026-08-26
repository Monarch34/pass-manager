import Foundation
import CArgon2

/// An Argon2 failure reported by the vendored reference implementation.
///
/// `code` is the library's own negative error code and `message` is whatever
/// `argon2_error_message` says about it, so a CI log tells you exactly which
/// input the C library objected to.
public struct Argon2Error: Error, Equatable, CustomStringConvertible {
    public let code: Int32
    public let message: String

    public init(code: Int32, message: String) {
        self.code = code
        self.message = message
    }

    public var description: String {
        return "Argon2 error \(code): \(message)"
    }
}

/// Thin, safe wrapper over the vendored `argon2id_hash_raw`.
///
/// Always Argon2**id**, always version 1.3 (`ARGON2_VERSION_13`, which is what
/// `argon2id_hash_raw` pins), raw output, no secret key and no associated data —
/// exactly the shape `docs/FORMAT.md` specifies and exactly what Android's
/// `Argon2KdfProvider` asks Argon2Kt for.
///
/// The vendored library is built with `ARGON2_DEFAULT_FLAGS == 0`, so it never
/// clears the caller's password buffer. Wiping the input is this package's job,
/// not the library's.
public enum Argon2id {

    /// Derive a key from a passphrase.
    ///
    /// - Parameters:
    ///   - passphrase: UTF-8 bytes of the passphrase. May be empty (the C library
    ///     accepts a NULL pointer with length 0, which is what an empty Swift
    ///     array produces).
    ///   - salt: Salt bytes. Argon2 requires at least 8.
    /// - Returns: `hashLength` freshly derived bytes.
    /// - Throws: ``Argon2Error`` for any parameter the library rejects. Never
    ///   traps: out-of-range values are caught before the `UInt32` conversions.
    public static func deriveKey(
        passphrase: [UInt8],
        salt: [UInt8],
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        hashLength: Int
    ) throws -> [UInt8] {
        // Guard before converting: `UInt32(_:)` on a negative or oversized Int
        // traps, and these numbers can come straight out of an untrusted file.
        guard
            hashLength > 0,
            hashLength <= 4096,
            let tCost = UInt32(exactly: iterations),
            let mCost = UInt32(exactly: memoryKiB),
            let lanes = UInt32(exactly: parallelism),
            tCost > 0,
            mCost > 0,
            lanes > 0
        else {
            throw Argon2Error(
                code: -1,
                message: "invalid Argon2 parameters "
                    + "(m=\(memoryKiB) KiB, t=\(iterations), p=\(parallelism), out=\(hashLength))"
            )
        }

        var output = [UInt8](repeating: 0, count: hashLength)

        let status: Int32 = passphrase.withUnsafeBytes { (passphraseBuffer: UnsafeRawBufferPointer) -> Int32 in
            return salt.withUnsafeBytes { (saltBuffer: UnsafeRawBufferPointer) -> Int32 in
                return output.withUnsafeMutableBytes { (outputBuffer: UnsafeMutableRawBufferPointer) -> Int32 in
                    return argon2id_hash_raw(
                        tCost,
                        mCost,
                        lanes,
                        passphraseBuffer.baseAddress,
                        passphraseBuffer.count,
                        saltBuffer.baseAddress,
                        saltBuffer.count,
                        outputBuffer.baseAddress,
                        outputBuffer.count
                    )
                }
            }
        }

        // Compared against the literal rather than `ARGON2_OK`: how Swift imports
        // a plain (non-`NS_ENUM`) C enum varies, and `ARGON2_OK == 0` is fixed by
        // the library's ABI anyway.
        guard status == 0 else {
            SecureBytes.zero(&output)
            throw Argon2Error(code: status, message: errorMessage(for: status))
        }

        return output
    }

    /// Convenience overload taking ``KdfParams``.
    public static func deriveKey(
        passphrase: [UInt8],
        salt: [UInt8],
        params: KdfParams
    ) throws -> [UInt8] {
        return try deriveKey(
            passphrase: passphrase,
            salt: salt,
            memoryKiB: params.memory,
            iterations: params.iterations,
            parallelism: params.parallelism,
            hashLength: params.hashLength
        )
    }

    /// Convenience overload working in `Data`.
    public static func deriveKey(
        passphrase: Data,
        salt: Data,
        params: KdfParams
    ) throws -> Data {
        let derived = try deriveKey(
            passphrase: [UInt8](passphrase),
            salt: [UInt8](salt),
            params: params
        )
        return Data(derived)
    }

    private static func errorMessage(for code: Int32) -> String {
        guard let cString = argon2_error_message(code) else {
            return "unknown Argon2 error"
        }
        return String(cString: cString)
    }
}
