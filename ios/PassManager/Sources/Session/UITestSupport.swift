import Foundation
import PassVaultCore
import PassVaultStorage

/// Launch-argument hooks that exist ONLY so the screenshot tour can drive the app
/// into known states.
///
/// SECURITY SHAPE, and why it is written this way: every function below has its
/// entire body inside `#if DEBUG`. In a release build they compile to empty
/// no-ops — the code that wipes a vault, or that fabricates a fixture, is not
/// merely unreachable, it is not in the binary at all. That matters more here
/// than in most apps: a shipped password manager containing a "delete everything"
/// path guarded by a string comparison would be an unacceptable liability,
/// however carefully guarded.
///
/// Call sites are therefore free of `#if`, which is the other half of the point:
/// no reviewer has to spot a missing guard.
///
/// NOTHING here touches key storage. There was once a `-uiTestRelaxedKeychain`
/// flag that lowered the protection class and, when even that failed, parked the
/// wrapped key in UserDefaults; it existed only because an unsigned CI build had
/// no `application-identifier` and therefore no Keychain. Ad-hoc signing the
/// simulator build gave it one, so the seam is gone rather than merely fenced
/// off — see `PassManager.entitlements`.
enum UITestMode {

    static let resetFlag = "-uiTestReset"
    static let seedFlag = "-uiTestSeed"
    static let importFixtureFlag = "-uiTestImportFixture"

    /// The passphrase the synthetic import fixture is written with. Mirrored by
    /// the UI test.
    static let importFixturePassphrase = "UITest-Import-2026"

    #if DEBUG
    static func isPresent(_ flag: String) -> Bool {
        return ProcessInfo.processInfo.arguments.contains(flag)
    }
    #endif

    static var wantsSeed: Bool {
        #if DEBUG
        return isPresent(seedFlag)
        #else
        return false
        #endif
    }

    static var wantsImportFixture: Bool {
        #if DEBUG
        return isPresent(importFixtureFlag)
        #else
        return false
        #endif
    }

    /// Wipe the vault so onboarding can be walked from the top on every run.
    static func resetIfRequested(databasePath: String) {
        #if DEBUG
        guard isPresent(resetFlag) else {
            return
        }
        let manager = FileManager.default
        // SQLite keeps a write-ahead log and a shared-memory file beside the
        // database; deleting only the main file would leave a half-vault behind.
        for suffix in ["", "-wal", "-shm"] {
            try? manager.removeItem(atPath: databasePath + suffix)
        }
        KeychainVaultStore.removeAll()
        UserDefaults.standard.removeObject(forKey: BackupReminder.defaultsKey)
        #endif
    }

    /// Sample items for the vault list, one per category, with Turkish titles
    /// among them so the screenshots show real text rather than lorem ipsum.
    ///
    /// The ids are fixed so the import fixture can target one of them.
    static func samplePayloads() -> [ItemPayload] {
        #if DEBUG
        return [
            .login(ItemPayload.Login(
                id: seededLoginID,
                title: "GitHub",
                username: "octocat",
                address: "https://github.com",
                password: "hunter2-correct-horse"
            )),
            .card(ItemPayload.Card(
                id: "u1000000-0000-4000-8000-000000000002",
                title: "Kadıköy Bankası Kartı",
                notes: "Ay sonu ödemesi",
                cardholderName: "Ayşe Yılmaz",
                cardNumber: "4111111111111111",
                cardCvc: "123",
                cardExpiry: "12/29"
            )),
            .bank(ItemPayload.Bank(
                id: "u1000000-0000-4000-8000-000000000003",
                title: "Kadıköy Bankası",
                accountNumber: "TR33 0006 1005 1978 6457 8413 26",
                bankName: "Kadıköy Bankası",
                password: "Bank7Xq2"
            )),
            .note(ItemPayload.SecureNote(
                id: "u1000000-0000-4000-8000-000000000004",
                title: "Kurtarma kodları",
                notes: "a1b2-c3d4\ne5f6-g7h8"
            )),
            .identity(ItemPayload.Identity(
                id: "u1000000-0000-4000-8000-000000000005",
                title: "Pasaport bilgileri",
                firstName: "Ayşe",
                lastName: "Yılmaz",
                email: "ayse@example.com",
                phone: "+90 555 000 00 00",
                address: "Kadıköy, İstanbul",
                company: "ACME Yazılım A.Ş."
            ))
        ]
        #else
        return []
        #endif
    }

    static let seededLoginID = "u1000000-0000-4000-8000-000000000001"

    /// A `.pmvault` built in memory so the import review screen can be reached
    /// without driving the system Files picker, which a UI test cannot reliably
    /// populate.
    ///
    /// Deliberately shaped to make the summary INTERESTING: one record whose id
    /// matches a seeded item and carries a newer `updatedAt` (an overwrite, so
    /// the replaced title is listed), and one unknown id (an insert).
    static func makeImportFixture() -> Data? {
        #if DEBUG
        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        let day: Int64 = 86_400_000
        let body = PmVaultBody(version: 1, exportedAt: now, items: [
            PmVaultItem(
                payload: .login(ItemPayload.Login(
                    id: seededLoginID,
                    title: "GitHub",
                    username: "octocat",
                    address: "https://github.com",
                    password: "yeni-parola-2026"
                )),
                createdAt: now - 90 * day,
                updatedAt: now - day
            ),
            PmVaultItem(
                payload: .note(ItemPayload.SecureNote(
                    id: "f1000000-0000-4000-8000-0000000000aa",
                    title: "Yedekten gelen not",
                    notes: "Bu kayıt içe aktarmayla geldi."
                )),
                createdAt: now - 60 * day,
                updatedAt: now - day
            )
        ])
        return try? PmVaultFile.write(body: body, passphrase: importFixturePassphrase)
        #else
        return nil
        #endif
    }
}
