import XCTest
import Foundation
import Security
import LocalAuthentication
@testable import PassManager

/// The classification split is the whole value of this layer, so it is what gets
/// tested. Reading and writing real Keychain items is not exercised here: a
/// `WhenPasscodeSetThisDeviceOnly` item cannot even be created on a simulator
/// with no device passcode, which every CI simulator is.
final class DeviceKeyErrorTests: XCTestCase {

    // MARK: - OSStatus classification

    func testUserCancellationIsNotAnError() {
        let error = KeychainVaultStore.classify(errSecUserCanceled)
        XCTAssertEqual(error, .cancelled)
        XCTAssertTrue(error.isCancellation)
        XCTAssertFalse(error.requiresReEnrolment)
        // Nothing is shown for a cancel; the user chose to back out.
        XCTAssertEqual(error.message, "")
    }

    /// The distinction this layer exists to make: a failed match must NOT throw
    /// away a working enrolment.
    func testFailedMatchIsTransient() {
        let error = KeychainVaultStore.classify(errSecAuthFailed)
        XCTAssertEqual(error, .transient(reason: .authenticationFailed))
        XCTAssertFalse(error.requiresReEnrolment)
        XCTAssertFalse(error.message.isEmpty)
    }

    /// A missing item on the BIOMETRIC read means the system destroyed it because
    /// the enrolled biometrics changed — that is how `.biometryCurrentSet`
    /// behaves, and it is permanent.
    func testMissingBiometricItemMeansBiometricsChanged() {
        let error = KeychainVaultStore.classify(errSecItemNotFound, isBiometricRead: true)
        XCTAssertEqual(error, .permanentlyInvalidated(reason: .biometricsChanged))
        XCTAssertTrue(error.requiresReEnrolment)
    }

    /// The same status outside that context just means nothing was stored, which
    /// must not be reported as "your biometrics changed".
    func testMissingItemElsewhereMeansNotEnrolled() {
        let error = KeychainVaultStore.classify(errSecItemNotFound)
        XCTAssertEqual(error, .permanentlyInvalidated(reason: .notEnrolled))
        XCTAssertTrue(error.requiresReEnrolment)
    }

    func testLockedDeviceIsTransient() {
        let error = KeychainVaultStore.classify(errSecInteractionNotAllowed)
        XCTAssertEqual(error, .transient(reason: .interactionNotAllowed))
        XCTAssertFalse(error.requiresReEnrolment)
    }

    /// No passcode means the protection class this app insists on is
    /// unavailable — a setup problem, not a failure to re-enrol.
    func testNoPasscodeIsUnavailableRatherThanInvalidated() {
        let error = KeychainVaultStore.classify(errSecNotAvailable)
        XCTAssertEqual(error, .unavailable(reason: .noPasscodeSet))
        XCTAssertFalse(error.requiresReEnrolment)
        XCTAssertTrue(error.message.contains("passcode"))
    }

    func testUnknownStatusIsCarriedVerbatim() {
        let error = KeychainVaultStore.classify(OSStatus(-99999))
        XCTAssertEqual(error, .unexpected(status: -99999))
        XCTAssertFalse(error.requiresReEnrolment)
        // The code survives into the message, so a bug report says which one.
        XCTAssertTrue(error.message.contains("-99999"))
    }

    /// Every classified status must land in exactly one bucket and produce a
    /// message, so no path can reach the UI with an empty string.
    func testEveryClassifiedStatusProducesAMessage() {
        let statuses: [OSStatus] = [
            errSecItemNotFound, errSecAuthFailed, errSecInteractionNotAllowed,
            errSecNotAvailable, errSecDecode, errSecInvalidItemRef, OSStatus(-1)
        ]
        for status in statuses {
            let error = KeychainVaultStore.classify(status)
            XCTAssertFalse(error.isCancellation, "\(status)")
            XCTAssertFalse(error.message.isEmpty, "\(status)")
        }
    }

    // MARK: - LAError classification

    func testCancellationsFromLocalAuthentication() {
        for code in [LAError.Code.userCancel, .systemCancel, .appCancel, .userFallback] {
            XCTAssertEqual(KeychainVaultStore.classify(LAError(code)), .cancelled, "\(code)")
        }
    }

    func testBiometryChangesArePermanent() {
        XCTAssertEqual(
            KeychainVaultStore.classify(LAError(.biometryNotEnrolled)),
            .permanentlyInvalidated(reason: .biometricsChanged)
        )
        XCTAssertEqual(
            KeychainVaultStore.classify(LAError(.passcodeNotSet)),
            .permanentlyInvalidated(reason: .passcodeRemoved)
        )
    }

    /// Lockout after too many attempts is recoverable — the key is untouched.
    func testLockoutIsTransient() {
        let error = KeychainVaultStore.classify(LAError(.biometryLockout))
        XCTAssertEqual(error, .transient(reason: .biometryLockout))
        XCTAssertFalse(error.requiresReEnrolment)
    }

    func testFailedAuthenticationFromLocalAuthenticationIsTransient() {
        let error = KeychainVaultStore.classify(LAError(.authenticationFailed))
        XCTAssertEqual(error, .transient(reason: .authenticationFailed))
        XCTAssertFalse(error.requiresReEnrolment)
    }

    // MARK: - Configuration

    /// The two items must live under distinct accounts, or enrolling biometrics
    /// would overwrite the wrapped key.
    func testAccountsAreDistinct() {
        XCTAssertNotEqual(
            KeychainVaultStore.Account.wrappedVaultKey.rawValue,
            KeychainVaultStore.Account.biometricVaultKey.rawValue
        )
        XCTAssertFalse(KeychainVaultStore.service.isEmpty)
    }
}
