package com.passmanager.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.passmanager.ui.theme.PillShape

@Composable
fun LoadingButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    // Fully round, and taller than a Material button: this is the one commit action on the panels
    // that use it — create the vault, unlock it, change the passphrase — and it is the last thing
    // under a column of fields that are all rounded rectangles.
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = PillShape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                // Follows whatever content colour the button's own palette set, so a destructive
                // button's spinner does not come out in the default primary tint.
                color = LocalContentColor.current
            )
        } else {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}
