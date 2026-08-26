import Foundation
import PassVaultCore

public enum ItemCryptoError: Error, Equatable {
    /// The row predates the encrypted header columns, or they were lost. iOS never
    /// writes such a row — unlike Android, this app has no legacy blobs to migrate
    /// — so it means the row is damaged.
    case missingHeaderColumns(id: String)
    case notUtf8
}

/// The three independent envelopes a `vault_items` row stores.
public struct ItemEnvelopes: Equatable, Sendable {
    /// Full payload JSON.
    public var data: AesGcm.Sealed
    /// Title string only.
    public var title: AesGcm.Sealed
    /// List-subtitle string. `nil` when the subtitle is empty — matching Android,
    /// which writes no address envelope rather than an envelope around "".
    public var address: AesGcm.Sealed?

    public init(data: AesGcm.Sealed, title: AesGcm.Sealed, address: AesGcm.Sealed?) {
        self.data = data
        self.title = title
        self.address = address
    }
}

/// A decrypted header, as shown in one vault list row.
public struct DecryptedHeader: Equatable, Sendable {
    public var title: String
    public var address: String

    public init(title: String, address: String) {
        self.title = title
        self.address = address
    }
}

/// Encrypts and decrypts the three envelopes of a vault item under the vault key.
///
/// Each envelope gets its own random 12-byte IV. They are independent by design:
/// the list screen decrypts title and address without ever touching the payload.
public enum ItemCrypto {

    /// Encrypt a payload into the three envelopes stored on a row.
    public static func encrypt(payload: ItemPayload, vaultKey: Data) throws -> ItemEnvelopes {
        var payloadJson = try PayloadJson.encode(payload)
        var titleBytes = Data(payload.title.utf8)
        let subtitle = payload.listSubtitle
        var addressBytes = Data(subtitle.utf8)
        defer {
            SecureBytes.zero(&payloadJson)
            SecureBytes.zero(&titleBytes)
            SecureBytes.zero(&addressBytes)
        }

        let dataEnvelope = try AesGcm.seal(payloadJson, key: vaultKey)
        let titleEnvelope = try AesGcm.seal(titleBytes, key: vaultKey)
        var addressEnvelope: AesGcm.Sealed? = nil
        if !subtitle.isEmpty {
            addressEnvelope = try AesGcm.seal(addressBytes, key: vaultKey)
        }

        return ItemEnvelopes(data: dataEnvelope, title: titleEnvelope, address: addressEnvelope)
    }

    /// Build a complete row from a payload. `createdAt` and `updatedAt` are taken
    /// separately and never inferred from each other.
    public static func makeRow(
        payload: ItemPayload,
        vaultKey: Data,
        keyVersion: Int,
        createdAt: Int64,
        updatedAt: Int64
    ) throws -> VaultItemRow {
        let envelopes = try encrypt(payload: payload, vaultKey: vaultKey)
        return VaultItemRow(
            id: payload.id,
            encryptedData: envelopes.data.ciphertext,
            dataIv: envelopes.data.nonce,
            keyVersion: keyVersion,
            createdAt: createdAt,
            updatedAt: updatedAt,
            category: payload.category.rawValue,
            encryptedTitle: envelopes.title.ciphertext,
            titleIv: envelopes.title.nonce,
            encryptedAddress: envelopes.address?.ciphertext,
            addressIv: envelopes.address?.nonce
        )
    }

    /// Decrypt the payload envelope only.
    public static func decryptPayload(row: VaultItemRow, vaultKey: Data) throws -> ItemPayload {
        let sealed = AesGcm.Sealed(nonce: row.dataIv, ciphertext: row.encryptedData)
        var plaintext = try AesGcm.open(sealed, key: vaultKey)
        defer { SecureBytes.zero(&plaintext) }
        return try PayloadJson.decode(plaintext)
    }

    /// Decrypt the title and address envelopes only — never the payload.
    ///
    /// Returns `nil` when the row carries no header columns at all, so a damaged
    /// row degrades to "not shown" instead of taking the list down.
    public static func decryptHeader(row: VaultItemHeaderRow, vaultKey: Data) throws -> DecryptedHeader? {
        guard let encryptedTitle = row.encryptedTitle, let titleIv = row.titleIv else {
            return nil
        }

        var titleData = try AesGcm.open(
            AesGcm.Sealed(nonce: titleIv, ciphertext: encryptedTitle),
            key: vaultKey
        )
        defer { SecureBytes.zero(&titleData) }
        let title = String(decoding: titleData, as: UTF8.self)

        var address = ""
        if let encryptedAddress = row.encryptedAddress, let addressIv = row.addressIv {
            var addressData = try AesGcm.open(
                AesGcm.Sealed(nonce: addressIv, ciphertext: encryptedAddress),
                key: vaultKey
            )
            defer { SecureBytes.zero(&addressData) }
            address = String(decoding: addressData, as: UTF8.self)
        }

        return DecryptedHeader(title: title, address: address)
    }
}
