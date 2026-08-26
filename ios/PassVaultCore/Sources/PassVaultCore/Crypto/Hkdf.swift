import Foundation
import Crypto

/// HKDF-SHA256 (RFC 5869).
///
/// NOT used anywhere in v1. The vault key path uses Argon2id + AES-GCM directly
/// and `docs/IOS_PARITY.md` puts desktop pairing out of v1 scope. This exists so
/// the counterpart of Android's `HkdfSha256.kt` has an obvious home when the
/// pairing channel lands — at which point its salt/info conventions must be
/// checked against `crypto/kdf/HkdfSha256.kt` and
/// `crypto/channel/EncryptedChannel.kt` before it is trusted for interop.
public enum Hkdf {

    /// Extract-and-expand to `outputByteCount` bytes.
    public static func deriveKey(
        inputKeyMaterial: Data,
        salt: Data,
        info: Data,
        outputByteCount: Int
    ) -> Data {
        let ikm = SymmetricKey(data: inputKeyMaterial)
        let derived = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: ikm,
            salt: salt,
            info: info,
            outputByteCount: outputByteCount
        )
        return derived.withUnsafeBytes { (buffer: UnsafeRawBufferPointer) -> Data in
            return Data(buffer)
        }
    }
}
