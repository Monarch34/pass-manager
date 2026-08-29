import Foundation
import LocalAuthentication
import PassManagerKit
import Security

/// The vault key, kept in the Keychain behind Face ID or Touch ID.
///
/// This is the second slot of the two-key model made real. There is one vault key; the
/// passphrase path derives a key that unwraps it, and this path holds it directly behind a
/// biometric gate. Neither can produce the other, and turning this off removes one way in
/// without touching the vault.
///
/// ### Why the Keychain and not a file we encrypt ourselves
///
/// The check is enforced by the Keychain, not by this code. An access control built with
/// `.biometryCurrentSet` means `SecItemCopyMatching` itself refuses to return the bytes
/// until the system has authenticated the user — so an attacker with the device and a
/// debugger cannot skip past an `if` statement, because there is no `if` statement to skip.
/// An app that read a flag and then decided for itself would be doing exactly that.
///
/// ### The two attributes doing the work
///
/// `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` keeps the item out of every backup and
/// off every other device, and makes it exist at all only while a passcode is set — removing
/// the passcode destroys it rather than leaving it readable.
///
/// `.biometryCurrentSet`, rather than `.biometryAny`, invalidates the item the moment a face
/// or fingerprint is added or removed. That is the whole point: someone who knows the device
/// passcode can enrol their own face, and with `.biometryAny` that would hand them the vault.
/// With this, enrolling destroys the key and the passphrase becomes the only way back in.
enum BiometricVaultKey {

    private static let service = "com.passmanager.ios.vault"
    private static let account = "biometric-vault-key"

    /// Whether the user has turned this on. Not a secret, and deliberately not in the
    /// Keychain: reading a protected item to find out whether it exists would prompt for a
    /// face just to draw a button.
    private static let enabledKey = "biometric-unlock-enabled"

    enum Failure: Error, Equatable {
        case unavailable
        case notEnrolled
        /// Too many failed attempts. The system requires the passcode before biometry works
        /// again, which this app cannot and should not ask for.
        case lockedOut
        /// A face or fingerprint was added or removed, so the stored key was destroyed.
        case enrolmentChanged
        case cancelled
        case notStored
        case keychain(OSStatus)

        var message: String {
            switch self {
            case .unavailable:
                return "This device cannot use biometric unlock."
            case .notEnrolled:
                return "No face or fingerprint is set up on this device."
            case .lockedOut:
                return "Too many attempts. Unlock the device with its passcode first."
            case .enrolmentChanged:
                return "Face or fingerprint enrolment changed, so the saved key was discarded. Unlock with your passphrase to set it up again."
            case .cancelled:
                return ""
            case .notStored:
                return "Biometric unlock is not set up for this vault."
            case .keychain(let status):
                return "The keychain refused the request (\(status))."
            }
        }
    }

    // MARK: - Availability

    static var isAvailable: Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    /// "Face ID" or "Touch ID", so the button says what the user's device actually does.
    static var name: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        default: return "Biometrics"
        }
    }

    static var isEnabled: Bool {
        UserDefaults.standard.bool(forKey: enabledKey)
    }

    // MARK: - Storing and loading

    /// Saves the vault key behind a biometric gate. Replaces any key already there.
    static func store(_ vaultKey: Secret) -> Result<Void, Failure> {
        var accessError: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            .biometryCurrentSet,
            &accessError
        ) else {
            return .failure(.unavailable)
        }

        remove()

        var data = vaultKey.toByteArray().swiftData
        defer {
            // The Keychain has its own copy by now; this one should not linger.
            data.resetBytes(in: 0..<data.count)
        }

        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessControl as String: access,
        ]

        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { return .failure(.keychain(status)) }

        UserDefaults.standard.set(true, forKey: enabledKey)
        return .success(())
    }

    /// Prompts for biometry and returns the vault key. The caller owns and must destroy it.
    static func load() -> Result<Secret, Failure> {
        guard isEnabled else { return .failure(.notStored) }

        let context = LAContext()
        context.localizedReason = "Unlock your vault"
        // No passcode fallback. The passphrase is this app's fallback, and it is the one
        // that actually protects the vault; offering the device passcode instead would let
        // anyone who knows it open a vault they do not have the passphrase for.
        context.localizedFallbackTitle = ""

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: context,
        ]

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        switch status {
        case errSecSuccess:
            guard let data = result as? Data else { return .failure(.keychain(status)) }
            return .success(Secret.companion.adopt(bytes: data.kotlinBytes))
        case errSecItemNotFound:
            // The flag says it was stored and the Keychain says it is gone, which is what
            // `.biometryCurrentSet` does when enrolment changes. Clear the flag so the app
            // stops offering a door that no longer exists.
            forget()
            return .failure(.enrolmentChanged)
        case errSecUserCanceled:
            return .failure(.cancelled)
        case errSecAuthFailed:
            return .failure(.lockedOut)
        default:
            return .failure(.keychain(status))
        }
    }

    /// Turns biometric unlock off and destroys the stored key.
    static func remove() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        forget()
    }

    private static func forget() {
        UserDefaults.standard.set(false, forKey: enabledKey)
    }
}
