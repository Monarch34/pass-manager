import Foundation
import PassVaultCore

/// Vault list filtering.
///
/// Search runs over DECRYPTED titles and addresses held in ``VaultHeaderCache``,
/// never over SQL. Ciphertext cannot be `LIKE`d, and any on-disk search index
/// would leak exactly what the encryption is there to hide. The plaintext exists
/// only while the vault is unlocked, and disappears when the cache is cleared.
public enum VaultSearch {

    /// Case-folding for search, matched to Android's behaviour.
    ///
    /// Android uses Kotlin's `String.contains(q, ignoreCase = true)`, which
    /// compares two characters as:
    ///
    /// ```
    /// thisUpper == otherUpper || thisUpper.lowercaseChar() == otherUpper.lowercaseChar()
    /// ```
    ///
    /// — uppercase both, and failing that, lowercase both of those uppercased
    /// forms. Two consequences worth spelling out, because getting either wrong
    /// makes iOS search behave differently from Android on the same vault:
    ///
    /// 1. **No diacritic folding.** `ş` does not match `s`, `ğ` does not match
    ///    `g`. Swift's `folding(options:)` with `.diacriticInsensitive` WOULD fold
    ///    them, so it is deliberately not used here.
    /// 2. **The Turkish dotted/dotless I collapses — all four of `i I ı İ`.**
    ///    `i`/`I`/`ı` share the uppercase `I`, so the first branch matches them.
    ///    `ı` and `İ` differ there (`I` vs `İ`), but the second branch lowercases
    ///    both: `I` → `i`, and per-character `İ` → `i`, so they match too. Swift's
    ///    plain `lowercased()` reproduces none of this — it leaves `ı` alone and
    ///    expands `İ` to `i` plus a combining dot, which would make "iş" fail to
    ///    find "İş Bankası" on iOS while finding it on Android. Mapping `İ` and
    ///    `ı` to `i` before lowercasing collapses the same four characters Kotlin
    ///    collapses.
    ///
    /// This fold is equivalent to `ignoreCase`, not merely close to it: Track A
    /// verified the equivalence independently over an ASCII + Turkish character
    /// table. If you are tempted to "fix" a difference here, measure it first.
    public static func foldForSearch(_ text: String) -> String {
        var folded = ""
        folded.reserveCapacity(text.count)
        for scalar in text.unicodeScalars {
            // U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE
            // U+0131 LATIN SMALL LETTER DOTLESS I
            if scalar.value == 0x0130 || scalar.value == 0x0131 {
                folded += "i"
            } else {
                folded.unicodeScalars.append(scalar)
            }
        }
        return folded.lowercased()
    }

    /// Filter by category and free-text query.
    ///
    /// Mirrors Android's `filterBySearchAndGroup`: the query matches against the
    /// decrypted title, the decrypted address, and BOTH the category's display
    /// label and its lowercase key — so typing "card" or "kart" finds cards even
    /// though the word appears in no item's text.
    public static func filter(
        headers: [VaultItemHeaderRow],
        query: String,
        cache: VaultHeaderCache,
        category: ItemCategory? = nil
    ) -> [VaultItemHeaderRow] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let needle = foldForSearch(trimmed)

        return headers.filter { header in
            if let category = category, header.category != category {
                return false
            }
            if needle.isEmpty {
                return true
            }
            if foldForSearch(cache.title(for: header.id)).contains(needle) {
                return true
            }
            if foldForSearch(cache.address(for: header.id)).contains(needle) {
                return true
            }
            if foldForSearch(header.category.label).contains(needle) {
                return true
            }
            if foldForSearch(header.category.dbKey).contains(needle) {
                return true
            }
            return false
        }
    }

    /// Whether one already-folded needle occurs in a raw haystack.
    /// Exposed for callers that pre-fold the query once for a large list.
    public static func matches(foldedNeedle: String, in text: String) -> Bool {
        if foldedNeedle.isEmpty {
            return true
        }
        return foldForSearch(text).contains(foldedNeedle)
    }
}
