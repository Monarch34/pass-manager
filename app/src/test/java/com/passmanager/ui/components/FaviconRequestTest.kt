package com.passmanager.ui.components

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The site-icon gate.
 *
 * Every non-login case below feeds the *real* subtitle the app would store for that category —
 * [ItemPayload.listSubtitle], the same value that reaches the vault row — rather than a string
 * invented for the test, so these stay honest if the subtitle rules change.
 */
class FaviconRequestTest {

    // ── The gate: only logins are looked up ──────────────────────────────────────────────────

    @Test
    fun `login address produces a request`() {
        val target = faviconTargetFor(ItemCategory.LOGIN, "https://www.github.com/settings")
        assertNotNull(target)
        assertEquals("github.com", target!!.domain)
    }

    @Test
    fun `identity subtitle produces no request even though it parses as a domain`() {
        val identity = ItemPayload.Identity(
            id = "1",
            title = "Passport",
            firstName = "Ayse",
            lastName = "Yilmaz",
            email = "ayse@example.com"
        )
        val subtitle = identity.listSubtitle
        assertEquals("ayse@example.com", subtitle)

        assertNull(faviconTargetFor(identity.category, subtitle))

        // Not a hypothetical: the same string under LOGIN resolves, because URI drops the userinfo.
        // Without the category gate the user's mail provider would be sent to Google.
        assertEquals("example.com", faviconTargetFor(ItemCategory.LOGIN, subtitle)?.domain)
    }

    @Test
    fun `note subtitle produces no request even though it parses as a domain`() {
        val note = ItemPayload.SecureNote(
            id = "2",
            title = "Backup",
            notes = "recovery.codes"
        )
        val subtitle = note.listSubtitle
        assertEquals("recovery.codes", subtitle)

        assertNull(faviconTargetFor(note.category, subtitle))
        // The leak this closes: a one-token note body is a perfectly good hostname.
        assertEquals("recovery.codes", faviconTargetFor(ItemCategory.LOGIN, subtitle)?.domain)
    }

    @Test
    fun `card subtitle produces no request`() {
        val card = ItemPayload.Card(
            id = "3",
            title = "Visa",
            cardholderName = "AYSE YILMAZ",
            cardNumber = "4111111111111111",
            cardExpiry = "12/28"
        )
        assertEquals("AYSE YILMAZ", card.listSubtitle)
        assertNull(faviconTargetFor(card.category, card.listSubtitle))
    }

    @Test
    fun `bank subtitle produces no request even though it parses as a domain`() {
        val bank = ItemPayload.Bank(
            id = "4",
            title = "Salary account",
            bankName = "garantibbva.com.tr",
            accountNumber = "000123456789"
        )
        assertEquals("garantibbva.com.tr", bank.listSubtitle)

        assertNull(faviconTargetFor(bank.category, bank.listSubtitle))
        assertEquals(
            "garantibbva.com.tr",
            faviconTargetFor(ItemCategory.LOGIN, bank.listSubtitle)?.domain
        )
    }

    @Test
    fun `login with a non-address subtitle produces no request`() {
        assertNull(faviconTargetFor(ItemCategory.LOGIN, "work account"))
        assertNull(faviconTargetFor(ItemCategory.LOGIN, ""))
        assertNull(faviconTargetFor(ItemCategory.LOGIN, "   "))
    }

    // ── The request URL ─────────────────────────────────────────────────────────────────────

    @Test
    fun `request goes to the icon CDN and asks for 256`() {
        val url = faviconTargetFor(ItemCategory.LOGIN, "github.com")!!.url
        assertTrue(url, url.startsWith("https://t0.gstatic.com/faviconV2?"))
        assertTrue(url, url.contains("&url=https%3A%2F%2Fgithub.com"))
        assertTrue(url, url.endsWith("&size=256"))
    }

    @Test
    fun `only the host is sent - no path, query or credential material`() {
        val target = faviconTargetFor(
            ItemCategory.LOGIN,
            "https://user:secret@www.github.com/login?token=abc"
        )
        assertEquals("github.com", target!!.domain)
        assertTrue(target.url, !target.url.contains("secret"))
        assertTrue(target.url, !target.url.contains("token"))
        assertTrue(target.url, !target.url.contains("login"))
    }

    // ── Integer-multiple sizing ─────────────────────────────────────────────────────────────

    @Test
    fun `a smaller source is drawn at the largest integer multiple that fits`() {
        // github's 32x32 in a 40dp tile: 105px box at 2.625x, 70px icon box.
        assertEquals(64, integerFitPx(sourcePx = 32, boxPx = 70))
        // ...and in the 60dp detail plate, 104px icon box.
        assertEquals(96, integerFitPx(sourcePx = 32, boxPx = 104))
        // netflix's 64x64 has no room to double, so it stays 1:1 rather than stretching to 70.
        assertEquals(64, integerFitPx(sourcePx = 64, boxPx = 70))
        // An exact multiple fills the box exactly.
        assertEquals(96, integerFitPx(sourcePx = 32, boxPx = 96))
    }

    @Test
    fun `a larger source is downscaled to the box`() {
        assertEquals(70, integerFitPx(sourcePx = 180, boxPx = 70))
        assertEquals(70, integerFitPx(sourcePx = 71, boxPx = 70))
        assertEquals(70, integerFitPx(sourcePx = 70, boxPx = 70))
    }

    @Test
    fun `degenerate sizes fall back to the box`() {
        assertEquals(70, integerFitPx(sourcePx = 0, boxPx = 70))
        assertEquals(70, integerFitPx(sourcePx = -1, boxPx = 70))
        assertEquals(0, integerFitPx(sourcePx = 32, boxPx = 0))
    }
}
