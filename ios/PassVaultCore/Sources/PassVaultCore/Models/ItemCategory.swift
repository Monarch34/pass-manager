import Foundation

/// Vault item category — pure domain enum with no UI dependencies.
///
/// The raw value is the lowercase key used on the wire: it is simultaneously the
/// Android `ItemCategory.dbKey`, the `category` column of a storage row, the
/// `category` field of a `.pmvault` item record and the `"type"` discriminator
/// inside an ``ItemPayload``. Keeping all four on one enum is what stops them
/// drifting apart.
///
/// Mirrors `app/src/main/java/com/passmanager/domain/model/ItemCategory.kt`.
public enum ItemCategory: String, Codable, CaseIterable, Sendable {
    case login
    case card
    case note
    case identity
    case bank

    /// Human-readable English label (matches the Android `label` constructor arg).
    public var label: String {
        switch self {
        case .login: return "Login"
        case .card: return "Card"
        case .note: return "Note"
        case .identity: return "Identity"
        case .bank: return "Bank"
        }
    }

    /// Lowercase key used for storage and interchange.
    public var dbKey: String {
        return rawValue
    }

    /// Lenient parse used for navigation args, deep links and the plaintext
    /// `category` column. Blank or unknown values map to ``login`` so a corrupted
    /// or legacy row never crashes the app — same contract as Android's
    /// `ItemCategory.fromString`.
    ///
    /// This is deliberately NOT used when decoding a payload discriminator: an
    /// unknown `"type"` there must be a hard decoding error, not a silent
    /// downgrade to `login`.
    public static func lenientParse(_ value: String) -> ItemCategory {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return .login
        }
        let lowered = trimmed.lowercased()
        for candidate in ItemCategory.allCases {
            if candidate.rawValue == lowered {
                return candidate
            }
        }
        return .login
    }
}
