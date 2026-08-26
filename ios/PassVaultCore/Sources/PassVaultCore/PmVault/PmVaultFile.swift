import Foundation

/// Every way reading a `.pmvault` file can fail.
public enum PmVaultError: Error, Equatable {
    /// The header parsed but carries a `version` this build does not implement.
    /// Never attempt a best-effort parse of one of these.
    case unsupportedVersion(Int)
    /// The container itself is wrong: bad magic, truncated, header is not JSON.
    case malformed(String)
    /// The header parsed but its KDF parameters or salt fall outside the
    /// mandatory pre-KDF gate in `docs/FORMAT.md`.
    case invalidHeaderParameters(String)
    /// The GCM tag failed. Could be a wrong passphrase, a corrupted body or a
    /// tampered header — the tag cannot tell them apart, so neither do we.
    /// User-facing message: "passphrase is wrong or the file is corrupted"
    /// (TR: "parola yanlış veya dosya bozuk").
    case wrongPassphraseOrCorrupt

    /// The case identity without its payload, so tests and UI can switch on the
    /// outcome without matching on a diagnostic string.
    public enum Kind: String, Sendable {
        case unsupportedVersion
        case malformed
        case invalidHeaderParameters
        case wrongPassphraseOrCorrupt
    }

    public var kind: Kind {
        switch self {
        case .unsupportedVersion: return .unsupportedVersion
        case .malformed: return .malformed
        case .invalidHeaderParameters: return .invalidHeaderParameters
        case .wrongPassphraseOrCorrupt: return .wrongPassphraseOrCorrupt
        }
    }
}

/// The UTF-8 JSON header of a `.pmvault` file.
public struct PmVaultHeader: Codable, Equatable, Sendable {
    public var version: Int
    /// Base64 (RFC 4648, with padding) of exactly 16 salt bytes.
    public var salt: String
    public var kdf: KdfParams

    public init(version: Int, salt: String, kdf: KdfParams) {
        self.version = version
        self.salt = salt
        self.kdf = kdf
    }
}

/// One exported item. `category` duplicates `payload.type` for cheap scanning;
/// `payload.type` is authoritative.
public struct PmVaultItem: Codable, Equatable, Sendable {
    public var id: String
    public var category: String
    /// Unix epoch milliseconds, UTC.
    public var createdAt: Int64
    /// Unix epoch milliseconds, UTC.
    public var updatedAt: Int64
    public var payload: ItemPayload

    public init(id: String, category: String, createdAt: Int64, updatedAt: Int64, payload: ItemPayload) {
        self.id = id
        self.category = category
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.payload = payload
    }

    /// Build a record from a payload, taking `id` and `category` from the payload
    /// itself so the two can never disagree.
    public init(payload: ItemPayload, createdAt: Int64, updatedAt: Int64) {
        self.id = payload.id
        self.category = payload.category.rawValue
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.payload = payload
    }
}

/// The decrypted body plaintext.
public struct PmVaultBody: Codable, Equatable, Sendable {
    public var version: Int
    /// Unix epoch milliseconds, UTC.
    public var exportedAt: Int64
    public var items: [PmVaultItem]

    public init(version: Int = 1, exportedAt: Int64, items: [PmVaultItem]) {
        self.version = version
        self.exportedAt = exportedAt
        self.items = items
    }
}

/// Reader and writer for the `.pmvault` v1 container specified in
/// `docs/FORMAT.md`.
///
/// ```
/// offset  size  field
/// 0       4     magic: ASCII "PMVT"
/// 4       2     headerLen: unsigned 16-bit, big-endian
/// 6       N     header: UTF-8 JSON, exactly headerLen bytes
/// 6+N     12    iv: AES-GCM nonce for the body
/// 6+N+12  rest  body: AES-256-GCM ciphertext || 16-byte tag
/// ```
///
/// The AAD is the first `6 + headerLen` bytes verbatim, so tampering with the
/// magic, the length or the header fails the tag check.
public enum PmVaultFile {

    /// ASCII "PMVT".
    public static let magic: [UInt8] = [0x50, 0x4D, 0x56, 0x54]
    public static let formatVersion = 1
    public static let prologueByteCount = 6
    public static let ivByteCount = 12
    public static let tagByteCount = 16
    public static let saltByteCount = 16
    public static let maximumHeaderLength = KdfImportBounds.maximumHeaderLength

    // MARK: - Writing

    /// Encrypt `body` into a `.pmvault` container.
    ///
    /// A fresh 16-byte salt and a fresh 12-byte IV are generated per call, as
    /// `docs/FORMAT.md` requires; neither is ever reused.
    ///
    /// The export passphrase is independent of the master passphrase. Enforcing a
    /// strength floor and warning against reuse is the UI's job.
    public static func write(
        body: PmVaultBody,
        passphrase: String,
        params: KdfParams = KdfParams.standard
    ) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]

        var plaintext = try encoder.encode(body)
        var passphraseBytes = Array(passphrase.utf8)
        var derivedKeyBytes: [UInt8] = []
        var derivedKeyData = Data()
        defer {
            SecureBytes.zero(&plaintext)
            SecureBytes.zero(&passphraseBytes)
            SecureBytes.zero(&derivedKeyBytes)
            SecureBytes.zero(&derivedKeyData)
        }

        let saltBytes = SecureBytes.randomBytes(saltByteCount)
        let header = PmVaultHeader(
            version: formatVersion,
            salt: Data(saltBytes).base64EncodedString(),
            kdf: params
        )
        let headerData = try encoder.encode(header)
        guard headerData.count <= maximumHeaderLength else {
            throw PmVaultError.invalidHeaderParameters(
                "header is \(headerData.count) bytes, over the \(maximumHeaderLength) byte limit"
            )
        }

        let prefix = makePrefix(headerData: headerData)

        derivedKeyBytes = try Argon2id.deriveKey(
            passphrase: passphraseBytes,
            salt: saltBytes,
            params: params
        )
        derivedKeyData = Data(derivedKeyBytes)

        let sealed = try AesGcm.seal(plaintext, key: derivedKeyData, aad: prefix)

        var file = Data()
        file.append(prefix)
        file.append(sealed.nonce)
        file.append(sealed.ciphertext)
        return file
    }

    // MARK: - Reading

    /// Decrypt and parse a `.pmvault` container.
    ///
    /// The header is fully validated BEFORE any key derivation happens. Without
    /// that gate a crafted header could demand a 1 GiB derivation and get the app
    /// OOM-killed before any authenticity check ran.
    public static func read(_ file: Data, passphrase: String) throws -> PmVaultBody {
        // Copy into an array so a `Data` slice with a non-zero startIndex cannot
        // silently shift every offset below.
        let bytes = [UInt8](file)

        guard bytes.count >= prologueByteCount else {
            throw PmVaultError.malformed(
                "file is \(bytes.count) bytes, shorter than the \(prologueByteCount)-byte prologue"
            )
        }
        guard
            bytes[0] == magic[0],
            bytes[1] == magic[1],
            bytes[2] == magic[2],
            bytes[3] == magic[3]
        else {
            throw PmVaultError.malformed("bad magic — not a .pmvault file")
        }

        let headerLength = (Int(bytes[4]) << 8) | Int(bytes[5])
        guard headerLength <= maximumHeaderLength else {
            throw PmVaultError.invalidHeaderParameters(
                "headerLen \(headerLength) exceeds the \(maximumHeaderLength) byte limit"
            )
        }
        guard headerLength > 0 else {
            throw PmVaultError.malformed("headerLen is zero")
        }

        let headerEnd = prologueByteCount + headerLength
        let minimumLength = headerEnd + ivByteCount + tagByteCount
        guard bytes.count >= minimumLength else {
            throw PmVaultError.malformed(
                "file is \(bytes.count) bytes, truncated — needs at least \(minimumLength)"
            )
        }

        let headerData = Data(bytes[prologueByteCount..<headerEnd])
        let header: PmVaultHeader
        do {
            header = try JSONDecoder().decode(PmVaultHeader.self, from: headerData)
        } catch {
            throw PmVaultError.malformed("header is not valid JSON for this format")
        }

        // The header's `version` field alone governs parsing.
        guard header.version == formatVersion else {
            throw PmVaultError.unsupportedVersion(header.version)
        }

        guard let saltData = Data(base64Encoded: header.salt) else {
            throw PmVaultError.invalidHeaderParameters("salt is not valid base64")
        }
        if let reason = header.kdf.importRejectionReason(saltLength: saltData.count) {
            throw PmVaultError.invalidHeaderParameters(reason)
        }

        // ── Gate passed. Only now is it safe to derive. ──

        let aad = Data(bytes[0..<headerEnd])
        let iv = Data(bytes[headerEnd..<(headerEnd + ivByteCount)])
        let bodyCiphertext = Data(bytes[(headerEnd + ivByteCount)..<bytes.count])

        var passphraseBytes = Array(passphrase.utf8)
        var derivedKeyBytes: [UInt8] = []
        var derivedKeyData = Data()
        var plaintext = Data()
        defer {
            SecureBytes.zero(&passphraseBytes)
            SecureBytes.zero(&derivedKeyBytes)
            SecureBytes.zero(&derivedKeyData)
            SecureBytes.zero(&plaintext)
        }

        derivedKeyBytes = try Argon2id.deriveKey(
            passphrase: passphraseBytes,
            salt: [UInt8](saltData),
            params: header.kdf
        )
        derivedKeyData = Data(derivedKeyBytes)

        let sealed = AesGcm.Sealed(nonce: iv, ciphertext: bodyCiphertext)
        do {
            plaintext = try AesGcm.open(sealed, key: derivedKeyData, aad: aad)
        } catch {
            throw PmVaultError.wrongPassphraseOrCorrupt
        }

        do {
            return try JSONDecoder().decode(PmVaultBody.self, from: plaintext)
        } catch {
            throw PmVaultError.malformed("decrypted body is not valid .pmvault JSON")
        }
    }

    // MARK: - Helpers

    /// `magic || headerLen (big-endian UInt16) || header` — the exact byte range
    /// used as AAD.
    public static func makePrefix(headerData: Data) -> Data {
        var prefix = Data()
        prefix.append(contentsOf: magic)
        prefix.append(UInt8((headerData.count >> 8) & 0xFF))
        prefix.append(UInt8(headerData.count & 0xFF))
        prefix.append(headerData)
        return prefix
    }
}
