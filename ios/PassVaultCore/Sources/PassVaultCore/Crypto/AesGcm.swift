import Foundation
import Crypto

public enum AesGcmError: Error, Equatable {
    case invalidKeyLength(Int)
    case invalidNonceLength(Int)
    case invalidCiphertextLength(Int)
    /// The GCM tag did not verify. Indistinguishable, by design, from "wrong key".
    case authenticationFailure
}

/// AES-256-GCM with a 12-byte nonce and a 128-bit tag.
///
/// Byte-compatible with Android's `AesGcmCipher` (`AES/GCM/NoPadding`,
/// `GCMParameterSpec(128, iv)`): JCA appends the tag to the ciphertext, and so
/// does ``Sealed/ciphertext`` here. The nonce is kept separate rather than using
/// CryptoKit's `combined` representation, because Android stores the IV in its own
/// column and `docs/FORMAT.md` puts it in its own field.
public enum AesGcm {

    public static let keyByteCount = 32
    public static let nonceByteCount = 12
    public static let tagByteCount = 16

    /// A nonce plus `ciphertext || tag`.
    public struct Sealed: Equatable, Sendable {
        /// 12 bytes.
        public let nonce: Data
        /// Ciphertext with the 16-byte tag appended.
        public let ciphertext: Data

        public init(nonce: Data, ciphertext: Data) {
            self.nonce = nonce
            self.ciphertext = ciphertext
        }
    }

    /// Encrypt with a fresh random nonce.
    public static func seal(_ plaintext: Data, key: Data, aad: Data? = nil) throws -> Sealed {
        let nonce = SecureBytes.random(nonceByteCount)
        return try seal(plaintext, key: key, nonce: nonce, aad: aad)
    }

    /// Encrypt with a caller-supplied nonce.
    ///
    /// Only pass an explicit nonce when the format demands a specific one, never
    /// to reuse one: a repeated (key, nonce) pair destroys GCM's security.
    public static func seal(_ plaintext: Data, key: Data, nonce: Data, aad: Data? = nil) throws -> Sealed {
        guard key.count == keyByteCount else {
            throw AesGcmError.invalidKeyLength(key.count)
        }
        guard nonce.count == nonceByteCount else {
            throw AesGcmError.invalidNonceLength(nonce.count)
        }

        let symmetricKey = SymmetricKey(data: key)
        let gcmNonce: AES.GCM.Nonce
        do {
            gcmNonce = try AES.GCM.Nonce(data: nonce)
        } catch {
            throw AesGcmError.invalidNonceLength(nonce.count)
        }

        let box: AES.GCM.SealedBox
        if let aad = aad {
            box = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: gcmNonce, authenticating: aad)
        } else {
            box = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: gcmNonce)
        }

        var combined = Data()
        combined.append(box.ciphertext)
        combined.append(box.tag)
        return Sealed(nonce: nonce, ciphertext: combined)
    }

    /// Decrypt and verify.
    ///
    /// - Throws: ``AesGcmError/authenticationFailure`` when the tag does not
    ///   verify — which covers a wrong key, a tampered ciphertext AND a tampered
    ///   AAD. GCM cannot tell you which, and neither can this.
    public static func open(_ sealed: Sealed, key: Data, aad: Data? = nil) throws -> Data {
        guard key.count == keyByteCount else {
            throw AesGcmError.invalidKeyLength(key.count)
        }
        guard sealed.nonce.count == nonceByteCount else {
            throw AesGcmError.invalidNonceLength(sealed.nonce.count)
        }
        guard sealed.ciphertext.count >= tagByteCount else {
            throw AesGcmError.invalidCiphertextLength(sealed.ciphertext.count)
        }

        // Re-index through an array so a `Data` slice with a non-zero startIndex
        // (very easy to produce accidentally) cannot corrupt the split.
        let bytes = [UInt8](sealed.ciphertext)
        let splitIndex = bytes.count - tagByteCount
        let ciphertextOnly = Data(bytes[0..<splitIndex])
        let tag = Data(bytes[splitIndex..<bytes.count])

        let symmetricKey = SymmetricKey(data: key)
        let gcmNonce: AES.GCM.Nonce
        do {
            gcmNonce = try AES.GCM.Nonce(data: sealed.nonce)
        } catch {
            throw AesGcmError.invalidNonceLength(sealed.nonce.count)
        }

        do {
            let box = try AES.GCM.SealedBox(nonce: gcmNonce, ciphertext: ciphertextOnly, tag: tag)
            if let aad = aad {
                return try AES.GCM.open(box, using: symmetricKey, authenticating: aad)
            } else {
                return try AES.GCM.open(box, using: symmetricKey)
            }
        } catch {
            throw AesGcmError.authenticationFailure
        }
    }
}
