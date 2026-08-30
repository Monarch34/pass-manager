package com.passmanager.domain.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What an item actually holds, with only the fields its kind has.
 *
 * A sealed hierarchy rather than one wide type with everything nullable: a card has no
 * username and a note has no expiry date, and a shape that admits both invites code that
 * reads a field which is never set. The category is derived from the subtype rather than
 * stored beside it, so the two can never disagree.
 *
 * ### Which fields are secret
 *
 * Only the ones an attacker came for. A title, a website and a bank's name are shown in
 * lists and searched; a password, a card number and a security code are not, and the type
 * says so. That line is drawn deliberately rather than by making everything secret, because
 * a `SecretText` that has to be revealed to render a list is a `SecretText` that will be
 * revealed constantly and stop meaning anything.
 *
 * `notes` is on the secret side of that line, which may look surprising. It is where people
 * keep recovery codes, PINs and answers to security questions — for a secure note it is the
 * entire content — so treating it as ordinary text would exempt the field most likely to
 * hold the thing that matters.
 */
@Serializable
sealed interface ItemPayload {

    /** Shown in lists and searched. Never secret; an item nobody can identify is useless. */
    val title: String

    val notes: SecretText

    val category: ItemCategory

    @Serializable
    @SerialName("login")
    data class Login(
        override val title: String,
        override val notes: SecretText = SecretText.Empty,
        val username: String = "",
        val address: String = "",
        val password: SecretText = SecretText.Empty,
    ) : ItemPayload {
        override val category: ItemCategory get() = ItemCategory.LOGIN
    }

    @Serializable
    @SerialName("card")
    data class Card(
        override val title: String,
        override val notes: SecretText = SecretText.Empty,
        val cardholderName: String = "",
        /** Displayed masked; the last four digits are not enough to charge anything. */
        val cardNumber: SecretText = SecretText.Empty,
        val cardCvc: SecretText = SecretText.Empty,
        /** Printed on the front of the card and useless on its own. */
        val cardExpiry: String = "",
    ) : ItemPayload {
        override val category: ItemCategory get() = ItemCategory.CARD
    }

    @Serializable
    @SerialName("bank")
    data class Bank(
        override val title: String,
        override val notes: SecretText = SecretText.Empty,
        val bankName: String = "",
        val accountNumber: SecretText = SecretText.Empty,
        val password: SecretText = SecretText.Empty,
        /**
         * Some banks refuse a password used in the last N changes, so the old ones have to
         * be kept to pick a new one — which makes them exactly as sensitive as the current
         * one, not less.
         */
        val previousPasswords: List<SecretText> = emptyList(),
        /**
         * The cards this account issues.
         *
         * The link is one-sided and the bank owns it. A card does not name its bank, which
         * means adding or removing a link touches one item rather than two and cannot leave
         * the two halves disagreeing.
         *
         * An identifier here may name an item that no longer exists. That is not an error
         * and must not be repaired: a card deleted on a device makes every bank that named
         * it dangle immediately, with no merge and no second device involved. Readers skip
         * what they cannot resolve, and **writers write the list back unchanged** — an
         * editor that saves only the links it could resolve deletes the rest silently.
         */
        val cardIds: List<ItemId> = emptyList(),
    ) : ItemPayload {
        override val category: ItemCategory get() = ItemCategory.BANK

        /**
         * This payload, carrying the password history of the version it replaces.
         *
         * Here rather than in each editor, and that is the whole reason it exists. An editor
         * builds a payload out of the fields it rendered, so a field it does not render is
         * absent — and [previousPasswords] is never rendered. Android carried it forward by
         * hand and iOS did not, which meant saving a bank on the phone silently destroyed
         * every old password it had kept. One rule in one place cannot disagree with itself.
         *
         * The capture is the other half. The field is only useful because banks refuse a
         * password used in the last few changes, which requires the old one to be recorded
         * at the moment it stops being current — this moment, and no later one.
         */
        fun withHistoryFrom(earlier: Bank?): Bank {
            val replaced = earlier?.password ?: return this
            // An empty password was never in use, and re-saving without touching the field
            // is not a change. Neither is worth a slot.
            if (replaced.isEmpty || replaced == password) {
                return copy(previousPasswords = earlier.previousPasswords)
            }
            return copy(
                previousPasswords =
                    (listOf(replaced) + earlier.previousPasswords).take(MaxRememberedPasswords),
            )
        }

        companion object {
            /**
             * More than any bank checks against, and a hard stop. Every entry here is a
             * password that once protected this account and very likely still protects
             * something else, so the list has to end somewhere.
             */
            const val MaxRememberedPasswords = 8
        }
    }

    @Serializable
    @SerialName("note")
    data class Note(
        override val title: String,
        override val notes: SecretText = SecretText.Empty,
    ) : ItemPayload {
        override val category: ItemCategory get() = ItemCategory.NOTE
    }

    @Serializable
    @SerialName("identity")
    data class Identity(
        override val title: String,
        override val notes: SecretText = SecretText.Empty,
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val address: String = "",
        val company: String = "",
    ) : ItemPayload {
        override val category: ItemCategory get() = ItemCategory.IDENTITY
    }
}
