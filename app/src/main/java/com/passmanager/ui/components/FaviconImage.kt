package com.passmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import java.net.URI

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
 * Loads a site favicon for display next to vault items.
 *
 * **Resolution strategy**
 * 1. **Primary:** Google’s favicon service (`/s2/favicons?domain=…&sz=128`) — usually higher quality
 *    and more reliable than fetching `/favicon.ico` alone.
 * 2. **Fallback:** Direct `https://{domain}/favicon.ico` when the primary load fails.
 * 3. **Last resort:** [fallback] composable when the domain is missing or both loads fail.
 *
 * Note: The primary step sends the domain hostname to Google’s service; use only if that trade-off
 * is acceptable for your threat model.
 */
@Composable
fun FaviconImage(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    fallback: @Composable () -> Unit
) {
    val domain = remember(url) { extractDomain(url.trim()) }

    if (domain == null) {
        fallback()
        return
    }

    val primaryUrl = remember(domain) {
        "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }
    val directIcoUrl = remember(domain) {
        "https://$domain/favicon.ico"
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = remember(size, density) {
        with(density) { size.roundToPx().coerceAtLeast(32) }
    }

    val primaryRequest = remember(primaryUrl, decodePx) {
        ImageRequest.Builder(context)
            .data(primaryUrl)
            .size(Size(Dimension.Pixels(decodePx), Dimension.Pixels(decodePx)))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    val fallbackRequest = remember(directIcoUrl, decodePx) {
        ImageRequest.Builder(context)
            .data(directIcoUrl)
            .size(Size(Dimension.Pixels(decodePx), Dimension.Pixels(decodePx)))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    SubcomposeAsyncImage(
        model = primaryRequest,
        contentDescription = "$domain favicon",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(25)),
        loading = {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(25))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
                content = {}
            )
        },
        error = {
            SubcomposeAsyncImage(
                model = fallbackRequest,
                contentDescription = "$domain favicon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(25)),
                loading = {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(RoundedCornerShape(25))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                        content = {}
                    )
                },
                error = { fallback() }
            )
        }
    )
}
