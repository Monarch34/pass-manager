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

    /// `round(length * log2(actual pool size))`, with the pool taken from the
    /// real character sets: 26 + 26 + 10 + 26 = 88.
    func testEntropyUsesTheRealPoolSize() {
        var options = PasswordGenerator.Options()
        options.length = 16
        // 16 * log2(88) = 103.35 -> 103. The old Android-parity arithmetic
        // counted symbols as 32 and truncated, advertising 104.
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 103)

        options.includeSymbols = false
        // pool = 62; 16 * log2(62) = 95.27 -> 95
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 95)

        options.length = 8
        options.includeUppercase = false
        options.includeSymbols = false
        // pool = 36; 8 * log2(36) = 41.36 -> 41
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 41)
    }

    /// Rounds, never truncates. These are lengths where the two disagree, so a
    /// regression back to `Int(bits)` fails here instead of passing quietly.
    func testEntropyRoundsRatherThanTruncating() {
        var options = PasswordGenerator.Options()

        options.length = 10
        // 10 * log2(88) = 64.59 -> 65, not 64
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 65)

        options.length = 12
        // 12 * log2(88) = 77.51 -> 78, not 77
        XCTAssertEqual(PasswordGenerator.entropyBits(options), 78)

        let digitsOnly = PasswordGenerator.Options(
            length: 8,
            includeUppercase: false,
            includeLowercase: false,
            includeDigits: true,
            includeSymbols: false
        )
        // 8 * log2(10) = 26.58 -> 27, not 26
        XCTAssertEqual(PasswordGenerator.entropyBits(digitsOnly), 27)
    }

    /// The number on screen must never claim more than the generator delivers.
    /// Rounding may add at most half a bit; more than that means the arithmetic
    /// has drifted away from the pools it describes.
    func testEntropyNeverOverstatesTheRealPool() {
        for includeSymbols in [true, false] {
            for length in [8, 16, 32, 64] {
                var options = PasswordGenerator.Options()
                options.length = length
                options.includeSymbols = includeSymbols

                var realPool = 0
                for pool in options.enabledPools {
                    realPool += pool.count
                }
                let exact = Double(length) * (log(Double(realPool)) / log(2.0))
                let shown = Double(PasswordGenerator.entropyBits(options))

                XCTAssertLessThanOrEqual(
                    shown - exact,
                    0.5,
                    "length \(length), symbols \(includeSymbols): shown \(shown) vs real \(exact)"
                )
            }
        }
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
