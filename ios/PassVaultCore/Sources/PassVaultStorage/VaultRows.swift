import Foundation
import GRDB
import PassVaultCore

/// A full `vault_items` row.
///
/// Three independent AES-256-GCM envelopes under the vault key, each with its own
/// random 12-byte IV, plus the deliberately plaintext `category` column. The
/// category is the one tradeoff `docs/IOS_PARITY.md` accepts: it is what makes
/// filtering possible in SQL without leaking anything more specific than "this is
/// a card".
public struct VaultItemRow: Equatable, Sendable {
    public var id: String
    /// Ciphertext (with tag) of the full payload JSON.
    public var encryptedData: Data
    public var dataIv: Data
    public var keyVersion: Int
    /// Epoch milliseconds, UTC.
    public var createdAt: Int64
    /// Epoch milliseconds, UTC. Independent of ``createdAt`` — see
    /// `VaultStore.insert(_:)`.
    public var updatedAt: Int64
    /// Plaintext category key (`login`/`card`/`note`/`identity`/`bank`).
    public var category: String
    public var encryptedTitle: Data?
    public var titleIv: Data?
    public var encryptedAddress: Data?
    public var addressIv: Data?

    public init(
        id: String,
        encryptedData: Data,
        dataIv: Data,
        keyVersion: Int,
        createdAt: Int64,
        updatedAt: Int64,
        category: String,
        encryptedTitle: Data? = nil,
        titleIv: Data? = nil,
        encryptedAddress: Data? = nil,
        addressIv: Data? = nil
    ) {
        self.id = id
        self.encryptedData = encryptedData
        self.dataIv = dataIv
        self.keyVersion = keyVersion
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.category = category
        self.encryptedTitle = encryptedTitle
        self.titleIv = titleIv
        self.encryptedAddress = encryptedAddress
        self.addressIv = addressIv
    }

    init(row: Row) {
        self.id = row["id"]
        self.encryptedData = row["encrypted_data"]
        self.dataIv = row["data_iv"]
        self.keyVersion = row["key_version"]
        self.createdAt = row["created_at"]
        self.updatedAt = row["updated_at"]
        self.category = row["category"]
        self.encryptedTitle = row["encrypted_title"]
        self.titleIv = row["title_iv"]
        self.encryptedAddress = row["encrypted_address"]
        self.addressIv = row["address_iv"]
    }
}

/// The header projection the vault list reads.
///
/// This type has no payload field on purpose. The list screen must never pull
/// `encrypted_data` — it is the single largest column and the list has no use for
/// it, so fetching it would cost the whole vault's worth of I/O and decryption to
/// render one screen of titles.
public struct VaultItemHeaderRow: Equatable, Sendable {
    public var id: String
    public var encryptedTitle: Data?
    public var titleIv: Data?
    public var encryptedAddress: Data?
    public var addressIv: Data?
    public var category: ItemCategory
    public var updatedAt: Int64

    public init(
        id: String,
        encryptedTitle: Data?,
        titleIv: Data?,
        encryptedAddress: Data?,
        addressIv: Data?,
        category: ItemCategory,
        updatedAt: Int64
    ) {
        self.id = id
        self.encryptedTitle = encryptedTitle
        self.titleIv = titleIv
        self.encryptedAddress = encryptedAddress
        self.addressIv = addressIv
        self.category = category
        self.updatedAt = updatedAt
    }

    init(row: Row) {
        self.id = row["id"]
        self.encryptedTitle = row["encrypted_title"]
        self.titleIv = row["title_iv"]
        self.encryptedAddress = row["encrypted_address"]
        self.addressIv = row["address_iv"]
        let rawCategory: String = row["category"]
        // Lenient, like Android: a corrupted or unknown category must not crash
        // the list.
        self.category = ItemCategory.lenientParse(rawCategory)
        self.updatedAt = row["updated_at"]
    }
}

/// The single `vault_metadata` row.
///
/// `kdfParams` is stored as JSON in one column, exactly as Android stores it in
/// `kdf_params_json`, so the two databases stay readable side by side.
public struct StoredVaultMetadata: Equatable, Sendable {
    public var currentKeyVersion: Int
    public var wrappedVaultKey: Data
    public var wrapperIv: Data
    public var kdfSalt: Data
    public var kdfParams: KdfParams
    public var biometricEnabled: Bool
    public var biometricWrappedKey: Data?
    public var biometricWrapperIv: Data?

    public init(
        currentKeyVersion: Int,
        wrappedVaultKey: Data,
        wrapperIv: Data,
        kdfSalt: Data,
        kdfParams: KdfParams,
        biometricEnabled: Bool = false,
        biometricWrappedKey: Data? = nil,
        biometricWrapperIv: Data? = nil
    ) {
        self.currentKeyVersion = currentKeyVersion
        self.wrappedVaultKey = wrappedVaultKey
        self.wrapperIv = wrapperIv
        self.kdfSalt = kdfSalt
        self.kdfParams = kdfParams
        self.biometricEnabled = biometricEnabled
        self.biometricWrappedKey = biometricWrappedKey
        self.biometricWrapperIv = biometricWrapperIv
    }

    /// Bridge from the pure core's ``VaultMetadata``. The biometric columns are a
    /// device-layer concern and are never produced by the core.
    public init(_ metadata: VaultMetadata) {
        self.init(
            currentKeyVersion: metadata.keyVersion,
            wrappedVaultKey: metadata.wrappedVaultKey,
            wrapperIv: metadata.wrapNonce,
            kdfSalt: metadata.kdfSalt,
            kdfParams: metadata.kdfParams
        )
    }

    /// Bridge back to the pure core's ``VaultMetadata`` for unlocking.
    public var coreMetadata: VaultMetadata {
        return VaultMetadata(
            keyVersion: currentKeyVersion,
            wrappedVaultKey: wrappedVaultKey,
            wrapNonce: wrapperIv,
            kdfSalt: kdfSalt,
            kdfParams: kdfParams
        )
    }
}
