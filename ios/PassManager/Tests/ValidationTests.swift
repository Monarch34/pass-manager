import XCTest
import PassVaultCore
@testable import PassManager

final class ValidationTests: XCTestCase {

    // MARK: - Title

    func testTitleIsRequiredForEveryCategory() {
        for category in ItemCategory.allCases {
            var form = ItemFormSnapshot()
            form.category = category
            form.title = "   "
            XCTAssertEqual(ItemFormValidator.failure(for: form), .titleRequired, "\(category)")
        }
    }

    // MARK: - Login

    func testLoginRequiresAPassword() {
        var form = ItemFormSnapshot()
        form.category = .login
        form.title = "GitHub"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .passwordRequired)

        form.password = "hunter2"
        XCTAssertNil(ItemFormValidator.failure(for: form))
        XCTAssertTrue(ItemFormValidator.canSave(form))
    }

    // MARK: - Note and identity

    /// Notes and identities have no required field beyond the title.
    func testNoteAndIdentityNeedOnlyATitle() {
        for category in [ItemCategory.note, ItemCategory.identity] {
            var form = ItemFormSnapshot()
            form.category = category
            form.title = "Something"
            XCTAssertNil(ItemFormValidator.failure(for: form), "\(category)")
        }
    }

    // MARK: - Card

    func testCardNumberMustBeSixteenDigits() {
        var form = ItemFormSnapshot()
        form.category = .card
        form.title = "Visa"
        form.cardExpiry = "1230"

        form.cardNumber = "411111111111111"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .cardPanInvalid)

        form.cardNumber = "41111111111111111"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .cardPanInvalid)

        form.cardNumber = "4111111111111111"
        XCTAssertNil(ItemFormValidator.failure(for: form))
    }

    func testCardNumberIgnoresFormattingCharacters() {
        XCTAssertEqual(CardRules.panDigitsOnly("4111 1111-1111 1111"), "4111111111111111")
        XCTAssertTrue(CardRules.isPanAcceptableForSave(CardRules.panDigitsOnly("4111 1111 1111 1111")))
    }

    func testExpiryMustBeAValidMonth() {
        var form = ItemFormSnapshot()
        form.category = .card
        form.title = "Visa"
        form.cardNumber = "4111111111111111"

        form.cardExpiry = "1330"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .cardExpiryInvalid)

        form.cardExpiry = "0030"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .cardExpiryInvalid)

        form.cardExpiry = "123"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .cardExpiryInvalid)

        form.cardExpiry = "1230"
        XCTAssertNil(ItemFormValidator.failure(for: form))
    }

    func testExpiryFormatting() {
        XCTAssertEqual(CardRules.formatExpiry(month: 1, year: 2030), "01/30")
        XCTAssertEqual(CardRules.formatExpiry(month: 12, year: 2027), "12/27")
        XCTAssertEqual(CardRules.sanitizeExpiryDigits("12/30"), "1230")
        XCTAssertEqual(CardRules.sanitizeExpiryDigits("123456"), "1234")
    }

    /// Six-digit `MM/YYYY` is accepted for data saved by older builds.
    func testStoredExpiryRoundTrips() {
        XCTAssertEqual(CardRules.expiryFieldDigits(fromStored: "12/30"), "1230")
        XCTAssertEqual(CardRules.expiryFieldDigits(fromStored: "12/2030"), "1230")
    }

    /// A short CVC is a warning, NOT a save gate — Android does not block on it.
    func testWeakCvcDoesNotBlockSaving() {
        var form = ItemFormSnapshot()
        form.category = .card
        form.title = "Visa"
        form.cardNumber = "4111111111111111"
        form.cardExpiry = "1230"
        form.cardCvc = "1"

        XCTAssertTrue(CardRules.isCvcWeak("1"))
        XCTAssertFalse(CardRules.isCvcWeak("123"))
        XCTAssertFalse(CardRules.isCvcWeak(""))
        XCTAssertNil(ItemFormValidator.failure(for: form), "a weak CVC must not block the save")
    }

    // MARK: - Bank

    func testBankRequiresAPassword() {
        var form = ItemFormSnapshot()
        form.category = .bank
        form.title = "Ziraat"
        XCTAssertEqual(ItemFormValidator.failure(for: form), .passwordRequired)
    }

    func testBankPasswordComplexity() {
        // Valid: 6-12, upper + lower + digit, no run of three, no triple.
        XCTAssertEqual(BankPasswordRules.violations(in: "Bnk7Xq2"), [])

        XCTAssertTrue(BankPasswordRules.violations(in: "Ab1").contains(.tooShort))
        XCTAssertTrue(BankPasswordRules.violations(in: "Abcdefgh1jklmn").contains(.tooLong))
        XCTAssertTrue(BankPasswordRules.violations(in: "abcx7q").contains(.missingUppercase))
        XCTAssertTrue(BankPasswordRules.violations(in: "ABCX7Q").contains(.missingLowercase))
        XCTAssertTrue(BankPasswordRules.violations(in: "AbXqZw").contains(.missingDigit))
    }

    func testBankPasswordRejectsRunsAndRepeats() {
        XCTAssertTrue(BankPasswordRules.violations(in: "Xabc7Q").contains(.consecutiveSequence))
        XCTAssertTrue(BankPasswordRules.violations(in: "X321qQ").contains(.consecutiveSequence))
        XCTAssertTrue(BankPasswordRules.violations(in: "Xaaa7Q").contains(.repeatingCharacters))
    }

    /// An empty password produces NO complexity violations — "required" is a
    /// separate check, so an untouched field does not shout rules at the user.
    func testEmptyBankPasswordHasNoViolations() {
        XCTAssertEqual(BankPasswordRules.violations(in: ""), [])
    }

    /// An all-digit PIN of exactly the minimum length is exempt from the
    /// case/digit mix, but never from the pattern rules.
    func testAllDigitPinAtMinimumLength() {
        XCTAssertEqual(BankPasswordRules.violations(in: "759318"), [])
        XCTAssertTrue(BankPasswordRules.violations(in: "123456").contains(.consecutiveSequence))
        XCTAssertTrue(BankPasswordRules.violations(in: "711138").contains(.repeatingCharacters))
        XCTAssertTrue(BankPasswordRules.violations(in: "7593").contains(.tooShort))
    }

    func testBankPasswordRejectsReuse() {
        let violations = BankPasswordRules.violations(
            in: "Bnk7Xq2",
            previousPasswords: ["Bnk7Xq2", "Old9Zw3"]
        )
        XCTAssertTrue(violations.contains(.reusedPassword))
    }

    /// Only the first four retired passwords are checked.
    func testReuseChecksOnlyTheHistoryWindow() {
        let history = ["A1", "B2", "C3", "D4", "Bnk7Xq2"]
        let violations = BankPasswordRules.violations(in: "Bnk7Xq2", previousPasswords: history)
        XCTAssertFalse(violations.contains(.reusedPassword))
    }

    /// An unchanged bank password must not be pushed into its own history, or
    /// re-saving an untouched record would fail its own reuse check.
    func testUnchangedPasswordDoesNotEnterHistory() {
        let retired = BankPasswordRules.retiredPasswords(
            newPassword: "Same1Xq",
            originalPassword: "Same1Xq",
            previousPasswords: ["Older1X"]
        )
        XCTAssertEqual(retired, ["Older1X"])
        XCTAssertFalse(retired.contains("Same1Xq"))
    }

    func testChangedPasswordRetiresTheOldOne() {
        let retired = BankPasswordRules.retiredPasswords(
            newPassword: "New2Zwq",
            originalPassword: "Old1Xqp",
            previousPasswords: ["Older1X"]
        )
        XCTAssertEqual(retired, ["Old1Xqp", "Older1X"])
    }

    func testHistoryIsCappedAtFour() {
        let retired = BankPasswordRules.retiredPasswords(
            newPassword: "New2Zwq",
            originalPassword: "P0",
            previousPasswords: ["P1", "P2", "P3", "P4", "P5"]
        )
        XCTAssertEqual(retired.count, BankPasswordRules.historySize)
        XCTAssertEqual(retired, ["P0", "P1", "P2", "P3"])
    }

    // MARK: - Payload construction

    func testPayloadBuildsForEveryCategory() {
        for category in ItemCategory.allCases {
            var form = ItemFormSnapshot()
            form.category = category
            form.title = "Title"
            switch category {
            case .login:
                form.password = "pw"
            case .card:
                form.cardNumber = "4111111111111111"
                form.cardExpiry = "1230"
            case .bank:
                form.bankPassword = "Bnk7Xq2"
            case .note, .identity:
                break
            }

            let result = ItemFormValidator.makePayload(from: form, id: "id-1")
            switch result {
            case .success(let payload):
                XCTAssertEqual(payload.category, category)
                XCTAssertEqual(payload.id, "id-1")
                XCTAssertEqual(payload.title, "Title")
            case .failure(let failure):
                XCTFail("\(category) failed to build: \(failure)")
            }
        }
    }

    func testCardPayloadNormalisesExpiryToSlashForm() {
        var form = ItemFormSnapshot()
        form.category = .card
        form.title = "Visa"
        form.cardNumber = "4111111111111111"
        form.cardExpiry = "0330"

        guard case .success(let payload) = ItemFormValidator.makePayload(from: form, id: "c") else {
            XCTFail("expected success")
            return
        }
        guard case .card(let card) = payload else {
            XCTFail("expected a card")
            return
        }
        XCTAssertEqual(card.cardExpiry, "03/30")
    }

    func testFormRoundTripsThroughAPayload() {
        var form = ItemFormSnapshot()
        form.category = .bank
        form.title = "Ziraat"
        form.bankName = "Ziraat Bankası"
        form.accountNumber = "TR00"
        form.bankPassword = "Bnk7Xq2"

        guard case .success(let payload) = ItemFormValidator.makePayload(from: form, id: "b") else {
            XCTFail("expected success")
            return
        }
        let rebuilt = ItemFormValidator.makeForm(from: payload)
        XCTAssertEqual(rebuilt.title, "Ziraat")
        XCTAssertEqual(rebuilt.bankName, "Ziraat Bankası")
        XCTAssertEqual(rebuilt.bankPassword, "Bnk7Xq2")
        // The loaded password becomes the "original", so re-saving untouched is
        // still valid.
        XCTAssertEqual(rebuilt.originalBankPassword, "Bnk7Xq2")
        XCTAssertNil(ItemFormValidator.failure(for: rebuilt))
    }

    // MARK: - Strength

    func testPasswordStrengthMirrorsAndroidScoring() {
        XCTAssertEqual(PasswordStrength.evaluate(""), .weak)
        XCTAssertEqual(PasswordStrength.evaluate("abc"), .weak)
        // 8+ chars and lower only -> score 1 -> weak
        XCTAssertEqual(PasswordStrength.evaluate("abcdefgh"), .weak)
        // 8+, mixed case -> score 2 -> fair
        XCTAssertEqual(PasswordStrength.evaluate("abcdefgH"), .fair)
        // 8+, mixed case, digit -> score 3 -> good
        XCTAssertEqual(PasswordStrength.evaluate("abcdefG1"), .good)
        // 8+, mixed case, digit, symbol -> score 4 -> strong
        XCTAssertEqual(PasswordStrength.evaluate("abcdeF1!"), .strong)
        // 14+ adds another point
        XCTAssertEqual(PasswordStrength.evaluate("abcdefghijklmnoP1!"), .strong)
    }
}
