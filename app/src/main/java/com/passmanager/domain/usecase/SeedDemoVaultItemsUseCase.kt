package com.passmanager.domain.usecase

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import javax.inject.Inject

/**
 * Inserts demo vault rows for development — **six items per [ItemCategory]** (30 total).
 * Requires an unlocked vault ([SaveVaultItemUseCase] encrypts with the vault key).
 */
class SeedDemoVaultItemsUseCase @Inject constructor(
    private val saveVaultItemUseCase: SaveVaultItemUseCase
) {

    companion object {
        const val ITEMS_PER_CATEGORY: Int = 6
    }

    suspend operator fun invoke(): Int {
        var count = 0
        for (category in ItemCategory.entries) {
            repeat(ITEMS_PER_CATEGORY) { index ->
                val n = index + 1
                val payload = demoPayload(category, n)
                saveVaultItemUseCase(payload)
                count++
            }
        }
        return count
    }

    private fun demoPayload(category: ItemCategory, n: Int): ItemPayload {
        val idPlaceholder = ""
        return when (category) {
            ItemCategory.LOGIN -> ItemPayload.Login(
                id = idPlaceholder,
                title = "Demo Login $n",
                notes = "Seeded sample — login $n",
                username = "user$n",
                address = "https://demo$n.example.com",
                password = "DemoPass$n!"
            )
            ItemCategory.CARD -> ItemPayload.Card(
                id = idPlaceholder,
                title = "Demo Card $n",
                notes = "Seeded sample — card $n",
                cardholderName = "Demo User $n",
                cardNumber = sixteenDigitPan(n),
                cardCvc = "${n % 10}${(n + 1) % 10}${(n + 2) % 10}",
                cardExpiry = "12/28"
            )
            ItemCategory.NOTE -> ItemPayload.SecureNote(
                id = idPlaceholder,
                title = "Demo Note $n",
                notes = "Seeded secure note body $n. Lorem ipsum for list preview."
            )
            ItemCategory.IDENTITY -> ItemPayload.Identity(
                id = idPlaceholder,
                title = "Demo Identity $n",
                notes = "Seeded sample — identity $n",
                firstName = "Demo",
                lastName = "Person$n",
                email = "demo$n@example.com",
                phone = "+1 555 010$n",
                address = "$n Demo Street",
                company = "Demo Corp"
            )
            ItemCategory.BANK -> ItemPayload.Bank(
                id = idPlaceholder,
                title = "Demo Bank $n",
                notes = "Seeded sample — bank $n",
                accountNumber = "000123456789$n",
                bankName = "Demo Bank $n",
                password = bankCompliantPassword(n),
                previousPasswords = emptyList()
            )
        }
    }

    /** 16-digit test PAN (distinct per index). */
    private fun sixteenDigitPan(n: Int): String {
        val suffix = n.toString().padStart(3, '0')
        return "4111111111111$suffix".take(16).padEnd(16, '0')
    }

    /** Meets [com.passmanager.domain.validation.BankPasswordValidator] complex rules (6–12 chars). */
    private fun bankCompliantPassword(n: Int): String = "Aa1x${n}Yz"
}
