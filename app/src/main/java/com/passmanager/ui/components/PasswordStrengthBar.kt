package com.passmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.ui.theme.StrengthFairColor
import kotlinx.coroutines.delay

@Composable
private fun strengthColors() = listOf(
    MaterialTheme.colorScheme.error,
    StrengthFairColor,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.primary
)

private fun computeStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 14) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> 1
        score == 2 -> 2
        score == 3 -> 3
        else -> 4
    }
}

/**
 * Debounced strength meter to avoid heavy main-thread work on every keystroke.
 */
@Composable
fun PasswordStrengthBar(
    password: String,
    modifier: Modifier = Modifier,
    debounceMs: Long = 120L
) {
    var debounced by remember { mutableStateOf("") }

    LaunchedEffect(password) {
        if (password.isEmpty()) {
            debounced = ""
            return@LaunchedEffect
        }
        delay(debounceMs)
        debounced = password
    }

    if (debounced.isEmpty()) return

    val strength = remember(debounced) { computeStrength(debounced) }
    val strengthLabels = listOf(
        stringResource(R.string.strength_weak),
        stringResource(R.string.strength_fair),
        stringResource(R.string.strength_good),
        stringResource(R.string.strength_strong)
    )
    val colors = strengthColors()
    val active = colors[strength - 1]
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { index ->
                val segmentColor = if (index < strength) active else surfaceVariant
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(segmentColor)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = strengthLabels[strength - 1],
            style = MaterialTheme.typography.labelSmall,
            color = active
        )
    }
}
