package com.passmanager.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.passmanager.desktop.model.ItemSummary
import com.passmanager.desktop.ui.theme.CategoryCardTint
import com.passmanager.desktop.ui.theme.CategoryIdentityTint
import com.passmanager.desktop.ui.theme.CategoryLoginTint
import com.passmanager.desktop.ui.theme.CategoryNoteTint
import com.passmanager.desktop.ui.components.FaviconSourceSection
import com.passmanager.desktop.ui.components.ThemeToggleIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

@Composable
fun VaultBrowserScreen(
    items: List<ItemSummary>,
    clipboardStatus: String?,
    onCopyPassword: (String) -> Unit,
    onDisconnect: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    useGoogleFavicons: Boolean,
    onFaviconSourceChange: (useGoogle: Boolean) -> Unit,
    onRefreshVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPad = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 520.dp -> 16.dp
            else -> 20.dp
        }
        val verticalPad = when {
            maxHeight < 520.dp -> 10.dp
            else -> 16.dp
        }
        val scrollbarGutter = when {
            maxWidth < 380.dp -> 10.dp
            else -> 16.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPad, vertical = verticalPad)
        ) {
        // Pinned header — does not scroll with vault list
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = Strings.CONNECTED,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onRefreshVault) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = Strings.REFRESH_VAULT_LIST,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ThemeToggleIconButton(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
        if (clipboardStatus != null) {
            item(key = "clipboard") {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = clipboardStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item(key = "spacer_main") {
            Spacer(Modifier.height(12.dp))
        }

        if (items.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = Strings.WAITING_FOR_ITEMS,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "favicon_when_empty") {
                Spacer(Modifier.height(8.dp))
                FaviconSourceSection(
                    useGoogleFavicons = useGoogleFavicons,
                    onSelectPrivate = {
                        if (useGoogleFavicons) onFaviconSourceChange(false)
                    },
                    onSelectGoogle = {
                        if (!useGoogleFavicons) onFaviconSourceChange(true)
                    },
                    compact = true
                )
            }
        } else {
            item(key = "item_count_and_favicon") {
                Text(
                    text = Strings.itemCount(items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FaviconSourceSection(
                    useGoogleFavicons = useGoogleFavicons,
                    onSelectPrivate = {
                        if (useGoogleFavicons) onFaviconSourceChange(false)
                    },
                    onSelectGoogle = {
                        if (!useGoogleFavicons) onFaviconSourceChange(true)
                    },
                    compact = true
                )
                Spacer(Modifier.height(8.dp))
            }
            items(items, key = { it.id }) { item ->
                VaultItemRow(
                    item = item,
                    onCopy = { onCopyPassword(item.id) },
                    useGoogleFavicons = useGoogleFavicons
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
            }

            Spacer(Modifier.width(scrollbarGutter))

            // Scroll track + thumb (wide enough to grab easily)
            Surface(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                tonalElevation = 0.dp
            ) {
                val thumbIdle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                val thumbHover = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                VerticalScrollbar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    adapter = rememberScrollbarAdapter(listState),
                    style = ScrollbarStyle(
                        minimalHeight = 36.dp,
                        thickness = 10.dp,
                        shape = RoundedCornerShape(5.dp),
                        hoverDurationMillis = 250,
                        unhoverColor = thumbIdle,
                        hoverColor = thumbHover
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(Strings.DISCONNECT)
        }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: ItemSummary,
    onCopy: () -> Unit,
    useGoogleFavicons: Boolean
) {
    val categoryTint = categoryTintColor(item.category)
    val categoryIcon = categoryIcon(item.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favicon or category icon
        val domain = extractDomain(item.url)
        if (domain != null) {
            FaviconIcon(
                domain = domain,
                useGoogleFavicons = useGoogleFavicons,
                fallbackIcon = categoryIcon,
                fallbackTint = categoryTint,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(categoryTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    categoryIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = categoryTint
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.url.isNotBlank()) {
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onCopy) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = Strings.copyPasswordFor(item.title),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Favicon loading with cache ──────────────────────────────────────────
//
// Private mode: only `https://domain/favicon.ico`. Optional Google s2 (user toggle)
// matches Android for better coverage; domain is sent to Google in that mode.
//
// Quick wins: bounded LRU cache, limited concurrent loads, smaller Google sz=.

private const val MAX_FAVICON_CACHE_ENTRIES = 128
private const val MAX_CONCURRENT_FAVICON_LOADS = 4

private val faviconLoadSemaphore = Semaphore(MAX_CONCURRENT_FAVICON_LOADS)

private val faviconStoreLock = Any()
private val faviconCacheLru = object : LinkedHashMap<String, ImageBitmap>(MAX_FAVICON_CACHE_ENTRIES, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
        size > MAX_FAVICON_CACHE_ENTRIES
}

/** Domains we already tried; favicon was null or failed. */
private val faviconMissDomains = HashSet<String>()

private fun faviconCacheGet(key: String): ImageBitmap? = synchronized(faviconStoreLock) {
    faviconCacheLru[key]
}

private fun faviconCachePut(key: String, bitmap: ImageBitmap) {
    synchronized(faviconStoreLock) {
        faviconCacheLru[key] = bitmap
    }
}

private fun faviconMissContains(key: String): Boolean = synchronized(faviconStoreLock) {
    faviconMissDomains.contains(key)
}

private fun faviconMissAdd(key: String) {
    synchronized(faviconStoreLock) {
        faviconMissDomains.add(key)
    }
}

/** Clears in-memory favicon caches when the user changes favicon policy. */
internal fun clearDesktopFaviconMemoryCaches() {
    synchronized(faviconStoreLock) {
        faviconCacheLru.clear()
        faviconMissDomains.clear()
    }
}

@Composable
private fun FaviconIcon(
    domain: String,
    useGoogleFavicons: Boolean,
    fallbackIcon: ImageVector,
    fallbackTint: Color,
    modifier: Modifier = Modifier
) {
    val cacheKey = remember(domain, useGoogleFavicons) { cacheKeyFor(domain, useGoogleFavicons) }
    val cached = remember(cacheKey) { faviconCacheGet(cacheKey) }
    val bitmapState = remember(cacheKey) { mutableStateOf(cached) }
    val loaded = remember(cacheKey) {
        mutableStateOf(cached != null || faviconMissContains(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        faviconCacheGet(cacheKey)?.let { hit ->
            bitmapState.value = hit
            loaded.value = true
            return@LaunchedEffect
        }
        if (faviconMissContains(cacheKey)) {
            loaded.value = true
            return@LaunchedEffect
        }
        if (!loaded.value) {
            val bitmap = faviconLoadSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    faviconCacheGet(cacheKey) ?: loadFavicon(domain, useGoogleFavicons)
                }
            }
            if (bitmap != null) {
                faviconCachePut(cacheKey, bitmap)
            } else {
                faviconMissAdd(cacheKey)
            }
            bitmapState.value = bitmap
            loaded.value = true
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp))
        )
    } else if (loaded.value) {
        // Fetch completed but no favicon — show letter avatar
        LetterAvatar(
            letter = domain.firstOrNull()?.uppercaseChar() ?: '?',
            tint = fallbackTint,
            modifier = modifier
        )
    } else {
        // Still loading — show category icon as placeholder
        Icon(
            fallbackIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = fallbackTint
        )
    }
}

private fun cacheKeyFor(domain: String, useGoogle: Boolean): String =
    "$domain|${if (useGoogle) "g" else "p"}"

/**
 * When [useGoogleResolver] is true, tries Google s2 first (same as Android), then direct ICO.
 * When false, only `https://domain/favicon.ico` — no third party.
 */
private fun loadFavicon(domain: String, useGoogleResolver: Boolean): ImageBitmap? {
    if (useGoogleResolver) {
        loadFaviconFromGoogle(domain)?.let { return it }
    }
    return loadFaviconDirect(domain)
}

private fun loadFaviconFromGoogle(domain: String): ImageBitmap? {
    return try {
        val enc = URLEncoder.encode(domain, StandardCharsets.UTF_8)
        val url = URI("https://www.google.com/s2/favicons?domain=$enc&sz=64").toURL()
        openAndDecodeFavicon(url)
    } catch (_: Exception) {
        null
    }
}

/**
 * Fetches /favicon.ico directly from the domain over HTTPS (private mode).
 */
private fun loadFaviconDirect(domain: String): ImageBitmap? {
    return try {
        val url = URI("https://$domain/favicon.ico").toURL()
        openAndDecodeFavicon(url)
    } catch (_: Exception) {
        null
    }
}

private fun openAndDecodeFavicon(url: java.net.URL): ImageBitmap? {
    return try {
        val conn = url.openConnection()
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.setRequestProperty("User-Agent", "PassManager-Desktop/1.0")
        val awtImage = conn.getInputStream().use { input ->
            ImageIO.read(input)
        } ?: return null
        awtImage.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun LetterAvatar(letter: Char, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                fontSize = 14.sp
            )
        }
    }
}

private fun extractDomain(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val normalized = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") -> "https://$trimmed"
        else -> return null
    }
    return try {
        URI(normalized).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}

private fun categoryTintColor(category: String): Color = when (category.lowercase()) {
    "login" -> CategoryLoginTint
    "card" -> CategoryCardTint
    "identity" -> CategoryIdentityTint
    "note" -> CategoryNoteTint
    else -> CategoryLoginTint
}

private fun categoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "login" -> Icons.Default.Person
    "card" -> Icons.Default.CreditCard
    "identity" -> Icons.Default.Person
    "note" -> Icons.AutoMirrored.Filled.Note
    else -> Icons.Default.Key
}
