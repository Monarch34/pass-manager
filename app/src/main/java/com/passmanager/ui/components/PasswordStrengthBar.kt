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
import com.passmanager.domain.validation.PasswordStrengthEvaluator
import com.passmanager.ui.theme.StrengthFairColor
import kotlinx.coroutines.delay

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

    val strength = remember(debounced) { PasswordStrengthEvaluator.evaluate(debounced) }
    val idx = strength.ordinal
    val weakLabel = stringResource(R.string.strength_weak)
    val fairLabel = stringResource(R.string.strength_fair)
    val goodLabel = stringResource(R.string.strength_good)
    val strongLabel = stringResource(R.string.strength_strong)
    val strengthLabels = remember(weakLabel, fairLabel, goodLabel, strongLabel) {
        listOf(weakLabel, fairLabel, goodLabel, strongLabel)
    }
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryColor = MaterialTheme.colorScheme.primary
    val colors = remember(errorColor, tertiaryColor, primaryColor) {
        listOf(errorColor, StrengthFairColor, tertiaryColor, primaryColor)
    }
    val active = colors[idx]
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { index ->
                val segmentColor = if (index <= idx) active else surfaceVariant
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
            text = strengthLabels[idx],
            style = MaterialTheme.typography.labelSmall,
            color = active
        )
    }
}
