import Foundation
import Security
import LocalAuthentication
import PassVaultCore

/// Why a Keychain or biometric operation failed, split by what the user should
/// do about it.
///
/// The split is the whole point. Android's A2 layer learned this the hard way:
/// a transient `errSecAuthFailed` and a permanent "the user re-enrolled their
/// face" both surface as "authentication failed", and treating the first like
/// the second throws away a working enrolment and makes the app look broken.
public enum DeviceKeyError: Error, Equatable {

    /// The enrolment is gone for good and must be set up again from the
    /// passphrase. The stored item is already useless; delete it and offer
    /// re-enrolment.
    case permanentlyInvalidated(reason: PermanentReason)

    /// Try again. The key material is intact.
    case transient(reason: TransientReason)

    /// The user cancelled, or fell back to the passphrase. Not an error to
    /// report — just go back to the passphrase field.
    case cancelled

    /// The device cannot do this at all.
    case unavailable(reason: UnavailableReason)

    /// An OSStatus this layer does not classify. Carried verbatim so a CI log or
    /// a bug report says which one, instead of "something went wrong".
    case unexpected(status: OSStatus)

    public enum PermanentReason: String, Sendable {
        /// Face/Touch ID enrolment changed — the `.biometryCurrentSet` ACL
        /// invalidated the item. Mirrors Android's
        /// `setInvalidatedByBiometricEnrollment`.
        case biometricsChanged
        /// The device passcode was removed, which destroys every
        /// `WhenPasscodeSet` item.
        case passcodeRemoved
        /// Nothing stored under this account.
        case notEnrolled
    }

    public enum TransientReason: String, Sendable {
        case authenticationFailed
        case biometryLockout
        case interactionNotAllowed
    }

    public enum UnavailableReason: String, Sendable {
        case noPasscodeSet
        case noBiometricHardware
        case biometryNotEnrolled
    }

    /// Whether the caller should drop the stored enrolment and ask the user to
    /// set it up again.
    public var requiresReEnrolment: Bool {
        if case .permanentlyInvalidated = self {
            return true
        }
        return false
    }

    public var isCancellation: Bool {
        return self == .cancelled
    }

    /// What to put in front of the user. Deliberately never a catastrophe
    /// message for a transient failure.
    public var message: String {
        switch self {
        case .permanentlyInvalidated(let reason):
            switch reason {
            case .biometricsChanged:
                return "Face ID has changed on this device, so the saved key was discarded. "
                    + "Unlock with your passphrase to enable Face ID again."
            case .passcodeRemoved:
                return "The device passcode was removed, so the saved key was discarded. "
                    + "Unlock with your passphrase to enable Face ID again."
            case .notEnrolled:
                return "Face ID is not set up for this vault yet."
            }
        case .transient(let reason):
            switch reason {
            case .authenticationFailed:
                return "Face ID did not recognise you. Try again, or use your passphrase."
            case .biometryLockout:
                return "Too many failed attempts. Use your passphrase to unlock."
            case .interactionNotAllowed:
                return "The device is locked. Try again once it is unlocked."
            }
        case .cancelled:
            return ""
        case .unavailable(let reason):
            switch reason {
            case .noPasscodeSet:
                return "Set a device passcode to use this vault's secure storage."
            case .noBiometricHardware:
                return "This device has no Face ID or Touch ID."
            case .biometryNotEnrolled:
                return "No face or fingerprint is enrolled on this device."
            }
        case .unexpected(let status):
            return "Secure storage failed (code \(status))."
        }
    }
}

/// The device-bound half of the key architecture, per `docs/IOS_PARITY.md`
/// "Key architecture" item 5.
///
/// Two Keychain items, deliberately with different protection:
///
/// | item | holds | protection |
/// |---|---|---|
/// | `wrappedVaultKey` | the ALREADY-wrapped vault key blob | `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` |
/// | `biometricVaultKey` | the RAW vault key | same, plus `SecAccessControl(.biometryCurrentSet)` |
///
/// `WhenPasscodeSetThisDeviceOnly` gives the three properties the contract asks
/// for at once: it requires a device passcode, it never leaves this device, and
/// it is excluded from every backup. `.biometryCurrentSet` is the counterpart of
/// Android's `setInvalidatedByBiometricEnrollment` — enrol a new face and the
/// item is destroyed by the system rather than left readable.
///
/// NOTHING here may ever reach a `.pmvault` file. That is not a convention, it
/// is what makes the export format portable at all, and there is a test asserting
/// it.
public enum KeychainVaultStore {

    public static let service = "com.passmanager.ios.vault"

    /// The protection class every item is written with.
    ///
    /// Normally `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`, which is what
    /// the contract asks for — and which a simulator with NO device passcode
    /// cannot satisfy, so `SecItemAdd` fails with `errSecNotAvailable` and no
    /// vault can be created at all. That is correct on a real device and a hard
    /// stop for an automated screenshot run.
    ///
    /// In a DEBUG build launched with the relaxed flag it drops to
    /// `AfterFirstUnlockThisDeviceOnly`: still device-only, still excluded from
    /// backups, but not requiring a passcode. This changes ONLY which constant is
    /// passed — every surrounding code path is the shipping one — and in release
    /// the branch does not exist in the binary.
    static var accessibility: CFString {
        #if DEBUG
        if UITestMode.wantsRelaxedKeychain {
            return kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        }
        #endif
        return kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
    }

    public enum Account: String {
        /// The wrapped blob. Reading it needs no biometric prompt — it is still
        /// encrypted under the passphrase-derived KEK.
        case wrappedVaultKey = "wrapped-vault-key"
        /// The raw vault key, readable only behind a successful biometric check.
        case biometricVaultKey = "biometric-vault-key"
    }

    // MARK: - Availability

    /// Whether biometric enrolment can even be offered.
    public static func biometryAvailability(context: LAContext = LAContext())
        -> Result<Void, DeviceKeyError> {
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            return .success(())
        }
        guard let nsError = error, let code = LAError.Code(rawValue: nsError.code) else {
            return .failure(.unavailable(reason: .noBiometricHardware))
        }
        switch code {
        case .biometryNotEnrolled:
            return .failure(.unavailable(reason: .biometryNotEnrolled))
        case .passcodeNotSet:
            return .failure(.unavailable(reason: .noPasscodeSet))
        case .biometryLockout:
            return .failure(.transient(reason: .biometryLockout))
        default:
            return .failure(.unavailable(reason: .noBiometricHardware))
        }
    }

    // MARK: - Wrapped key (no biometric prompt)

    public static func saveWrappedKey(_ blob: Data) -> Result<Void, DeviceKeyError> {
        var query = baseQuery(.wrappedVaultKey)
        query[kSecAttrAccessible as String] = accessibility

        SecItemDelete(baseQuery(.wrappedVaultKey) as CFDictionary)
        query[kSecValueData as String] = blob

        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecSuccess {
            return .success(())
        }
        return .failure(classify(status))
    }

    public static func loadWrappedKey() -> Result<Data, DeviceKeyError> {
        var query = baseQuery(.wrappedVaultKey)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data {
            return .success(data)
        }
        if status == errSecItemNotFound {
            return .failure(.permanentlyInvalidated(reason: .notEnrolled))
        }
        return .failure(classify(status))
    }

    // MARK: - Biometric key

    /// Store the RAW vault key behind a biometric access control.
    ///
    /// The ACL is built with `.biometryCurrentSet`, so the item dies the moment
    /// the enrolled biometrics change. Writing it needs no prompt; reading it
    /// always does.
    public static func enrolBiometrics(vaultKey: Data) -> Result<Void, DeviceKeyError> {
        var accessError: Unmanaged<CFError>?
        let control = SecAccessControlCreateWithFlags(
            nil,
            accessibility,
            .biometryCurrentSet,
            &accessError
        )
        guard let control = control else {
            accessError?.release()
            return .failure(.unavailable(reason: .noBiometricHardware))
        }

        SecItemDelete(baseQuery(.biometricVaultKey) as CFDictionary)

        var query = baseQuery(.biometricVaultKey)
        query[kSecAttrAccessControl as String] = control
        query[kSecValueData as String] = vaultKey

        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecSuccess {
            return .success(())
        }
        return .failure(classify(status))
    }

    /// Read the raw vault key, prompting for Face ID / Touch ID.
    ///
    /// `context` is injectable so the caller can set the fallback button title
    /// and reuse an already-evaluated context.
    public static func loadBiometricKey(
        prompt: String,
        context: LAContext = LAContext()
    ) -> Result<Data, DeviceKeyError> {
        // The reason string goes on the context rather than through
        // `kSecUseOperationPrompt`, which is the deprecated spelling of the same
        // thing.
        context.localizedReason = prompt

        var query = baseQuery(.biometricVaultKey)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecUseAuthenticationContext as String] = context

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data {
            return .success(data)
        }
        return .failure(classify(status, isBiometricRead: true))
    }

    /// Drop the biometric enrolment. Called on disable, on passphrase change and
    /// whenever a read reports the enrolment is permanently invalid.
    @discardableResult
    public static func removeBiometricEnrolment() -> Bool {
        let status = SecItemDelete(baseQuery(.biometricVaultKey) as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    /// Whether an enrolment exists, WITHOUT prompting.
    ///
    /// Asks only for the attributes, never `kSecReturnData`, so no biometric
    /// sheet appears — the settings screen must be able to render its toggle
    /// without demanding a face.
    public static func hasBiometricEnrolment() -> Bool {
        // Attributes only, never `kSecReturnData` — decrypting the data is what
        // triggers the prompt, so this query cannot raise one.
        var query = baseQuery(.biometricVaultKey)
        query[kSecReturnAttributes as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        // `errSecInteractionNotAllowed` means "it is there, but you would have to
        // authenticate" — which is a yes.
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    /// Remove everything this app stored. Used when the vault is reset.
    public static func removeAll() {
        SecItemDelete(baseQuery(.wrappedVaultKey) as CFDictionary)
        SecItemDelete(baseQuery(.biometricVaultKey) as CFDictionary)
    }

    // MARK: - Classification

    private static func baseQuery(_ account: Account) -> [String: Any] {
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account.rawValue
        ]
    }

    /// Map an `OSStatus` onto the permanent / transient / cancelled split.
    ///
    /// `isBiometricRead` matters: `errSecItemNotFound` on the biometric item
    /// means the enrolment is gone — which is exactly what the system does when
    /// biometrics change, since it destroys the item rather than failing the
    /// read. Outside that context the same status just means "nothing stored".
    static func classify(_ status: OSStatus, isBiometricRead: Bool = false) -> DeviceKeyError {
        switch status {
        case errSecUserCanceled:
            return .cancelled
        case errSecItemNotFound:
            return .permanentlyInvalidated(
                reason: isBiometricRead ? .biometricsChanged : .notEnrolled
            )
        case errSecAuthFailed:
            // A failed match, not a destroyed key: the item is still there and
            // the next attempt can succeed.
            return .transient(reason: .authenticationFailed)
        case errSecInteractionNotAllowed:
            return .transient(reason: .interactionNotAllowed)
        case errSecNotAvailable:
            return .unavailable(reason: .noPasscodeSet)
        case errSecDecode, errSecInvalidItemRef:
            return .permanentlyInvalidated(reason: .biometricsChanged)
        default:
            return .unexpected(status: status)
        }
    }

    /// Map an `LAError` from an explicit `evaluatePolicy` call.
    static func classify(_ error: LAError) -> DeviceKeyError {
        switch error.code {
        case .userCancel, .systemCancel, .appCancel, .userFallback:
            return .cancelled
        case .biometryNotEnrolled:
            return .permanentlyInvalidated(reason: .biometricsChanged)
        case .passcodeNotSet:
            return .permanentlyInvalidated(reason: .passcodeRemoved)
        case .biometryLockout:
            return .transient(reason: .biometryLockout)
        case .authenticationFailed:
            return .transient(reason: .authenticationFailed)
        case .biometryNotAvailable:
            return .unavailable(reason: .noBiometricHardware)
        default:
            return .unexpected(status: OSStatus(truncatingIfNeeded: error.code.rawValue))
        }
    }
}
