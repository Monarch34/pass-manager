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
import com.passmanager.domain.model.ItemCategory
import kotlinx.coroutines.flow.filterIsInstance

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
 * A lookup happens for [ItemCategory.LOGIN] and for nothing else. The composable is handed the
 * item's [category] and its raw [address] envelope rather than a prepared domain precisely so that
 * decision cannot be made anywhere but [faviconTargetFor]; see the reasoning there.
 *
 * Failures are remembered process-wide, so a domain Google has no icon for is asked about once per
 * session instead of once per scroll pass.
 *
 * Uses [rememberAsyncImagePainter] instead of nested `SubcomposeAsyncImage` to cut main-thread
 * subcomposition cost when many rows are visible.
 */
@Composable
fun FaviconImage(
    category: ItemCategory,
    address: String,
    useGoogleFavicons: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    fallback: @Composable () -> Unit
) {
    val target = remember(category, address) { faviconTargetFor(category, address) }

    if (target == null || !useGoogleFavicons) {
        fallback()
        return
    }

    if (faviconMissDomains.contains(target.domain)) {
        fallback()
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = remember(size, density) {
        with(density) { size.roundToPx().coerceIn(32, 128) }
    }
    val coilSize = remember(decodePx) {
        Size(Dimension.Pixels(decodePx), Dimension.Pixels(decodePx))
    }

    val request = remember(target.url, coilSize) {
        ImageRequest.Builder(context)
            .data(target.url)
            .size(coilSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val painter = rememberAsyncImagePainter(model = request)

    // Keyed on the domain alone: observing the state through snapshotFlow keeps one coroutine alive
    // for the row's lifetime instead of cancelling and relaunching on every
    // Empty -> Loading -> Success transition, which matters in this LazyColumn's fast path.
    LaunchedEffect(target.domain) {
        snapshotFlow { painter.state }
            .filterIsInstance<AsyncImagePainter.State.Error>()
            .collect { recordFaviconMiss(target.domain) }
    }

    val clipMod = modifier
        .size(size)
        .clip(RoundedCornerShape(25))

    when (painter.state) {
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription =
                    stringResource(R.string.favicon_content_description, target.domain),
                contentScale = ContentScale.Fit,
                modifier = clipMod
            )
        }
        is AsyncImagePainter.State.Error -> fallback()
        else -> FaviconPlaceholder(clipMod)
    }
}
