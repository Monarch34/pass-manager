package com.passmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.passmanager.R

/**
 * Brand shield mark: [Surface] with [primaryContainer] fill and [primary] shield icon,
 * matching the lock screen hero treatment at scalable sizes.
 */
@Composable
fun AppShieldLogo(
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(25),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.ic_vault_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.65f)
            )
        }
    }
}
