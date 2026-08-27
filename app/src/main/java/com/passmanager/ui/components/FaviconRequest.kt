package com.passmanager.ui.components

import com.passmanager.domain.model.ItemCategory
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Everything about a site-icon lookup that can be decided without a Composable: whether the item is
 * even eligible for one, and what URL is asked for if it is.
 *
 * It lives apart from [FaviconImage] so the one decision that lets data out of the vault is a plain
 * function a unit test can call. The rendering half is untestable without an instrumented run; this
 * half must not be.
 */

/** The only host this app contacts for icons. Kept next to the code that builds the URL. */
internal const val FAVICON_HOST = "t0.gstatic.com"

/**
 * Requested icon size.
 *
 * The CDN serves whatever the site actually publishes, capped by this number — it does not
 * synthesise resolution. Measured 2026-08-27: `github.com` answers 32x32 and `netflix.com` 64x64 at
 * `size=64`, `128` and `256` alike (byte-identical responses), while `stackoverflow.com` and
 * `garantibbva.com.tr` answer 128x128 at `size=128` but 180x180 at `size=256`, and
 * `migros.com.tr` 144x144. Asking for 256 therefore costs nothing on the sites that cap below it
 * and gains real pixels on the ones that do not. Re-measure before changing this rather than
 * reasoning about it.
 */
private const val FAVICON_REQUESTED_SIZE = 256

/** A resolved lookup: the domain that will be disclosed, and the exact URL that will be fetched. */
internal data class FaviconTarget(val domain: String, val url: String)

/**
 * The gate. Returns null — meaning no request of any kind — unless [category] is
 * [ItemCategory.LOGIN] and [address] parses to a host.
 *
 * The address envelope is shared by all five categories but only holds a *site* for logins: for an
 * identity it holds an email address, for a card the cardholder's name, for a bank the bank's name,
 * for a note the first 60 characters of the body (see [com.passmanager.domain.model.ItemPayload
 * .listSubtitle]). Those parse just fine — an identity's `ayse@example.com` comes back as
 * `example.com`, because [URI] strips the userinfo, and a single-token note like `recovery.codes`
 * comes back as itself. Enabling site icons is consent to look up the domains of your *logins*; it
 * is not consent to disclose your mail provider or the first line of a note.
 *
 * The gate is on the category and deliberately not on how URL-shaped the string looks: filtering
 * free text for URL-shaped input is a guess, and a guess is the wrong instrument for deciding what
 * leaves a vault. It sits here, on the only path to a request URL, so no call site can route
 * around it by handing [FaviconImage] a string it prepared itself.
 */
internal fun faviconTargetFor(category: ItemCategory, address: String): FaviconTarget? {
    if (category != ItemCategory.LOGIN) return null
    val domain = extractDomain(address.trim()) ?: return null
    return FaviconTarget(domain, faviconRequestUrl(domain))
}

/** Extracts the domain from a URL string, returns null if not parseable. */
private fun extractDomain(url: String): String? {
    val normalized = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.contains(".") -> "https://$url"
        else -> return null
    }
    return try {
        URI(normalized).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}

private fun faviconRequestUrl(domain: String): String {
    // The charset-object overload of URLEncoder.encode is API 33; this app ships to API 26.
    val encoded = URLEncoder.encode("https://$domain", StandardCharsets.UTF_8.name())
    // Deliberately the CDN endpoint and not www.google.com/s2/favicons. That address answers 301
    // and sends the client to exactly this URL on t0.gstatic.com — so "no other host is contacted"
    // was only ever true of the request we made, not of the request that ran. Asking the CDN
    // directly means the loader can refuse redirects outright (see PassManagerApp's ImageLoader)
    // and the promise in settings_site_icons_subtitle_on becomes enforceable.
    // If Google ever retires this shape the response stops being a 200 and every entry quietly
    // falls back to its category icon, which is the safe direction for a vault.
    return "https://$FAVICON_HOST/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL" +
        "&url=$encoded&size=$FAVICON_REQUESTED_SIZE"
}

/**
 * The width to draw a [sourcePx]-wide icon at inside a [boxPx] box, in pixels.
 *
 * Upscaling a raster icon by a fractional factor is what makes it look cheap: a 40dp tile is 105px
 * on this emulator and 120px on a 3x screen, and github publishes 32x32, so filling the tile means
 * a smooth 3.75x stretch. No request parameter fixes that — the site simply has no more pixels. So
 * when the source is smaller than the box it is drawn at the largest **integer** multiple that
 * fits (with nearest-neighbour sampling, see [FaviconImage]) and centred, which keeps every source
 * pixel a crisp square block. When the source is larger the box wins and the image is downscaled,
 * which always looks better than the alternative.
 */
internal fun integerFitPx(sourcePx: Int, boxPx: Int): Int = when {
    sourcePx <= 0 || boxPx <= 0 -> boxPx
    sourcePx >= boxPx -> boxPx
    else -> sourcePx * (boxPx / sourcePx)
}
