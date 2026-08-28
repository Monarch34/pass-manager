package com.passmanager.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.ui.theme.FootnoteStyle
import com.passmanager.ui.theme.PlateShape

data class DrawerItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

val drawerItems = listOf(
    DrawerItem(Screen.VaultList.route, R.string.drawer_vault, Icons.Default.Lock),
    DrawerItem(Screen.DrawerGenerator.route, R.string.drawer_generator, Icons.Default.AutoAwesome),
    DrawerItem(Screen.DrawerSettings.route, R.string.drawer_settings, Icons.Default.Settings),
    DrawerItem(Screen.DesktopLink.route, R.string.drawer_desktop_link, Icons.Default.Laptop)
)

/** The drawer sheet is a fixed panel, not a percentage of the screen, up to a cap on small phones. */
private val DrawerWidth = 300.dp
private val DrawerItemHeight = 56.dp
private val DrawerItemShape = RoundedCornerShape(28.dp)

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    itemCount: Int,
    desktopConnected: Boolean,
    onItemClick: (DrawerItem) -> Unit,
    onLockNow: () -> Unit
) {
    // Not ModalDrawerSheet: its own container shape rounds all four corners, and the sheet is
    // flush with the left edge of the window, so the two corners nobody can see were the only
    // ones Material would let go of.
    Column(
        modifier = Modifier
            .width(DrawerWidth)
            .fillMaxWidth(0.82f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // The sheet is painted edge to edge so its fill reaches the top of the window; the
            // rows inside still have to clear the bars.
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(PlateShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    // The count is the honest thing to put next to "unlocked": it is the one fact
                    // that proves the vault really did open, and it costs no decrypt to say.
                    text = stringResource(
                        R.string.drawer_status_unlocked,
                        pluralStringResource(R.plurals.vault_item_count, itemCount, itemCount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        drawerItems.forEach { item ->
            val selected = when (item.route) {
                Screen.VaultList.route -> currentRoute == Screen.VaultList.route
                    || currentRoute == Screen.AddEditItem.route
                else -> currentRoute == item.route
            }
            DrawerRow(
                icon = item.icon,
                label = stringResource(item.labelRes),
                selected = selected,
                badge = if (item.route == Screen.DesktopLink.route && desktopConnected) {
                    stringResource(R.string.drawer_desktop_connected)
                } else {
                    null
                },
                onClick = { if (!selected) onItemClick(item) }
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        DrawerRow(
            icon = Icons.Default.Lock,
            label = stringResource(R.string.drawer_lock_now),
            selected = false,
            badge = null,
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onLockNow
        )
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    // Selection is the brand at low alpha, the same treatment the filter chips and a selected
    // vault row use. Material's default fills the pill with a container colour, which at drawer
    // width made the selected entry the most saturated block in the app.
    val color = if (selected) MaterialTheme.colorScheme.primary else contentColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DrawerItemHeight)
            .clip(DrawerItemShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Text(
                text = badge,
                style = FootnoteStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
