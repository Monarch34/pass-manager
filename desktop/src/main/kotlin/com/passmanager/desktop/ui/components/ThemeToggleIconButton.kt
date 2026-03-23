package com.passmanager.desktop.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.passmanager.desktop.ui.Strings

/**
 * Shows the theme the user will switch **to** (sun in dark mode, moon in light mode).
 */
@Composable
fun ThemeToggleIconButton(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (isDarkTheme) Strings.SWITCH_TO_LIGHT_THEME else Strings.SWITCH_TO_DARK_THEME
    IconButton(onClick = onToggleTheme, modifier = modifier) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
