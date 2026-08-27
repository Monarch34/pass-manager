package com.passmanager.ui.components

import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
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

/**
 * How much of the plate the icon itself occupies.
 *
 * The plate is the container and the icon is content inside it; filling the plate edge to edge
 * makes a 32x32 favicon read as a stretched screenshot rather than as a logo. Two thirds leaves the
 * icon at roughly the size of the category glyph the fallback draws (22dp inside a 40dp tile), so a
 * list where some rows have icons and some do not still reads as one column of tiles.
 */
private const val FAVICON_ICON_FRACTION = 0.66f

/**
 * Corner radius of the icon itself, as a percentage of its side.
 *
 * Most favicons are an opaque square — github's is a white one — so an unclipped icon puts a hard
 * square inside a rounded plate. Rounding it by roughly the amount a platform app icon is rounded
 * makes it read as a chip resting on the plate instead. Transparent icons (netflix's mark) are
 * unaffected, which is why this is a clip and not a second plate.
 */
private const val FAVICON_ICON_CORNER_PERCENT = 22

private fun recordFaviconMiss(domain: String) {
    if (faviconMissDomains.size >= MAX_FAVICON_MISS_DOMAINS) faviconMissDomains.clear()
    faviconMissDomains.add(domain)
}

/** Called when the setting is toggled, so a re-enable retries domains that failed earlier. */
internal fun clearFaviconMissDomains() {
    faviconMissDomains.clear()
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
    plateColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    plateShape: Shape = RoundedCornerShape(25),
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

    // The icon's own box, not the plate's: decoding to the plate would hand Compose a bitmap it can
    // only shrink again. Coil's painter forces Precision.INEXACT, so this is an upper bound — a
    // source smaller than the box is decoded at its natural size and never smooth-upscaled here.
    val iconBox = size * FAVICON_ICON_FRACTION
    val iconBoxPx = remember(iconBox, density) {
        with(density) { iconBox.roundToPx() }.coerceAtLeast(1)
    }
    val coilSize = remember(iconBoxPx) {
        Size(Dimension.Pixels(iconBoxPx), Dimension.Pixels(iconBoxPx))
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

    val plateMod = modifier
        .size(size)
        .clip(plateShape)
        .background(plateColor)

    when (val state = painter.state) {
        is AsyncImagePainter.State.Success -> {
            Box(modifier = plateMod, contentAlignment = Alignment.Center) {
                val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                val description =
                    stringResource(R.string.favicon_content_description, target.domain)
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    val sourcePx = maxOf(bitmap.width, bitmap.height)
                    val upscaling = sourcePx < iconBoxPx
                    // Nearest-neighbour only when enlarging, where it is the whole point: every
                    // source pixel becomes an exact NxN block instead of a bilinear smear. On the
                    // downscale path it would alias, so that path keeps a filtered sample.
                    val bitmapPainter = remember(bitmap, upscaling) {
                        BitmapPainter(
                            bitmap.asImageBitmap(),
                            filterQuality =
                                if (upscaling) FilterQuality.None else FilterQuality.Medium
                        )
                    }
                    Image(
                        painter = bitmapPainter,
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(with(density) { integerFitPx(sourcePx, iconBoxPx).toDp() })
                            .clip(RoundedCornerShape(FAVICON_ICON_CORNER_PERCENT))
                    )
                } else {
                    // Not a plain bitmap (Coil can hand back a vector or animated drawable). Still
                    // inset inside the plate; only the integer-multiple rule is unavailable.
                    Image(
                        painter = painter,
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(iconBox)
                    )
                }
            }
        }
        is AsyncImagePainter.State.Error -> fallback()
        else -> Box(modifier = plateMod)
    }
}
