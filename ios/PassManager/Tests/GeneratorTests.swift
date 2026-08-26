import XCTest
import PassVaultCore
@testable import PassManager

final class GeneratorTests: XCTestCase {

    func testLengthIsHonoured() {
        for length in [8, 16, 32, 64] {
            var options = PasswordGenerator.Options()
            options.length = length
            let password = PasswordGenerator.generate(options)
            XCTAssertEqual(password?.count, length)
        }
    }

    func testDefaultLengthIsSixteen() {
        XCTAssertEqual(PasswordGenerator.Options().length, 16)
        XCTAssertEqual(PasswordGenerator.defaultLength, 16)
        XCTAssertEqual(PasswordGenerator.minLength, 8)
        XCTAssertEqual(PasswordGenerator.maxLength, 64)
    }

    /// The rule from `docs/IOS_PARITY.md`: the result must contain at least one
    /// character from every enabled class.
    func testEveryEnabledClassIsRepresented() {
        var options = PasswordGenerator.Options()
        options.length = 8
        for _ in 0..<200 {
            guard let password = PasswordGenerator.generate(options) else {
                XCTFail("expected a password")
                return
            }
            XCTAssertTrue(password.contains(where: { $0.isUppercase }), password)
            XCTAssertTrue(password.contains(where: { $0.isLowercase }), password)
            XCTAssertTrue(password.contains(where: { $0.isNumber }), password)
            XCTAssertTrue(
                password.contains(where: { PasswordGenerator.symbols.contains($0) }),
                password
            )
        }
    }

    func testDisabledClassesNeverAppear() {
        var options = PasswordGenerator.Options()
        options.length = 20
        options.includeUppercase = false
        options.includeSymbols = false

        for _ in 0..<100 {
            guard let password = PasswordGenerator.generate(options) else {
                XCTFail("expected a password")
                return
            }
            XCTAssertFalse(password.contains(where: { $0.isUppercase }), password)
            XCTAssertFalse(
                password.contains(where: { PasswordGenerator.symbols.contains($0) }),
                password
            )
        }
    }

    func testSingleClassStillWorks() {
        var options = PasswordGenerator.Options(
            length: 12,
            includeUppercase: false,
            includeLowercase: false,
            includeDigits: true,
            includeSymbols: false
        )
        let password = PasswordGenerator.generate(options)
        XCTAssertEqual(password?.count, 12)
        XCTAssertTrue(password?.allSatisfy { $0.isNumber } ?? false)

        options.includeDigits = false
        XCTAssertFalse(options.hasAnyClass)
        XCTAssertNil(PasswordGenerator.generate(options), "no class enabled means no password")
    }

    func testPasswordsAreNotRepeated() {
        let options = PasswordGenerator.Options()
        var seen = Set<String>()
        for _ in 0..<50 {
            if let password = PasswordGenerator.generate(options) {
                seen.insert(password)
            }
        }
        XCTAssertEqual(seen.count, 50, "generated passwords must not repeat")
    }

    /// Entropy must match Android's arithmetic exactly, quirks included: the
    /// symbol class counts as 32 (its real pool is 26) and the result is
    /// truncated rather than rounded.
    func testEntropyMatchesAndroid() {
        var options = PasswordGenerator.Options()
        options.length = 16
        // pool = 26 + 26 + 10 + 32 = 94; 16 * log2(94) = 104.87 -> 104
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 104)

        options.includeSymbols = false
        // pool = 62; 16 * log2(62) = 95.27 -> 95
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 95)

        options.length = 8
        options.includeUppercase = false
        options.includeSymbols = false
        // pool = 36; 8 * log2(36) = 41.36 -> 41
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 41)
    }

    func testEntropyIsZeroWithoutAnyClass() {
        let options = PasswordGenerator.Options(
            length: 16,
            includeUppercase: false,
            includeLowercase: false,
            includeDigits: false,
            includeSymbols: false
        )
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 0)
    }

    /// A generated bank password must satisfy the bank rules, or the generator
    /// hands the form something the form rejects on arrival.
    func testBankConstrainedOutputSatisfiesBankRules() {
        var options = PasswordGenerator.Options()
        options.length = BankPasswordRules.maxLength

        for _ in 0..<100 {
            guard let password = PasswordGenerator.generate(options, constrainedTo: .bank) else {
                XCTFail("expected a password")
                return
            }
            XCTAssertEqual(
                BankPasswordRules.violations(in: password),
                [],
                "generated bank password broke its own rules: \(password)"
            )
        }
    }

    func testUnconstrainedGenerationIsNotFilteredByBankRules() {
        var options = PasswordGenerator.Options()
        options.length = 32
        guard let password = PasswordGenerator.generate(options, constrainedTo: nil) else {
            XCTFail("expected a password")
            return
        }
        // 32 characters is far over the bank maximum, which is exactly why the
        // constraint has to be applied by the caller and not assumed.
        XCTAssertEqual(password.count, 32)
        XCTAssertTrue(BankPasswordRules.violations(in: password).contains(.tooLong))
    }

    func testCharacterPoolsMatchAndroid() {
        XCTAssertEqual(PasswordGenerator.uppercase, "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        XCTAssertEqual(PasswordGenerator.lowercase, "abcdefghijklmnopqrstuvwxyz")
        XCTAssertEqual(PasswordGenerator.digits, "0123456789")
        XCTAssertEqual(PasswordGenerator.symbols, "!@#$%^&*()-_=+[]{}|;:,.<>?")
    }
}
