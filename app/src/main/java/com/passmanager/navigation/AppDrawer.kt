package com.passmanager.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R

data class DrawerItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

val drawerItems = listOf(
    DrawerItem(Screen.VaultList.route, R.string.drawer_vault, Icons.Default.Lock),
    DrawerItem(Screen.DrawerGenerator.route, R.string.drawer_generator, Icons.Default.Password),
    DrawerItem(Screen.DesktopLink.route, R.string.drawer_desktop_link, Icons.Default.Laptop),
    DrawerItem(Screen.DrawerSettings.route, R.string.drawer_settings, Icons.Default.Settings)
)

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onItemClick: (DrawerItem) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        drawerItems.forEach { item ->
            val selected = when (item.route) {
                Screen.VaultList.route -> currentRoute == Screen.VaultList.route
                    || currentRoute == Screen.AddEditItem.route
                else -> currentRoute == item.route
            }
            NavigationDrawerItem(
                label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes), modifier = Modifier.size(24.dp)) },
                selected = selected,
                onClick = { if (!selected) onItemClick(item) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
