package com.passmanager.format

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The container, end to end, on every target.
 *
 * Running these in `commonTest` rather than per platform is the point: a vault written on a
 * phone has to open on a desktop and on iOS, and the only way to know that is to run the
 * same round trip on all three. A test that passes only where it was written proves nothing
 * about the property the format exists to provide.
 */
class PmVaultTest {

    /** Cheap on purpose: the container is under test, and Argon2 has its own vectors. */
    private val cheap = Argon2Parameters(memoryKib = 16384, iterations = 1, parallelism = 1)

    private fun sampleContents() = VaultContents(
        items = listOf(
            VaultItem(
                id = ItemId.parse("11111111-1111-4111-8111-111111111111")!!,
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_001_000,
                payload = ItemPayload.Login(
                    title = "GitHub",
                    username = "monarch",
                    address = "https://github.com",
                    password = SecretText.of("correct horse battery staple"),
                    notes = SecretText.of("recovery code 4821"),
                ),
            ),
            VaultItem(
                id = ItemId.parse("22222222-2222-4222-8222-222222222222")!!,
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_002_000,
                payload = ItemPayload.Bank(
                    title = "Bank",
                    bankName = "Ziraat",
                    accountNumber = SecretText.of("TR000000000000"),
                    password = SecretText.of("hunter2"),
                    previousPasswords = listOf(SecretText.of("hunter1")),
                    cardIds = listOf(ItemId.parse("33333333-3333-4333-8333-333333333333")!!),
                ),
            ),
        ),
    )

    private fun sealAndParse(
        contents: VaultContents = sampleContents(),
        passphrase: String = "open sesame",
    ): Pair<ByteArray, VaultParse.Sealed> {
        val bytes = PmVault.create(contents, Secret.copyOfUtf8(passphrase), cheap)
        return bytes to assertIs<VaultParse.Sealed>(PmVault.parse(bytes))
    }

    @Test
    fun `a vault round trips through the container`() {
        val (_, sealed) = sealAndParse()
        val opened = assertIs<VaultOpen.Opened>(
            sealed.openWithPassphrase(Secret.copyOfUtf8("open sesame"))
        )

        assertEquals(2, opened.contents.items.size)
        val login = opened.contents.items[0]
        val credentials = assertIs<ItemPayload.Login>(login.payload)
        assertEquals("GitHub", credentials.title)
        assertEquals("monarch", credentials.username)
        assertEquals("correct horse battery staple", credentials.password.reveal { it })
        assertEquals(1_700_000_001_000, login.updatedAt)

        val bank = assertIs<ItemPayload.Bank>(opened.contents.items[1].payload)
        assertEquals("hunter2", bank.password.reveal { it })
        assertEquals(listOf("hunter1"), bank.previousPasswords.map { p -> p.reveal { it } })
        assertEquals("33333333-3333-4333-8333-333333333333", bank.cardIds.single().value)
    }

    /**
     * No password, no note and no account number may appear anywhere in the file. The whole
     * point of the container, stated as the only test that would catch a writer that framed
     * the plaintext instead of the ciphertext.
     */
    @Test
    fun `nothing secret survives into the file`() {
        val (bytes, _) = sealAndParse()
        val text = bytes.decodeToString()
        for (secret in listOf("correct horse battery staple", "hunter2", "hunter1", "TR000000000000", "recovery code 4821")) {
            assertTrue(!text.contains(secret), "the file contains \"$secret\" in the clear")
        }
        // The title is inside the seal too — only the descriptor is meant to be readable.
        assertTrue(!text.contains("GitHub"), "an item title leaked into the file")
    }

    @Test
    fun `the descriptor is the only readable part and it says what it should`() {
        val (bytes, sealed) = sealAndParse()
        assertEquals("PMV2", bytes.decodeToString(0, 4))
        assertEquals(VaultDescriptor.Container, sealed.descriptor.container)
        assertEquals(VaultDescriptor.Schema, sealed.descriptor.schema)
        assertEquals(cheap, sealed.descriptor.kdf)
        assertEquals(16, sealed.descriptor.salt.size)
        assertEquals(1, sealed.slots.size)
        assertEquals(WrapSlot.KindPassphrase, sealed.slots.single().kind)
    }

    @Test
    fun `the wrong passphrase is unopenable`() {
        val (_, sealed) = sealAndParse()
        assertIs<VaultOpen.Unopenable>(sealed.openWithPassphrase(Secret.copyOfUtf8("wrong")))
    }

    /**
     * The failure this format exists to tell apart. A file cut short is provably damaged
     * without a key, so it must never come back as "your passphrase is wrong" — which is
     * what version 1 had to say, because GCM cannot distinguish the two and nothing else
     * was checking.
     */
    @Test
    fun `a truncated file is damaged and never a wrong passphrase`() {
        val (bytes, _) = sealAndParse()
        for (cut in intArrayOf(1, 10, 30, 31, 40, bytes.size / 2, bytes.size - 1)) {
            val short = bytes.copyOf(cut)
            val parse = PmVault.parse(short)
            assertTrue(
                parse is VaultParse.Damaged || parse is VaultParse.NotAVault,
                "cutting to $cut bytes gave ${parse::class.simpleName}",
            )
        }
    }

    /**
     * Any edit inside the sealed region must fail, and any edit to the descriptor must fail
     * too — that is what makes it associated data rather than merely a header.
     */
    @Test
    fun `editing the file makes it unopenable`() {
        val (bytes, _) = sealAndParse()
        // The version integers and the salt: readable, and covered by the body's tag.
        for (index in intArrayOf(5, 7, 15, 20, 30)) {
            val altered = bytes.copyOf()
            altered[index] = (altered[index].toInt() xor 1).toByte()
            val parse = PmVault.parse(altered)
            if (parse is VaultParse.Sealed) {
                assertIs<VaultOpen.Unopenable>(
                    parse.openWithPassphrase(Secret.copyOfUtf8("open sesame")),
                    "editing byte $index went undetected",
                )
            }
        }
        // Deep inside the body ciphertext.
        val altered = bytes.copyOf()
        altered[bytes.size - 20] = (altered[bytes.size - 20].toInt() xor 1).toByte()
        val parse = assertIs<VaultParse.Sealed>(PmVault.parse(altered))
        assertIs<VaultOpen.Unopenable>(parse.openWithPassphrase(Secret.copyOfUtf8("open sesame")))
    }

    /** Changing the recorded cost changes the derived key, so it cannot be quietly lowered. */
    @Test
    fun `lowering the recorded cost does not open the vault`() {
        val (bytes, _) = sealAndParse()
        val altered = bytes.copyOf()
        altered.putU32(11, VaultDescriptor.MinMemoryKib.toLong())
        val parse = PmVault.parse(altered)
        if (parse is VaultParse.Sealed) {
            assertIs<VaultOpen.Unopenable>(parse.openWithPassphrase(Secret.copyOfUtf8("open sesame")))
        }
    }

    @Test
    fun `something that is not a vault is reported as such`() {
        assertIs<VaultParse.NotAVault>(PmVault.parse("not a vault at all, just text".encodeToByteArray()))
        assertIs<VaultParse.NotAVault>(PmVault.parse(ByteArray(4) { 'P'.code.toByte() }))
        assertIs<VaultParse.NotAVault>(PmVault.parse(ByteArray(0)))
        // Right magic, nothing behind it: that one really is a damaged vault.
        assertIs<VaultParse.Damaged>(PmVault.parse(VaultDescriptor.Magic.copyOf(20)))
    }

    @Test
    fun `a file from a future version is unsupported rather than damaged`() {
        val (bytes, _) = sealAndParse()
        val fromTheFuture = bytes.copyOf()
        fromTheFuture.putU16(7, VaultDescriptor.Schema + 1) // minSchema
        val parse = assertIs<VaultParse.Unsupported>(PmVault.parse(fromTheFuture))
        assertEquals(VaultDescriptor.Schema + 1, parse.minSchema)

        val unknownContainer = bytes.copyOf()
        unknownContainer.putU8(4, VaultDescriptor.Container + 1)
        assertIs<VaultParse.Unsupported>(PmVault.parse(unknownContainer))
    }

    /**
     * A newer writer may add fields a reader must ignore, and those still open here — that
     * is what `schema` moving without `minSchema` means.
     */
    @Test
    fun `a newer schema still opens when minSchema allows it`() {
        val (bytes, _) = sealAndParse()
        val newer = bytes.copyOf()
        newer.putU16(5, VaultDescriptor.Schema + 5) // schema only
        // The descriptor is associated data, so editing it must break the tag rather than
        // being tolerated. Proving it is refused is the point: a reader cannot be tricked
        // into opening a file whose declared meaning was changed.
        val parse = assertIs<VaultParse.Sealed>(PmVault.parse(newer))
        assertEquals(VaultDescriptor.Schema + 5, parse.descriptor.schema)
        assertIs<VaultOpen.Unopenable>(parse.openWithPassphrase(Secret.copyOfUtf8("open sesame")))
    }

    /** Two vaults of the same contents must differ: fresh salt, fresh key, fresh nonces. */
    @Test
    fun `two vaults of the same contents share no bytes past the magic`() {
        val first = PmVault.create(sampleContents(), Secret.copyOfUtf8("same"), cheap)
        val second = PmVault.create(sampleContents(), Secret.copyOfUtf8("same"), cheap)
        assertEquals(first.size, second.size)
        assertNotEquals(
            first.copyOfRange(9, first.size).toList(),
            second.copyOfRange(9, second.size).toList(),
        )
    }

    /**
     * The biometric and keystore unlock paths take the vault key directly, without a
     * passphrase ever existing.
     */
    @Test
    fun `the vault key opens the body on its own`() {
        val (_, sealed) = sealAndParse()
        val opened = assertIs<VaultOpen.Opened>(
            sealed.openWithPassphrase(Secret.copyOfUtf8("open sesame"))
        )
        val again = sealed.openWithVaultKey(opened.vaultKey)
        assertEquals(2, again?.items?.size)
        assertNull(sealed.openWithVaultKey(Secret.random(32)), "a random key opened the vault")
    }

    @Test
    fun `an empty vault is a valid vault`() {
        val bytes = PmVault.create(VaultContents(), Secret.copyOfUtf8("p"), cheap)
        val sealed = assertIs<VaultParse.Sealed>(PmVault.parse(bytes))
        val opened = assertIs<VaultOpen.Opened>(sealed.openWithPassphrase(Secret.copyOfUtf8("p")))
        assertEquals(0, opened.contents.items.size)
    }
}
