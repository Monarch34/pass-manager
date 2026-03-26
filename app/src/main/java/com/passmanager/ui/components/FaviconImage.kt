package com.passmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
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
 * Loads a site favicon for display next to vault items.
 *
 * When [useGoogleFavicons] is true: tries Google’s favicon service first, then
 * `https://{domain}/favicon.ico`. When false: shows [fallback] only (no network).
 * If loading fails, [fallback] is shown.
 *
 * Uses [rememberAsyncImagePainter] instead of nested [SubcomposeAsyncImage] to cut
 * main-thread subcomposition cost when many rows are visible.
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

    val primaryUrl = remember(domain) {
        "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }
    val directIcoUrl = remember(domain) {
        "https://$domain/favicon.ico"
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = remember(size, density) {
        with(density) { size.roundToPx().coerceIn(32, 96) }
    }
    val coilSize = remember(decodePx) {
        Size(Dimension.Pixels(decodePx), Dimension.Pixels(decodePx))
    }

    var tryDirectOnly by remember(domain) { mutableStateOf(false) }

    val dataUrl = if (tryDirectOnly) directIcoUrl else primaryUrl
    val request = remember(dataUrl, coilSize) {
        ImageRequest.Builder(context)
            .data(dataUrl)
            .size(coilSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val painter = rememberAsyncImagePainter(model = request)

    LaunchedEffect(painter.state, tryDirectOnly) {
        if (!tryDirectOnly && painter.state is AsyncImagePainter.State.Error) {
            tryDirectOnly = true
        }
    }

    val clipMod = modifier
        .size(size)
        .clip(RoundedCornerShape(25))

    when (painter.state) {
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = "$domain favicon",
                contentScale = ContentScale.Fit,
                modifier = clipMod
            )
        }
        is AsyncImagePainter.State.Error -> {
            if (tryDirectOnly) {
                fallback()
            } else {
                FaviconPlaceholder(clipMod)
            }
        }
        else -> {
            FaviconPlaceholder(clipMod)
        }
    }
}
