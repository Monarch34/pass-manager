import Foundation

public enum VaultError: Error, Equatable {
    /// The wrapped vault key failed to unwrap. As on Android, this is the GCM tag
    /// failing — there is no stored verifier and no boolean check to bypass.
    case wrongPassphrase
    case invalidSaltLength(Int)
    case invalidVaultKeyLength(Int)
}

/// Everything needed to unwrap a vault key, and nothing that is device-bound.
///
/// This is the portable half of Android's `VaultMetadata`. The platform layer
/// (Keychain on iOS, Keystore on Android) wraps *this blob* again; none of that
/// outer layer appears here, and none of it may ever reach a `.pmvault` file.
public struct VaultMetadata: Codable, Equatable, Sendable {
    public var keyVersion: Int
    /// AES-256-GCM `ciphertext || tag` of the 32-byte vault key under the KEK.
    public var wrappedVaultKey: Data
    /// 12-byte nonce used for ``wrappedVaultKey``.
    public var wrapNonce: Data
    /// 16-byte Argon2id salt.
    public var kdfSalt: Data
    public var kdfParams: KdfParams

    public init(
        keyVersion: Int,
        wrappedVaultKey: Data,
        wrapNonce: Data,
        kdfSalt: Data,
        kdfParams: KdfParams
    ) {
        self.keyVersion = keyVersion
        self.wrappedVaultKey = wrappedVaultKey
        self.wrapNonce = wrapNonce
        self.kdfSalt = kdfSalt
        self.kdfParams = kdfParams
    }
}

/// The two-key model, identical to Android's `SetupVaultUseCase` /
/// `UnlockWithPassphraseUseCase` / `ChangePassphraseUseCase`.
///
/// 1. **Vault key** — 32 CSPRNG bytes made at creation. Encrypts every item.
///    Never on disk in the clear.
/// 2. **KEK** — Argon2id over the master passphrase. Its only job is wrapping the
///    vault key with AES-256-GCM.
/// 3. A wrong passphrase surfaces as ``VaultError/wrongPassphrase``, produced by
///    the GCM tag failing on unwrap.
///
/// Pure and stateless: no persistence, no Keychain, no lock-state machine. Those
/// belong to the layers above.
public enum VaultCore {

    public static let vaultKeyByteCount = 32
    public static let saltByteCount = 16

    /// Create a vault: generate a random vault key, derive a KEK from the
    /// passphrase and return the wrapped key alongside the salt and cost
    /// parameters needed to unwrap it again.
    ///
    /// The vault key is wiped before returning, exactly like Android's setup use
    /// case. Call ``unlock(passphrase:metadata:)`` to obtain a usable key.
    public static func createVault(
        passphrase: String,
        params: KdfParams = KdfParams.standard
    ) throws -> VaultMetadata {
        var passphraseBytes = Array(passphrase.utf8)
        var vaultKeyBytes = SecureBytes.randomBytes(vaultKeyByteCount)
        var derivedKeyBytes: [UInt8] = []
        var vaultKeyData = Data()
        var derivedKeyData = Data()
        defer {
            SecureBytes.zero(&passphraseBytes)
            SecureBytes.zero(&vaultKeyBytes)
            SecureBytes.zero(&derivedKeyBytes)
            SecureBytes.zero(&vaultKeyData)
            SecureBytes.zero(&derivedKeyData)
        }

        let saltBytes = SecureBytes.randomBytes(saltByteCount)
        derivedKeyBytes = try Argon2id.deriveKey(
            passphrase: passphraseBytes,
            salt: saltBytes,
            params: params
        )
        vaultKeyData = Data(vaultKeyBytes)
        derivedKeyData = Data(derivedKeyBytes)

        let sealed = try AesGcm.seal(vaultKeyData, key: derivedKeyData)

        return VaultMetadata(
            keyVersion: 1,
            wrappedVaultKey: sealed.ciphertext,
            wrapNonce: sealed.nonce,
            kdfSalt: Data(saltBytes),
            kdfParams: params
        )
    }

    /// Unwrap the vault key.
    ///
    /// - Returns: the 32-byte vault key. The caller owns it and is responsible for
    ///   wiping it on lock.
    /// - Throws: ``VaultError/wrongPassphrase`` when the tag fails.
    public static func unlock(passphrase: String, metadata: VaultMetadata) throws -> Data {
        guard metadata.kdfSalt.count == saltByteCount else {
            throw VaultError.invalidSaltLength(metadata.kdfSalt.count)
        }

        var passphraseBytes = Array(passphrase.utf8)
        var derivedKeyBytes: [UInt8] = []
        var derivedKeyData = Data()
        defer {
            SecureBytes.zero(&passphraseBytes)
            SecureBytes.zero(&derivedKeyBytes)
            SecureBytes.zero(&derivedKeyData)
        }

        derivedKeyBytes = try Argon2id.deriveKey(
            passphrase: passphraseBytes,
            salt: [UInt8](metadata.kdfSalt),
            params: metadata.kdfParams
        )
        derivedKeyData = Data(derivedKeyBytes)

        let sealed = AesGcm.Sealed(
            nonce: metadata.wrapNonce,
            ciphertext: metadata.wrappedVaultKey
        )

        var vaultKey = Data()
        do {
            vaultKey = try AesGcm.open(sealed, key: derivedKeyData)
        } catch AesGcmError.authenticationFailure {
            throw VaultError.wrongPassphrase
        }

        guard vaultKey.count == vaultKeyByteCount else {
            SecureBytes.zero(&vaultKey)
            throw VaultError.invalidVaultKeyLength(vaultKey.count)
        }
        return vaultKey
    }

    /// Re-wrap the vault key under a new passphrase.
    ///
    /// Items are NOT re-encrypted — only the wrapping changes. A fresh salt is
    /// generated and, like Android's `ChangePassphraseUseCase`, the vault is moved
    /// onto the current default cost parameters, since this is the one moment the
    /// key is being re-wrapped anyway.
    ///
    /// The caller must also disable biometric unlock afterwards (Android does this
    /// through `BiometricLockPort`; on iOS it means deleting the
    /// `.biometryCurrentSet` Keychain item). That is a device-layer concern and
    /// deliberately outside this pure core.
    public static func changePassphrase(
        currentPassphrase: String,
        newPassphrase: String,
        metadata: VaultMetadata,
        params: KdfParams = KdfParams.standard
    ) throws -> VaultMetadata {
        var vaultKey = try unlock(passphrase: currentPassphrase, metadata: metadata)
        var newPassphraseBytes = Array(newPassphrase.utf8)
        var newDerivedKeyBytes: [UInt8] = []
        var newDerivedKeyData = Data()
        defer {
            SecureBytes.zero(&vaultKey)
            SecureBytes.zero(&newPassphraseBytes)
            SecureBytes.zero(&newDerivedKeyBytes)
            SecureBytes.zero(&newDerivedKeyData)
        }

        let newSaltBytes = SecureBytes.randomBytes(saltByteCount)
        newDerivedKeyBytes = try Argon2id.deriveKey(
            passphrase: newPassphraseBytes,
            salt: newSaltBytes,
            params: params
        )
        newDerivedKeyData = Data(newDerivedKeyBytes)

        let sealed = try AesGcm.seal(vaultKey, key: newDerivedKeyData)

        var updated = metadata
        updated.wrappedVaultKey = sealed.ciphertext
        updated.wrapNonce = sealed.nonce
        updated.kdfSalt = Data(newSaltBytes)
        updated.kdfParams = params
        return updated
    }
}
