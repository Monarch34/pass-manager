package com.passmanager.vault

import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.VaultItem

/**
 * Everything about an item that a search should be able to find.
 *
 * Secrets included — a password, a card number, an account number, with one exception
 * named below. That is deliberate and it
 * is safe here: the whole vault is already decrypted in memory while it is open, so matching
 * against a password costs nothing extra and discloses nothing that was not already
 * available to the process. What it buys is being able to find the entry when the only thing
 * you can remember is part of the value.
 *
 * Separate from the session so that adding a category means editing one exhaustive `when`
 * that the compiler checks, rather than remembering that search exists.
 */
internal fun StringBuilder.appendPayloadFields(item: VaultItem) {
    when (val payload = item.payload) {
        is ItemPayload.Login -> {
            append(payload.username).append(' ')
            append(payload.address).append(' ')
            payload.password.reveal { append(it) }
        }
        is ItemPayload.Card -> {
            append(payload.cardholderName).append(' ')
            append(payload.cardExpiry).append(' ')
            payload.cardNumber.reveal { append(it) }
            // The security code is left out on purpose, and it is the one exception. Three
            // digits match a substring of almost every card number and account number in the
            // vault, so including it would return everything for a great many queries.
        }
        is ItemPayload.Bank -> {
            append(payload.bankName).append(' ')
            payload.accountNumber.reveal { append(it).append(' ') }
            payload.password.reveal { append(it).append(' ') }
            // Searchable because the whole reason they are kept is to answer "have I used
            // this one before", and a list you cannot search cannot answer it.
            for (previous in payload.previousPasswords) previous.reveal { append(it).append(' ') }
        }
        is ItemPayload.Identity -> {
            append(payload.firstName).append(' ')
            append(payload.lastName).append(' ')
            append(payload.email).append(' ')
            append(payload.phone).append(' ')
            append(payload.address).append(' ')
            append(payload.company)
        }
        // A note is its title and its notes, both already appended by the caller.
        is ItemPayload.Note -> Unit
    }
}
