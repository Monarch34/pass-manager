import Foundation
import PassVaultCore

/// Card field rules, mirrored from Android's `CardExpiry.kt` /
/// `CardFieldValidation.kt`.
public enum CardRules {

    public static let panDigits = 16

    /// Card number field: digits only.
    public static func panDigitsOnly(_ raw: String) -> String {
        return String(raw.filter { $0.isNumber })
    }

    public static func isPanAcceptableForSave(_ panDigits: String) -> Bool {
        return panDigits.count == CardRules.panDigits
    }

    /// Expiry field raw value: digits only, at most four. The UI inserts the
    /// visual slash.
    public static func sanitizeExpiryDigits(_ value: String) -> String {
        return String(value.filter { $0.isNumber }.prefix(4))
    }

    /// `MMYY` (or `MMYYYY`, accepted for data saved earlier) → month and full year.
    public static func parseExpiry(_ value: String) -> (month: Int, year: Int)? {
        let digits = Array(value.trimmingCharacters(in: .whitespaces).filter { $0.isNumber })
        if digits.count >= 6 {
            guard
                let month = Int(String(digits[0..<2])),
                let year = Int(String(digits[2..<6])),
                (1...12).contains(month),
                (2000...2100).contains(year)
            else {
                return nil
            }
            return (month, year)
        }
        if digits.count >= 4 {
            guard
                let month = Int(String(digits[0..<2])),
                let shortYear = Int(String(digits[2..<4])),
                (1...12).contains(month)
            else {
                return nil
            }
            return (month, 2000 + shortYear)
        }
        return nil
    }

    public static func isExpiryAcceptableForSave(_ fieldDigits: String) -> Bool {
        return parseExpiry(fieldDigits) != nil
    }

    /// Storage and display form: `MM/YY`.
    public static func formatExpiry(month: Int, year: Int) -> String {
        return String(format: "%02d/%02d", month, year % 100)
    }

    /// Digit-only field value to show when loading a stored expiry.
    public static func expiryFieldDigits(fromStored stored: String) -> String {
        guard let parsed = parseExpiry(stored) else {
            return sanitizeExpiryDigits(stored)
        }
        return String(format: "%02d%02d", parsed.month, parsed.year % 100)
    }

    /// A short CVC is a WARNING, not a save gate.
    ///
    /// `docs/IOS_PARITY.md` describes CVC as "3-4" alongside the hard rules, but
    /// Android's `AddEditItemSaveValidator` never blocks a save on it — only
    /// `cardCvcIsWeak` flags it in the UI. Android's shipped behaviour is what is
    /// mirrored here; blocking would make the same card saveable on one platform
    /// and not the other.
    public static func isCvcWeak(_ raw: String) -> Bool {
        let digits = raw.filter { $0.isNumber }
        return !digits.isEmpty && digits.count < 3
    }

    public static func sanitizeCvcDigits(_ value: String) -> String {
        return String(value.filter { $0.isNumber }.prefix(4))
    }
}

/// Why an add/edit form cannot be saved. Mirrors `AddEditSaveFailure`.
public enum SaveFailure: Equatable {
    case titleRequired
    case cardPanInvalid
    case cardExpiryInvalid
    case passwordRequired
    case bankInvalid([BankPasswordRules.Violation])

    public var message: String {
        switch self {
        case .titleRequired:
            return "A title is required"
        case .cardPanInvalid:
            return "Card number must be \(CardRules.panDigits) digits"
        case .cardExpiryInvalid:
            return "Expiry must be a valid MM/YY"
        case .passwordRequired:
            return "A password is required"
        case .bankInvalid(let violations):
            let first = violations.first
            return first.map { "Bank password: \($0.message.lowercased())" } ?? "Bank password is invalid"
        }
    }
}

/// A flat snapshot of the add/edit form. Mirrors `AddEditSaveSnapshot`.
public struct ItemFormSnapshot: Equatable {
    public var title: String = ""
    public var category: ItemCategory = .login
    public var notes: String = ""

    // login
    public var username: String = ""
    public var address: String = ""
    public var password: String = ""

    // card — `cardExpiry` holds DIGITS ONLY (MMYY)
    public var cardholderName: String = ""
    public var cardNumber: String = ""
    public var cardCvc: String = ""
    public var cardExpiry: String = ""

    // bank
    public var accountNumber: String = ""
    public var bankName: String = ""
    public var bankPassword: String = ""
    /// The bank password this record was loaded with; empty for a new record.
    /// Kept apart from `previousPasswords` so an untouched record stays saveable
    /// while a genuine rollback is still rejected.
    public var originalBankPassword: String = ""
    /// Retired passwords only — never contains the current or original one.
    public var previousPasswords: [String] = []

    // identity
    public var firstName: String = ""
    public var lastName: String = ""
    public var email: String = ""
    public var phone: String = ""
    public var identityAddress: String = ""
    public var company: String = ""

    public init() {}
}

/// Save gating and payload construction. Mirrors `AddEditItemSaveValidator`.
public enum ItemFormValidator {

    /// The FIRST reason this form cannot be saved, or `nil` when it can.
    ///
    /// Exposed so the screen can say why the save button is disabled instead of
    /// leaving it dead — the same reason Android made `evaluateFailure` public.
    public static func failure(for form: ItemFormSnapshot) -> SaveFailure? {
        if form.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .titleRequired
        }
        switch form.category {
        case .card:
            if !CardRules.isPanAcceptableForSave(CardRules.panDigitsOnly(form.cardNumber)) {
                return .cardPanInvalid
            }
            if !CardRules.isExpiryAcceptableForSave(form.cardExpiry) {
                return .cardExpiryInvalid
            }
            return nil
        case .bank:
            if form.bankPassword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return .passwordRequired
            }
            let violations = BankPasswordRules.violations(
                in: form.bankPassword,
                previousPasswords: form.previousPasswords
            )
            if !violations.isEmpty {
                return .bankInvalid(violations)
            }
            return nil
        case .note, .identity:
            return nil
        case .login:
            if form.password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return .passwordRequired
            }
            return nil
        }
    }

    public static func canSave(_ form: ItemFormSnapshot) -> Bool {
        return failure(for: form) == nil
    }

    /// Build the payload, or return the reason it cannot be built.
    public static func makePayload(from form: ItemFormSnapshot, id: String) -> Result<ItemPayload, SaveFailure> {
        if let failure = failure(for: form) {
            return .failure(failure)
        }

        let title = form.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let notes = form.notes.trimmingCharacters(in: .whitespacesAndNewlines)

        switch form.category {
        case .card:
            guard let expiry = CardRules.parseExpiry(form.cardExpiry) else {
                return .failure(.cardExpiryInvalid)
            }
            return .success(.card(ItemPayload.Card(
                id: id,
                title: title,
                notes: notes,
                cardholderName: form.cardholderName.trimmingCharacters(in: .whitespacesAndNewlines),
                cardNumber: form.cardNumber.trimmingCharacters(in: .whitespacesAndNewlines),
                cardCvc: form.cardCvc.trimmingCharacters(in: .whitespacesAndNewlines),
                cardExpiry: CardRules.formatExpiry(month: expiry.month, year: expiry.year)
            )))
        case .bank:
            return .success(.bank(ItemPayload.Bank(
                id: id,
                title: title,
                notes: notes,
                accountNumber: form.accountNumber.trimmingCharacters(in: .whitespacesAndNewlines),
                bankName: form.bankName.trimmingCharacters(in: .whitespacesAndNewlines),
                password: form.bankPassword,
                previousPasswords: BankPasswordRules.retiredPasswords(
                    newPassword: form.bankPassword,
                    originalPassword: form.originalBankPassword,
                    previousPasswords: form.previousPasswords
                )
            )))
        case .note:
            return .success(.note(ItemPayload.SecureNote(id: id, title: title, notes: notes)))
        case .identity:
            return .success(.identity(ItemPayload.Identity(
                id: id,
                title: title,
                notes: notes,
                firstName: form.firstName.trimmingCharacters(in: .whitespacesAndNewlines),
                lastName: form.lastName.trimmingCharacters(in: .whitespacesAndNewlines),
                email: form.email.trimmingCharacters(in: .whitespacesAndNewlines),
                phone: form.phone.trimmingCharacters(in: .whitespacesAndNewlines),
                address: form.identityAddress.trimmingCharacters(in: .whitespacesAndNewlines),
                company: form.company.trimmingCharacters(in: .whitespacesAndNewlines)
            )))
        case .login:
            return .success(.login(ItemPayload.Login(
                id: id,
                title: title,
                notes: notes,
                username: form.username.trimmingCharacters(in: .whitespacesAndNewlines),
                address: form.address.trimmingCharacters(in: .whitespacesAndNewlines),
                password: form.password
            )))
        }
    }

    /// Fill a form from an existing payload, for editing.
    public static func makeForm(from payload: ItemPayload) -> ItemFormSnapshot {
        var form = ItemFormSnapshot()
        form.title = payload.title
        form.notes = payload.notes
        form.category = payload.category

        switch payload {
        case .login(let value):
            form.username = value.username
            form.address = value.address
            form.password = value.password
        case .card(let value):
            form.cardholderName = value.cardholderName
            form.cardNumber = value.cardNumber
            form.cardCvc = value.cardCvc
            form.cardExpiry = CardRules.expiryFieldDigits(fromStored: value.cardExpiry)
        case .bank(let value):
            form.accountNumber = value.accountNumber
            form.bankName = value.bankName
            form.bankPassword = value.password
            form.originalBankPassword = value.password
            form.previousPasswords = value.previousPasswords
        case .note:
            break
        case .identity(let value):
            form.firstName = value.firstName
            form.lastName = value.lastName
            form.email = value.email
            form.phone = value.phone
            form.identityAddress = value.address
            form.company = value.company
        }
        return form
    }
}

/// Mirrors Android's `PasswordStrengthEvaluator`.
public enum PasswordStrength: Int, Equatable, CaseIterable {
    case weak = 0
    case fair = 1
    case good = 2
    case strong = 3

    public var label: String {
        switch self {
        case .weak: return "Weak"
        case .fair: return "Fair"
        case .good: return "Good"
        case .strong: return "Strong"
        }
    }

    /// 0...1, for the strength bar.
    public var fraction: Double {
        return Double(rawValue + 1) / 4.0
    }

    public static func evaluate(_ password: String) -> PasswordStrength {
        if password.isEmpty {
            return .weak
        }
        let characters = Array(password)
        var score = 0
        if characters.count >= 8 {
            score += 1
        }
        if characters.count >= 14 {
            score += 1
        }
        if characters.contains(where: { $0.isUppercase }) && characters.contains(where: { $0.isLowercase }) {
            score += 1
        }
        if characters.contains(where: { $0.isNumber }) {
            score += 1
        }
        if characters.contains(where: { !$0.isLetter && !$0.isNumber }) {
            score += 1
        }
        switch score {
        case 0, 1: return .weak
        case 2: return .fair
        case 3: return .good
        default: return .strong
        }
    }
}
