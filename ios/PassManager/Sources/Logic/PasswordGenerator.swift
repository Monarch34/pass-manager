import Foundation
import PassVaultCore

/// Password generation, matching Android's `GeneratePasswordUseCase` +
/// `PasswordGeneratorViewModel`.
///
/// The character pools below are copied verbatim from the Kotlin companion
/// object. They must stay identical: a password generated on one platform has to
/// be acceptable to the other platform's validators.
public enum PasswordGenerator {

    public static let uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    public static let lowercase = "abcdefghijklmnopqrstuvwxyz"
    public static let digits = "0123456789"
    public static let symbols = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    public static let minLength = 8
    public static let maxLength = 64
    public static let defaultLength = 16

    /// A random draw can still land on `abc` or `111`. Redrawing is cheaper than
    /// patching characters into place, and keeps full entropy.
    static let maxAttempts = 12

    public struct Options: Equatable, Sendable {
        public var length: Int
        public var includeUppercase: Bool
        public var includeLowercase: Bool
        public var includeDigits: Bool
        public var includeSymbols: Bool

        public init(
            length: Int = PasswordGenerator.defaultLength,
            includeUppercase: Bool = true,
            includeLowercase: Bool = true,
            includeDigits: Bool = true,
            includeSymbols: Bool = true
        ) {
            self.length = length
            self.includeUppercase = includeUppercase
            self.includeLowercase = includeLowercase
            self.includeDigits = includeDigits
            self.includeSymbols = includeSymbols
        }

        public var hasAnyClass: Bool {
            return includeUppercase || includeLowercase || includeDigits || includeSymbols
        }

        var enabledPools: [String] {
            var pools: [String] = []
            if includeUppercase {
                pools.append(PasswordGenerator.uppercase)
            }
            if includeLowercase {
                pools.append(PasswordGenerator.lowercase)
            }
            if includeDigits {
                pools.append(PasswordGenerator.digits)
            }
            if includeSymbols {
                pools.append(PasswordGenerator.symbols)
            }
            return pools
        }
    }

    /// Generate one password.
    ///
    /// Guarantees at least one character from every enabled class, then fills the
    /// rest from the combined pool and shuffles — the same construction Kotlin
    /// uses, so the distributions match.
    ///
    /// Returns `nil` when no class is enabled. The UI never allows that (the last
    /// toggle cannot be switched off), so it is a defensive case rather than a
    /// reachable one.
    public static func generate(_ options: Options) -> String? {
        let pools = options.enabledPools
        if pools.isEmpty {
            return nil
        }
        let length = max(options.length, pools.count)
        var combined = ""
        for pool in pools {
            combined += pool
        }

        var characters: [Character] = []
        characters.reserveCapacity(length)
        // One guaranteed character per enabled class.
        for pool in pools {
            characters.append(randomCharacter(from: pool))
        }
        while characters.count < length {
            characters.append(randomCharacter(from: combined))
        }
        shuffle(&characters)
        return String(characters)
    }

    /// Generate a password that also satisfies a category's own rules, redrawing
    /// rather than patching. Falls through with the last draw if every attempt
    /// fails — the form still shows which rule failed, so nothing is silently
    /// accepted.
    public static func generate(_ options: Options, constrainedTo category: ItemCategory?) -> String? {
        guard var candidate = generate(options) else {
            return nil
        }
        guard let category = category, category == .bank else {
            return candidate
        }
        var attempt = 1
        while attempt < maxAttempts && !BankPasswordRules.violations(in: candidate).isEmpty {
            guard let next = generate(options) else {
                return candidate
            }
            candidate = next
            attempt += 1
        }
        return candidate
    }

    /// Shown under the password as "≈ N bits".
    ///
    /// DELIBERATE ANDROID PARITY QUIRK, twice over:
    ///
    /// 1. The symbol pool counts as 32 here, but `symbols` above is 26 characters
    ///    long. Android's `computeEntropyBits` uses 32 and its actual pool is the
    ///    same 26, so the number both apps display is equally optimistic. Fixing
    ///    it on one platform only would make the two disagree in front of the
    ///    user, which is worse than being consistently off.
    /// 2. The result is TRUNCATED, not rounded. `docs/IOS_PARITY.md` says
    ///    `round(...)`, but `PasswordGeneratorViewModel.kt` ends in `.toInt()`,
    ///    and at the default settings that is the difference between 104 and 105.
    ///    The shipped Android behaviour wins.
    ///
    /// Both are flagged for Track A to change on both sides at once.
    public static func entropyBits(_ options: Options) -> Int {
        var poolSize = 0
        if options.includeUppercase {
            poolSize += 26
        }
        if options.includeLowercase {
            poolSize += 26
        }
        if options.includeDigits {
            poolSize += 10
        }
        if options.includeSymbols {
            poolSize += 32
        }
        if poolSize <= 0 {
            return 0
        }
        let bits = Double(options.length) * (log(Double(poolSize)) / log(2.0))
        return Int(bits)
    }

    // MARK: - Randomness

    // `Int.random(in:)` draws from `SystemRandomNumberGenerator`, which is
    // documented as cryptographically secure and is backed by `arc4random_buf`
    // on Apple platforms — the counterpart of Kotlin's `SecureRandom`.

    private static func randomCharacter(from pool: String) -> Character {
        let characters = Array(pool)
        let index = Int.random(in: 0..<characters.count)
        return characters[index]
    }

    private static func shuffle(_ characters: inout [Character]) {
        // Fisher-Yates, same direction as the Kotlin loop.
        var index = characters.count - 1
        while index > 0 {
            let target = Int.random(in: 0...index)
            characters.swapAt(index, target)
            index -= 1
        }
    }
}

/// The bank password rules, mirrored from Android's `BankPasswordValidator`.
public enum BankPasswordRules {

    public static let minLength = 6
    public static let maxLength = 12
    /// How many retired passwords a new bank password is checked against.
    public static let historySize = 4

    public enum Violation: String, Equatable, Sendable, CaseIterable {
        case tooShort
        case tooLong
        case missingUppercase
        case missingLowercase
        case missingDigit
        case consecutiveSequence
        case repeatingCharacters
        case reusedPassword

        public var message: String {
            switch self {
            case .tooShort: return "At least \(BankPasswordRules.minLength) characters"
            case .tooLong: return "At most \(BankPasswordRules.maxLength) characters"
            case .missingUppercase: return "One uppercase letter"
            case .missingLowercase: return "One lowercase letter"
            case .missingDigit: return "One digit"
            case .consecutiveSequence: return "No runs like abc or 321"
            case .repeatingCharacters: return "No character three times in a row"
            case .reusedPassword: return "Not a previously used password"
            }
        }
    }

    /// Every rule this password breaks, or an empty list when it is acceptable.
    ///
    /// An EMPTY password returns no violations, exactly like Android: "empty" is
    /// handled by the required-field check, not by the complexity checker, so the
    /// form does not shout complexity rules at an untouched field.
    public static func violations(
        in password: String,
        previousPasswords: [String] = []
    ) -> [Violation] {
        if password.isEmpty {
            return []
        }

        let characters = Array(password)
        let allDigits = characters.allSatisfy { $0.isNumber }
        if allDigits {
            if characters.count < minLength {
                return [.tooShort]
            }
            if characters.count == minLength {
                // A PIN of exactly the minimum length is exempt from the
                // case/digit mix, but never from the pattern rules.
                var result: [Violation] = []
                if hasConsecutiveSequence(characters) {
                    result.append(.consecutiveSequence)
                }
                if hasRepeatingCharacters(characters) {
                    result.append(.repeatingCharacters)
                }
                if isReused(password, previousPasswords) {
                    result.append(.reusedPassword)
                }
                return result
            }
        }
        return complexViolations(characters, password: password, previousPasswords: previousPasswords)
    }

    private static func complexViolations(
        _ characters: [Character],
        password: String,
        previousPasswords: [String]
    ) -> [Violation] {
        var result: [Violation] = []
        if characters.count < minLength {
            result.append(.tooShort)
        }
        if characters.count > maxLength {
            result.append(.tooLong)
        }
        if !characters.contains(where: { $0.isUppercase }) {
            result.append(.missingUppercase)
        }
        if !characters.contains(where: { $0.isLowercase }) {
            result.append(.missingLowercase)
        }
        if !characters.contains(where: { $0.isNumber }) {
            result.append(.missingDigit)
        }
        if hasConsecutiveSequence(characters) {
            result.append(.consecutiveSequence)
        }
        if hasRepeatingCharacters(characters) {
            result.append(.repeatingCharacters)
        }
        if isReused(password, previousPasswords) {
            result.append(.reusedPassword)
        }
        return result
    }

    private static func isReused(_ password: String, _ previousPasswords: [String]) -> Bool {
        return previousPasswords.prefix(historySize).contains(password)
    }

    private static func hasConsecutiveSequence(_ characters: [Character]) -> Bool {
        if characters.count < 3 {
            return false
        }
        for index in 0...(characters.count - 3) {
            guard
                let first = characters[index].unicodeScalars.first,
                let second = characters[index + 1].unicodeScalars.first,
                let third = characters[index + 2].unicodeScalars.first
            else {
                continue
            }
            let firstStep = Int(second.value) - Int(first.value)
            let secondStep = Int(third.value) - Int(second.value)
            if firstStep == secondStep && (firstStep == 1 || firstStep == -1) {
                return true
            }
        }
        return false
    }

    private static func hasRepeatingCharacters(_ characters: [Character]) -> Bool {
        if characters.count < 3 {
            return false
        }
        for index in 0...(characters.count - 3) {
            if characters[index] == characters[index + 1] && characters[index + 1] == characters[index + 2] {
                return true
            }
        }
        return false
    }

    /// The retired passwords written back with a bank record.
    ///
    /// The password being saved is deliberately excluded — it already lives in
    /// `password`, and repeating it here would make the record fail its own
    /// reuse check the next time it is opened for editing.
    public static func retiredPasswords(
        newPassword: String,
        originalPassword: String,
        previousPasswords: [String]
    ) -> [String] {
        if newPassword == originalPassword {
            return previousPasswords
        }
        var result: [String] = []
        for candidate in [originalPassword] + previousPasswords {
            if candidate.isEmpty {
                continue
            }
            if result.contains(candidate) {
                continue
            }
            result.append(candidate)
        }
        return Array(result.prefix(historySize))
    }
}
