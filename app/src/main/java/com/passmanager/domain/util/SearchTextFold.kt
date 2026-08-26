package com.passmanager.domain.util

/**
 * Length-preserving case fold used by vault search and the NAME_ASC sort.
 *
 * It reproduces `String.contains(other, ignoreCase = true)` exactly at the character level: under
 * Kotlin's `ignoreCase`, two chars `a`/`b` compare equal iff `a.uppercaseChar().lowercaseChar()`
 * equals `b.uppercaseChar().lowercaseChar()`. Folding every character that way and then using a
 * plain, case-sensitive [String.contains] therefore yields identical match results to the old
 * per-comparison `ignoreCase = true` filter — but the folding happens once per decrypt batch
 * instead of once per keystroke per item.
 *
 * It is deliberately NOT [String.lowercase]: `"İ".lowercase()` expands U+0130 into `i` + U+0307
 * (combining dot above), a length change that would silently break substring matching on Turkish
 * text. Folding char-by-char keeps `İ`, `ı` and `I` each collapsing to a single char, matching the
 * legacy `ignoreCase` behavior.
 */
fun String.foldForSearch(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(c.uppercaseChar().lowercaseChar())
    return sb.toString()
}
