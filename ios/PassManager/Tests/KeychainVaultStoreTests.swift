import XCTest
import Security
@testable import PassManager

/// Proves the app can actually use the Keychain — in CI, on the simulator, with
/// no developer account anywhere.
///
/// WHY THIS EXISTS. The wrapped vault key has exactly one home, and every other
/// guarantee in the device layer is written on top of that: device binding,
/// exclusion from backups, and the fact that a stolen database file carries no
/// wrapped key to attack offline. When an unsigned build could not write to the
/// Keychain at all, the workaround was a debug-only UserDefaults fallback — the
/// wrapped key sitting in a plist — because nothing failed loudly enough to make
/// the real cause worth chasing.
///
/// So this suite fails, loudly, the moment the real Keychain stops working:
/// there is no longer any way to make the screenshot tour green by routing key
/// storage somewhere else, because these tests would still be red.
///
/// They run in the app's own process (the unit-test bundle is hosted by
/// PassManager), which is what makes them a test of the SHIPPING entitlement
/// rather than of the test runner's.
final class KeychainVaultStoreTests: XCTestCase {

    /// Both are the app's real accounts, so this clears the host app's own
    /// items — on a simulator, in a throwaway container.
    override func setUp() {
        super.setUp()
        KeychainVaultStore.removeAll()
    }

    override func tearDown() {
        KeychainVaultStore.removeAll()
        super.tearDown()
    }

    /// The load-bearing one: a real `SecItemAdd` followed by a real
    /// `SecItemCopyMatching`, with the bytes compared.
    func testWrappedKeyRoundTripsThroughTheRealKeychain() {
        let report = Self.probeReport()
        print("KEYCHAIN-PROBE: \(report)")

        let blob = Data((0..<48).map { UInt8($0) })
        if case .failure(let error) = KeychainVaultStore.saveWrappedKey(blob) {
            XCTFail("the Keychain refused the wrapped key: \(error) [\(report)]")
            return
        }

        switch KeychainVaultStore.loadWrappedKey() {
        case .success(let readBack):
            XCTAssertEqual(readBack, blob, "the Keychain returned different bytes")
        case .failure(let error):
            XCTFail("the wrapped key could not be read back: \(error) [\(report)]")
        }
    }

    /// The stored item must carry the protection class the contract asks for.
    /// Read off the item itself rather than off the constant, so lowering the
    /// class to make some environment happy shows up here as a failure.
    func testTheStoredItemCarriesTheContractedProtectionClass() {
        guard case .success = KeychainVaultStore.saveWrappedKey(Data([0x0f, 0x1e, 0x2d])) else {
            XCTFail("could not store a wrapped key to inspect")
            return
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: KeychainVaultStore.service,
            kSecAttrAccount as String: KeychainVaultStore.Account.wrappedVaultKey.rawValue,
            kSecReturnAttributes as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let attributes = item as? [String: Any] else {
            XCTFail("could not read the item's attributes back (status \(status))")
            return
        }

        let stored = attributes[kSecAttrAccessible as String] as? String
        XCTAssertEqual(
            stored,
            KeychainVaultStore.accessibility as String,
            "stored protection class differs from the one the store asks for; "
                + "attributes were \(attributes.keys.sorted())"
        )
        // Whatever the constant happens to be, it must be one that never leaves
        // this device and never enters a backup.
        XCTAssertTrue(
            [
                kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly as String,
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String
            ].contains(KeychainVaultStore.accessibility as String),
            "the wrapped key is stored with a class that is not ThisDeviceOnly"
        )
    }

    /// A reset must leave nothing behind: `removeAll` is what a vault wipe relies
    /// on, and a surviving item would let the app believe in a vault whose
    /// database is gone.
    func testRemoveAllLeavesNoWrappedKeyBehind() {
        _ = KeychainVaultStore.saveWrappedKey(Data([0x01, 0x02, 0x03]))
        KeychainVaultStore.removeAll()

        switch KeychainVaultStore.loadWrappedKey() {
        case .success:
            XCTFail("the wrapped key survived removeAll()")
        case .failure(let error):
            XCTAssertEqual(error, .permanentlyInvalidated(reason: .notEnrolled))
        }
    }

    // MARK: - Diagnosis

    /// One `SecItemAdd` per protection class, reporting the raw `OSStatus` and the
    /// access group the item landed in.
    ///
    /// Printed on every run and quoted in every failure message, because the
    /// difference between "this simulator has no passcode" (`errSecNotAvailable`,
    /// -25291) and "this binary has no entitlement" (`errSecMissingEntitlement`,
    /// -34018) is the difference between two completely different fixes, and
    /// neither is visible from the UI the screenshot tour gets stuck on.
    private static func probeReport() -> String {
        var lines = ["host=\(Bundle.main.bundleIdentifier ?? "nil")"]
        let classes: [(String, CFString)] = [
            ("WhenPasscodeSetThisDeviceOnly", kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly),
            ("AfterFirstUnlockThisDeviceOnly", kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
            ("WhenUnlockedThisDeviceOnly", kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        ]
        for (name, accessible) in classes {
            let identity: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: probeService,
                kSecAttrAccount as String: "probe-\(name)"
            ]
            SecItemDelete(identity as CFDictionary)

            var add = identity
            add[kSecAttrAccessible as String] = accessible
            add[kSecValueData as String] = Data([0xAB, 0xCD])
            let status = SecItemAdd(add as CFDictionary, nil)

            var group = "-"
            if status == errSecSuccess {
                var read = identity
                read[kSecReturnAttributes as String] = true
                read[kSecMatchLimit as String] = kSecMatchLimitOne
                var item: CFTypeRef?
                if SecItemCopyMatching(read as CFDictionary, &item) == errSecSuccess,
                   let attributes = item as? [String: Any] {
                    group = attributes[kSecAttrAccessGroup as String] as? String ?? "none"
                }
            }
            lines.append("\(name): status=\(status) agrp=\(group)")
            SecItemDelete(identity as CFDictionary)
        }
        return lines.joined(separator: "; ")
    }

    private static let probeService = "com.passmanager.ios.keychain-probe"
}
