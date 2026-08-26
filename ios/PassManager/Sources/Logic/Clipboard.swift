import Foundation
import UIKit
import UniformTypeIdentifiers

/// Copying secrets to the pasteboard.
///
/// Two non-negotiables from `docs/IOS_PARITY.md`:
///
/// - `expirationDate` 15 seconds out, so a copied password does not sit in the
///   pasteboard until something else replaces it.
/// - `localOnly: true`, so it never crosses to the user's other devices over
///   Universal Clipboard. A password appearing on a Mac in a cafe because it was
///   copied on a phone is exactly the leak this app exists to prevent.
enum Clipboard {

    static let clearAfter: TimeInterval = 15

    static func copySecret(_ value: String) {
        let expiry = Date().addingTimeInterval(clearAfter)
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: value]],
            options: [
                .localOnly: true,
                .expirationDate: expiry
            ]
        )
    }
}
