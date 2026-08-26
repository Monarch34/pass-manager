import Foundation

/// Type-safe encrypted vault item payload.
///
/// Wire-compatible with the Android `ItemPayload` sealed class serialized by
/// `PayloadJson` (kotlinx.serialization, `classDiscriminator = "type"`,
/// `encodeDefaults = false`, `ignoreUnknownKeys = true`). The schema is normative
/// in `docs/FORMAT.md`.
///
/// Two rules make the JSON byte-compatible with Android:
///
/// 1. Every payload object carries a `"type"` discriminator whose value is the
///    lowercase category key.
/// 2. Fields that carry the Kotlin default (`""` for strings, `[]` for
///    `previousPasswords`) are **omitted**, exactly like `encodeDefaults = false`.
///    `id` and `title` have no Kotlin default and are therefore always written,
///    even when empty.
///
/// Decoding is tolerant in the same way Kotlin is: a missing optional field
/// decodes to the empty value, and unknown fields are ignored (Swift's
/// `KeyedDecodingContainer` ignores keys with no matching `CodingKey`).
///
/// SECURITY NOTE: Swift `String` is immutable and heap-allocated; it cannot be
/// zeroed. Minimize how long payloads are retained in view models and drop the
/// references when navigating away.
public enum ItemPayload: Codable, Equatable, Sendable {

    // MARK: - Login / default credential

    public struct Login: Codable, Equatable, Sendable {
        public var id: String
        public var title: String
        public var notes: String
        public var username: String
        public var address: String
        public var password: String

        public init(
            id: String,
            title: String,
            notes: String = "",
            username: String = "",
            address: String = "",
            password: String = ""
        ) {
            self.id = id
            self.title = title
            self.notes = notes
            self.username = username
            self.address = address
            self.password = password
        }

        enum CodingKeys: String, CodingKey {
            case type
            case id
            case title
            case notes
            case username
            case address
            case password
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
            self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
            self.notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
            self.username = try container.decodeIfPresent(String.self, forKey: .username) ?? ""
            self.address = try container.decodeIfPresent(String.self, forKey: .address) ?? ""
            self.password = try container.decodeIfPresent(String.self, forKey: .password) ?? ""
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(ItemCategory.login.rawValue, forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(title, forKey: .title)
            if !notes.isEmpty {
                try container.encode(notes, forKey: .notes)
            }
            if !username.isEmpty {
                try container.encode(username, forKey: .username)
            }
            if !address.isEmpty {
                try container.encode(address, forKey: .address)
            }
            if !password.isEmpty {
                try container.encode(password, forKey: .password)
            }
        }
    }

    // MARK: - Payment card

    public struct Card: Codable, Equatable, Sendable {
        public var id: String
        public var title: String
        public var notes: String
        public var cardholderName: String
        public var cardNumber: String
        public var cardCvc: String
        public var cardExpiry: String

        public init(
            id: String,
            title: String,
            notes: String = "",
            cardholderName: String = "",
            cardNumber: String = "",
            cardCvc: String = "",
            cardExpiry: String = ""
        ) {
            self.id = id
            self.title = title
            self.notes = notes
            self.cardholderName = cardholderName
            self.cardNumber = cardNumber
            self.cardCvc = cardCvc
            self.cardExpiry = cardExpiry
        }

        enum CodingKeys: String, CodingKey {
            case type
            case id
            case title
            case notes
            case cardholderName
            case cardNumber
            case cardCvc
            case cardExpiry
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
            self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
            self.notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
            self.cardholderName = try container.decodeIfPresent(String.self, forKey: .cardholderName) ?? ""
            self.cardNumber = try container.decodeIfPresent(String.self, forKey: .cardNumber) ?? ""
            self.cardCvc = try container.decodeIfPresent(String.self, forKey: .cardCvc) ?? ""
            self.cardExpiry = try container.decodeIfPresent(String.self, forKey: .cardExpiry) ?? ""
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(ItemCategory.card.rawValue, forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(title, forKey: .title)
            if !notes.isEmpty {
                try container.encode(notes, forKey: .notes)
            }
            if !cardholderName.isEmpty {
                try container.encode(cardholderName, forKey: .cardholderName)
            }
            if !cardNumber.isEmpty {
                try container.encode(cardNumber, forKey: .cardNumber)
            }
            if !cardCvc.isEmpty {
                try container.encode(cardCvc, forKey: .cardCvc)
            }
            if !cardExpiry.isEmpty {
                try container.encode(cardExpiry, forKey: .cardExpiry)
            }
        }
    }

    // MARK: - Bank account

    public struct Bank: Codable, Equatable, Sendable {
        public var id: String
        public var title: String
        public var notes: String
        public var accountNumber: String
        public var bankName: String
        public var password: String
        public var previousPasswords: [String]

        public init(
            id: String,
            title: String,
            notes: String = "",
            accountNumber: String = "",
            bankName: String = "",
            password: String = "",
            previousPasswords: [String] = []
        ) {
            self.id = id
            self.title = title
            self.notes = notes
            self.accountNumber = accountNumber
            self.bankName = bankName
            self.password = password
            self.previousPasswords = previousPasswords
        }

        enum CodingKeys: String, CodingKey {
            case type
            case id
            case title
            case notes
            case accountNumber
            case bankName
            case password
            case previousPasswords
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
            self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
            self.notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
            self.accountNumber = try container.decodeIfPresent(String.self, forKey: .accountNumber) ?? ""
            self.bankName = try container.decodeIfPresent(String.self, forKey: .bankName) ?? ""
            self.password = try container.decodeIfPresent(String.self, forKey: .password) ?? ""
            self.previousPasswords = try container.decodeIfPresent([String].self, forKey: .previousPasswords) ?? []
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(ItemCategory.bank.rawValue, forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(title, forKey: .title)
            if !notes.isEmpty {
                try container.encode(notes, forKey: .notes)
            }
            if !accountNumber.isEmpty {
                try container.encode(accountNumber, forKey: .accountNumber)
            }
            if !bankName.isEmpty {
                try container.encode(bankName, forKey: .bankName)
            }
            if !password.isEmpty {
                try container.encode(password, forKey: .password)
            }
            if !previousPasswords.isEmpty {
                try container.encode(previousPasswords, forKey: .previousPasswords)
            }
        }
    }

    // MARK: - Secure note

    public struct SecureNote: Codable, Equatable, Sendable {
        public var id: String
        public var title: String
        public var notes: String

        public init(id: String, title: String, notes: String = "") {
            self.id = id
            self.title = title
            self.notes = notes
        }

        enum CodingKeys: String, CodingKey {
            case type
            case id
            case title
            case notes
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
            self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
            self.notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(ItemCategory.note.rawValue, forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(title, forKey: .title)
            if !notes.isEmpty {
                try container.encode(notes, forKey: .notes)
            }
        }
    }

    // MARK: - Identity

    public struct Identity: Codable, Equatable, Sendable {
        public var id: String
        public var title: String
        public var notes: String
        public var firstName: String
        public var lastName: String
        public var email: String
        public var phone: String
        public var address: String
        public var company: String

        public init(
            id: String,
            title: String,
            notes: String = "",
            firstName: String = "",
            lastName: String = "",
            email: String = "",
            phone: String = "",
            address: String = "",
            company: String = ""
        ) {
            self.id = id
            self.title = title
            self.notes = notes
            self.firstName = firstName
            self.lastName = lastName
            self.email = email
            self.phone = phone
            self.address = address
            self.company = company
        }

        enum CodingKeys: String, CodingKey {
            case type
            case id
            case title
            case notes
            case firstName
            case lastName
            case email
            case phone
            case address
            case company
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
            self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
            self.notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
            self.firstName = try container.decodeIfPresent(String.self, forKey: .firstName) ?? ""
            self.lastName = try container.decodeIfPresent(String.self, forKey: .lastName) ?? ""
            self.email = try container.decodeIfPresent(String.self, forKey: .email) ?? ""
            self.phone = try container.decodeIfPresent(String.self, forKey: .phone) ?? ""
            self.address = try container.decodeIfPresent(String.self, forKey: .address) ?? ""
            self.company = try container.decodeIfPresent(String.self, forKey: .company) ?? ""
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(ItemCategory.identity.rawValue, forKey: .type)
            try container.encode(id, forKey: .id)
            try container.encode(title, forKey: .title)
            if !notes.isEmpty {
                try container.encode(notes, forKey: .notes)
            }
            if !firstName.isEmpty {
                try container.encode(firstName, forKey: .firstName)
            }
            if !lastName.isEmpty {
                try container.encode(lastName, forKey: .lastName)
            }
            if !email.isEmpty {
                try container.encode(email, forKey: .email)
            }
            if !phone.isEmpty {
                try container.encode(phone, forKey: .phone)
            }
            if !address.isEmpty {
                try container.encode(address, forKey: .address)
            }
            if !company.isEmpty {
                try container.encode(company, forKey: .company)
            }
        }
    }

    // MARK: - Cases

    case login(Login)
    case card(Card)
    case bank(Bank)
    case note(SecureNote)
    case identity(Identity)

    // MARK: - Common accessors

    public var id: String {
        switch self {
        case .login(let value): return value.id
        case .card(let value): return value.id
        case .bank(let value): return value.id
        case .note(let value): return value.id
        case .identity(let value): return value.id
        }
    }

    public var title: String {
        switch self {
        case .login(let value): return value.title
        case .card(let value): return value.title
        case .bank(let value): return value.title
        case .note(let value): return value.title
        case .identity(let value): return value.title
        }
    }

    public var notes: String {
        switch self {
        case .login(let value): return value.notes
        case .card(let value): return value.notes
        case .bank(let value): return value.notes
        case .note(let value): return value.notes
        case .identity(let value): return value.notes
        }
    }

    /// Derived from the case itself — compile-time exhaustive, so it can never
    /// disagree with the `"type"` discriminator.
    public var category: ItemCategory {
        switch self {
        case .login: return .login
        case .card: return .card
        case .bank: return .bank
        case .note: return .note
        case .identity: return .identity
        }
    }

    /// The subtitle shown under the title in the vault list.
    /// Mirrors Android `ItemPayload.listSubtitle` exactly.
    public var listSubtitle: String {
        switch self {
        case .login(let value):
            return value.address
        case .card(let value):
            return value.cardholderName
        case .bank(let value):
            return value.bankName
        case .note(let value):
            return String(value.notes.prefix(60))
        case .identity(let value):
            if !value.email.isEmpty {
                return value.email
            }
            let fullName = "\(value.firstName) \(value.lastName)"
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !fullName.isEmpty {
                return fullName
            }
            if !value.company.isEmpty {
                return value.company
            }
            return value.phone
        }
    }

    // MARK: - Codable

    private enum DiscriminatorKey: String, CodingKey {
        case type
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DiscriminatorKey.self)
        let rawType = try container.decode(String.self, forKey: .type)
        guard let category = ItemCategory(rawValue: rawType) else {
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unknown ItemPayload type \"\(rawType)\""
            )
        }
        switch category {
        case .login:
            let value = try Login(from: decoder)
            self = .login(value)
        case .card:
            let value = try Card(from: decoder)
            self = .card(value)
        case .bank:
            let value = try Bank(from: decoder)
            self = .bank(value)
        case .note:
            let value = try SecureNote(from: decoder)
            self = .note(value)
        case .identity:
            let value = try Identity(from: decoder)
            self = .identity(value)
        }
    }

    public func encode(to encoder: Encoder) throws {
        switch self {
        case .login(let value):
            try value.encode(to: encoder)
        case .card(let value):
            try value.encode(to: encoder)
        case .bank(let value):
            try value.encode(to: encoder)
        case .note(let value):
            try value.encode(to: encoder)
        case .identity(let value):
            try value.encode(to: encoder)
        }
    }
}

/// Central JSON codec for ``ItemPayload`` — the Swift counterpart of Android's
/// `PayloadJson` object.
///
/// `.sortedKeys` makes the output deterministic (the same payload always
/// produces the same bytes, which matters for reproducible tests and stable
/// at-rest blobs). JSON object key ORDER is not semantically significant, and
/// kotlinx.serialization locates the `"type"` discriminator wherever it appears
/// in the object, so sorted output stays readable by Android.
///
/// `.withoutEscapingSlashes` is required for byte-compatibility: Foundation
/// otherwise writes `https:\/\/github.com`, which Kotlin never produces.
public enum PayloadJson {

    public static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return encoder
    }

    public static func makeDecoder() -> JSONDecoder {
        return JSONDecoder()
    }

    public static func encode(_ payload: ItemPayload) throws -> Data {
        return try makeEncoder().encode(payload)
    }

    public static func decode(_ data: Data) throws -> ItemPayload {
        return try makeDecoder().decode(ItemPayload.self, from: data)
    }

    public static func encodeToString(_ payload: ItemPayload) throws -> String {
        let data = try encode(payload)
        return String(decoding: data, as: UTF8.self)
    }

    public static func decode(fromString string: String) throws -> ItemPayload {
        return try decode(Data(string.utf8))
    }
}
