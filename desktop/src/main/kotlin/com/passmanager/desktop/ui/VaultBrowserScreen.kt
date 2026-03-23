package com.passmanager.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Person
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
import com.passmanager.desktop.ui.components.ThemeToggleIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

@Composable
fun VaultBrowserScreen(
    items: List<ItemSummary>,
    clipboardStatus: String?,
    onCopyPassword: (String) -> Unit,
    onDisconnect: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Strings.CONNECTED,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            ThemeToggleIconButton(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }

        clipboardStatus?.let { status ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
        } else {
            Text(
                text = Strings.itemCount(items.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    VaultItemRow(
                        item = item,
                        onCopy = { onCopyPassword(item.id) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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

@Composable
private fun VaultItemRow(
    item: ItemSummary,
    onCopy: () -> Unit
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
                fallbackIcon = categoryIcon,
                fallbackTint = categoryTint,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Icon(
                categoryIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = categoryTint
            )
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
// Fetches /favicon.ico directly from the site — no third-party involved,
// preserving user privacy. Falls back to a letter avatar if unavailable.

private val faviconCache = ConcurrentHashMap<String, ImageBitmap?>()

@Composable
private fun FaviconIcon(
    domain: String,
    fallbackIcon: ImageVector,
    fallbackTint: Color,
    modifier: Modifier = Modifier
) {
    val bitmapState = remember(domain) { mutableStateOf(faviconCache[domain]) }
    val loaded = remember(domain) { mutableStateOf(faviconCache.containsKey(domain)) }

    LaunchedEffect(domain) {
        if (!loaded.value) {
            val bitmap = withContext(Dispatchers.IO) { loadFavicon(domain) }
            faviconCache[domain] = bitmap
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

/**
 * Fetches /favicon.ico directly from the domain over HTTPS.
 * No third-party service is contacted — only the site itself.
 */
private fun loadFavicon(domain: String): ImageBitmap? {
    return try {
        val url = URI("https://$domain/favicon.ico").toURL()
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
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun extractDomain(url: String): String? {
    if (url.isBlank()) return null
    return try {
        val withScheme = if (url.startsWith("http")) url else "https://$url"
        URI(withScheme).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}

private fun categoryTintColor(category: String): Color = when (category) {
    "login" -> CategoryLoginTint
    "card" -> CategoryCardTint
    "note" -> CategoryNoteTint
    "identity" -> CategoryIdentityTint
    else -> CategoryLoginTint
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "card" -> Icons.Default.CreditCard
    "note" -> Icons.AutoMirrored.Filled.Note
    "identity" -> Icons.Default.Person
    else -> Icons.Default.Key
}
