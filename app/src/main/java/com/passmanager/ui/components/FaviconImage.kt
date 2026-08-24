package com.passmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.passmanager.R
import kotlinx.coroutines.flow.filterIsInstance
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Extracts the domain from a URL string, returns null if not parseable.
 */
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

/**
 * Domains whose icon lookup already failed once in this process.
 *
 * A miss is permanent for the session so that scrolling past the same entry does not re-announce
 * its domain to Google over and over. The set is bounded rather than evicted one entry at a time:
 * losing the whole set costs at most one extra request per domain, and the alternative — an LRU
 * with per-access bookkeeping — would put work back in the LazyColumn's fast path.
 */
private val faviconMissDomains: MutableSet<String> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

private const val MAX_FAVICON_MISS_DOMAINS = 512

/** The only host this app contacts for icons. Kept next to the loader that enforces it. */
internal const val FAVICON_HOST = "t0.gstatic.com"

private fun recordFaviconMiss(domain: String) {
    if (faviconMissDomains.size >= MAX_FAVICON_MISS_DOMAINS) faviconMissDomains.clear()
    faviconMissDomains.add(domain)
}

/** Called when the setting is toggled, so a re-enable retries domains that failed earlier. */
internal fun clearFaviconMissDomains() {
    faviconMissDomains.clear()
}

@Composable
private fun FaviconPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(25))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
        content = {}
    )
}

/**
 * Loads a site icon for display next to vault items.
 *
 * The contract this makes to the user is exact. When [useGoogleFavicons] is true, one host is
 * contacted and only that host: `t0.gstatic.com`, Google's icon CDN. Nothing is requested from the
 * site itself — the
 * old `https://{domain}/favicon.ico` hop is gone, because Coil's decoders cannot read a true ICO
 * container, so it spent an undisclosed connection to a site the user holds credentials for and had
 * near-zero chance of returning an icon. When [useGoogleFavicons] is false, no request of any kind
 * is made and [fallback] — the entry's category icon — is shown.
 *
 * Failures are remembered process-wide, so a domain Google has no icon for is asked about once per
 * session instead of once per scroll pass.
 *
 * Uses [rememberAsyncImagePainter] instead of nested `SubcomposeAsyncImage` to cut main-thread
 * subcomposition cost when many rows are visible.
 */
@Composable
fun FaviconImage(
    url: String,
    useGoogleFavicons: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    fallback: @Composable () -> Unit
) {
    val domain = remember(url) { extractDomain(url.trim()) }

    if (domain == null || !useGoogleFavicons) {
        fallback()
        return
    }

    if (faviconMissDomains.contains(domain)) {
        fallback()
        return
    }

    val iconUrl = remember(domain) {
        // The charset-object overload of URLEncoder.encode is API 33; this app ships to API 26.
        val encoded = URLEncoder.encode("https://$domain", StandardCharsets.UTF_8.name())
        // Deliberately the CDN endpoint and not www.google.com/s2/favicons. That address answers 301
        // and sends the client to exactly this URL on t0.gstatic.com — so "no other host is
        // contacted" was only ever true of the request we made, not of the request that ran. Asking
        // the CDN directly means the loader can refuse redirects outright (see PassManagerApp's
        // ImageLoader) and the promise in settings_site_icons_subtitle_on becomes enforceable.
        // If Google ever retires this shape the response stops being a 200 and every entry quietly
        // falls back to its category icon, which is the safe direction for a vault.
        "https://$FAVICON_HOST/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL" +
            "&url=$encoded&size=128"
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = remember(size, density) {
        with(density) { size.roundToPx().coerceIn(32, 128) }
    }
    val coilSize = remember(decodePx) {
        Size(Dimension.Pixels(decodePx), Dimension.Pixels(decodePx))
    }

    val request = remember(iconUrl, coilSize) {
        ImageRequest.Builder(context)
            .data(iconUrl)
            .size(coilSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val painter = rememberAsyncImagePainter(model = request)

    // Keyed on the domain alone: observing the state through snapshotFlow keeps one coroutine alive
    // for the row's lifetime instead of cancelling and relaunching on every
    // Empty -> Loading -> Success transition, which matters in this LazyColumn's fast path.
    LaunchedEffect(domain) {
        snapshotFlow { painter.state }
            .filterIsInstance<AsyncImagePainter.State.Error>()
            .collect { recordFaviconMiss(domain) }
    }

    val clipMod = modifier
        .size(size)
        .clip(RoundedCornerShape(25))

    when (painter.state) {
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = stringResource(R.string.favicon_content_description, domain),
                contentScale = ContentScale.Fit,
                modifier = clipMod
            )
        }
        is AsyncImagePainter.State.Error -> fallback()
        else -> FaviconPlaceholder(clipMod)
    }
}
