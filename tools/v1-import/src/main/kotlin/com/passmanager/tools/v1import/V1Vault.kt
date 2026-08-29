package com.passmanager.tools.v1import

import com.passmanager.crypto.Secret
import com.passmanager.crypto.aead.AesGcm
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.crypto.kdf.argon2id
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Reads a version 1 `.pmvault`.
 *
 * Implemented against `docs/FORMAT.md` in the version 1 repository, which is the normative
 * specification and says so — neither platform's source code was authoritative over it. That
 * is why this can be exact rather than reverse-engineered.
 *
 * ```
 * 0        4     magic "PMVT"
 * 4        2     headerLen, unsigned 16-bit big-endian
 * 6        N     header, UTF-8 JSON
 * 6+N      12    AES-GCM nonce
 * 6+N+12   rest  ciphertext || 16-byte tag
 * ```
 *
 * The associated data is the first `6 + headerLen` bytes, verbatim.
 *
 * Version 1 pinned Argon2id at `p = 4`, which version 2 never writes. It does not need to:
 * this reads with version 1's parameters and the writer re-seals with version 2's, so a
 * version 1 cost never reaches a version 2 file.
 */
object V1Vault {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    private val magic = "PMVT".toByteArray(Charsets.US_ASCII)

    fun read(file: ByteArray, passphrase: Secret): Result<List<VaultItem>> = runCatching {
        require(file.size > magic.size + 2) { "file is too short to be a v1 vault" }
        require(file.copyOfRange(0, 4).contentEquals(magic)) { "not a v1 .pmvault (bad magic)" }

        val headerLength = ((file[4].toInt() and 0xff) shl 8) or (file[5].toInt() and 0xff)
        require(headerLength in 1..4096) { "header length $headerLength is out of bounds" }
        require(file.size > 6 + headerLength + 12) { "file is truncated" }

        val header = json.decodeFromString(
            V1Header.serializer(),
            file.decodeToString(6, 6 + headerLength),
        )
        require(header.version == 1) { "unsupported v1 header version ${header.version}" }
        require(header.kdf.hashLength == 32) { "unsupported hash length ${header.kdf.hashLength}" }

        val salt = Base64.getDecoder().decode(header.salt)
        require(salt.size == 16) { "salt is ${salt.size} bytes, expected 16" }

        val associatedData = file.copyOfRange(0, 6 + headerLength)
        val nonce = file.copyOfRange(6 + headerLength, 6 + headerLength + 12)
        val sealed = file.copyOfRange(6 + headerLength + 12, file.size)

        val parameters = Argon2Parameters(
            memoryKib = header.kdf.memory,
            iterations = header.kdf.iterations,
            parallelism = header.kdf.parallelism,
        )

        val plaintext = argon2id(passphrase, salt, parameters).use { key ->
            AesGcm.open(key, nonce, sealed, associatedData)
        } ?: error("the passphrase is wrong or the file is corrupted")

        // The decrypted export holds every password in the old vault. It is erased on the
        // way out of this scope, including if parsing throws.
        plaintext.use { secret ->
            val body = secret.reveal { json.decodeFromString(V1Body.serializer(), it.decodeToString()) }
            require(body.version == 1) { "unsupported v1 body version ${body.version}" }
            body.items.map(V1Item::toV2)
        }
    }

    @Serializable
    private data class V1Header(val version: Int, val salt: String, val kdf: V1Kdf)

    @Serializable
    private data class V1Kdf(
        val memory: Int,
        val iterations: Int,
        val parallelism: Int,
        val hashLength: Int,
    )

    @Serializable
    private data class V1Body(
        val version: Int,
        val exportedAt: Long = 0,
        val items: List<V1Item> = emptyList(),
    )

    @Serializable
    private data class V1Item(
        val id: String,
        val category: String = "",
        val createdAt: Long,
        val updatedAt: Long,
        val payload: V1Payload,
    ) {
        fun toV2(): VaultItem = VaultItem(
            // Version 1's identifiers are carried across unchanged, only case-folded.
            //
            // This is the decision that matters most in the whole tool. Both applications
            // ship at once, so the realistic migration is to import the same file on a phone
            // and on a tablet. Minting fresh identifiers would leave two vaults holding
            // identical data and sharing not one identifier, and the first time they were
            // ever reconciled every item would be duplicated permanently — with no repair
            // short of matching them by hand, since de-duplicating by content would merge a
            // personal and a work account and lose one of them.
            id = ItemId.parse(id) ?: error("item $id has an unusable identifier"),
            createdAt = createdAt,
            updatedAt = updatedAt,
            payload = payload.toV2(),
        )
    }

    /**
     * Version 1's payload schema. `payload.type` is authoritative for the category; the
     * item's `category` field merely duplicated it for cheap scanning and is ignored here.
     *
     * Version 1 also repeated the item's identifier inside the payload. Version 2 keeps one
     * copy, on the item, so it is dropped — nothing is lost, because the two were required
     * to agree and the outer one is the one a merge uses.
     */
    @Serializable
    private sealed interface V1Payload {
        fun toV2(): ItemPayload

        @Serializable
        @SerialName("login")
        data class Login(
            val title: String = "",
            val notes: String = "",
            val username: String = "",
            val address: String = "",
            val password: String = "",
        ) : V1Payload {
            override fun toV2() = ItemPayload.Login(
                title = title,
                notes = SecretText.of(notes),
                username = username,
                address = address,
                password = SecretText.of(password),
            )
        }

        @Serializable
        @SerialName("card")
        data class Card(
            val title: String = "",
            val notes: String = "",
            val cardholderName: String = "",
            val cardNumber: String = "",
            val cardCvc: String = "",
            val cardExpiry: String = "",
        ) : V1Payload {
            override fun toV2() = ItemPayload.Card(
                title = title,
                notes = SecretText.of(notes),
                cardholderName = cardholderName,
                cardNumber = SecretText.of(cardNumber),
                cardCvc = SecretText.of(cardCvc),
                cardExpiry = cardExpiry,
            )
        }

        @Serializable
        @SerialName("bank")
        data class Bank(
            val title: String = "",
            val notes: String = "",
            val accountNumber: String = "",
            val bankName: String = "",
            val password: String = "",
            val previousPasswords: List<String> = emptyList(),
        ) : V1Payload {
            // cardIds has no version 1 counterpart: links are new in version 2, so every
            // imported bank starts with none.
            override fun toV2() = ItemPayload.Bank(
                title = title,
                notes = SecretText.of(notes),
                bankName = bankName,
                accountNumber = SecretText.of(accountNumber),
                password = SecretText.of(password),
                previousPasswords = previousPasswords.map(SecretText::of),
            )
        }

        @Serializable
        @SerialName("note")
        data class Note(val title: String = "", val notes: String = "") : V1Payload {
            override fun toV2() = ItemPayload.Note(title = title, notes = SecretText.of(notes))
        }

        @Serializable
        @SerialName("identity")
        data class Identity(
            val title: String = "",
            val notes: String = "",
            val firstName: String = "",
            val lastName: String = "",
            val email: String = "",
            val phone: String = "",
            val address: String = "",
            val company: String = "",
        ) : V1Payload {
            override fun toV2() = ItemPayload.Identity(
                title = title,
                notes = SecretText.of(notes),
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone,
                address = address,
                company = company,
            )
        }
    }
}
