import Foundation
import XCTest
@testable import PassVaultCore

enum TestSupport {

    /// Deliberately cheap Argon2 parameters for tests that are about container
    /// and key-management logic rather than about the KDF itself. The pinned
    /// production cost (m=65536, t=3, p=4) takes a few hundred milliseconds per
    /// derivation; using it in every test would push the suite into minutes.
    ///
    /// These still satisfy the `docs/FORMAT.md` import gate, so files written
    /// with them are read back through exactly the same validation path.
    static let cheapKdf = KdfParams(memory: 64, iterations: 1, parallelism: 1, hashLength: 32)

    static func hexBytes(_ hex: String) -> [UInt8] {
        precondition(hex.count % 2 == 0, "hex string must have an even length")
        var result: [UInt8] = []
        result.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            let pair = String(hex[index..<next])
            guard let value = UInt8(pair, radix: 16) else {
                preconditionFailure("not a hex pair: \(pair)")
            }
            result.append(value)
            index = next
        }
        return result
    }

    static func hexString(_ bytes: [UInt8]) -> String {
        var output = ""
        output.reserveCapacity(bytes.count * 2)
        for byte in bytes {
            output += String(format: "%02x", byte)
        }
        return output
    }

    static func hexString(_ data: Data) -> String {
        return hexString([UInt8](data))
    }

    /// A payload of every category, for round-trip coverage.
    static func samplePayloads() -> [ItemPayload] {
        return [
            .login(ItemPayload.Login(
                id: "11111111-1111-4111-8111-111111111111",
                title: "GitHub",
                notes: "work account",
                username: "octocat",
                address: "https://github.com",
                password: "hunter2"
            )),
            .card(ItemPayload.Card(
                id: "22222222-2222-4222-8222-222222222222",
                title: "Visa",
                notes: "",
                cardholderName: "Ada Lovelace",
                cardNumber: "4111111111111111",
                cardCvc: "737",
                cardExpiry: "12/30"
            )),
            .bank(ItemPayload.Bank(
                id: "33333333-3333-4333-8333-333333333333",
                title: "Ziraat",
                notes: "joint",
                accountNumber: "TR000000000000000000000000",
                bankName: "Ziraat Bankası",
                password: "Bank!2345",
                previousPasswords: ["Old!1234", "Older!123"]
            )),
            .note(ItemPayload.SecureNote(
                id: "44444444-4444-4444-8444-444444444444",
                title: "Recovery codes",
                notes: "a1b2-c3d4\ne5f6-g7h8"
            )),
            .identity(ItemPayload.Identity(
                id: "55555555-5555-4555-8555-555555555555",
                title: "Passport",
                notes: "",
                firstName: "Ada",
                lastName: "Lovelace",
                email: "ada@example.com",
                phone: "+90 555 000 0000",
                address: "Kadıköy, İstanbul",
                company: "Analytical Engines"
            ))
        ]
    }

    static func sampleBody(exportedAt: Int64 = 1_787_000_000_000) -> PmVaultBody {
        var items: [PmVaultItem] = []
        for payload in samplePayloads() {
            items.append(PmVaultItem(
                payload: payload,
                createdAt: 1_786_000_000_000,
                updatedAt: exportedAt
            ))
        }
        return PmVaultBody(version: 1, exportedAt: exportedAt, items: items)
    }
}
