package com.passmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.passmanager.domain.password.PasswordGenerator
import com.passmanager.domain.password.PasswordRecipe
import com.passmanager.domain.password.PasswordStrength
import kotlin.math.roundToInt

/**
 * A password field, with what it is worth written under it.
 *
 * The meter is here rather than on a separate screen because the only moment the number can
 * change a decision is while the password is being typed or chosen. Shown afterwards it is
 * trivia.
 */
@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var generating by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelField(label, value, onValueChange, secret = true)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value.isEmpty()) {
                Box(Modifier.weight(1f))
            } else {
                StrengthMeter(remember(value) { PasswordStrength.of(value) }, Modifier.weight(1f))
            }
            TextButton({ generating = true }) { Text("Generate") }
        }
    }

    if (generating) {
        GeneratorDialog(
            onDismiss = { generating = false },
            onUse = {
                onValueChange(it)
                generating = false
            },
        )
    }
}

@Composable
private fun StrengthMeter(strength: PasswordStrength, modifier: Modifier = Modifier) {
    val colour = when (strength.band) {
        PasswordStrength.Band.Trivial, PasswordStrength.Band.Weak -> MaterialTheme.colorScheme.error
        PasswordStrength.Band.Reasonable -> MaterialTheme.colorScheme.onSurfaceVariant
        PasswordStrength.Band.Strong -> MaterialTheme.colorScheme.primary
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(strength.fraction.toFloat())
                    .height(3.dp)
                    .background(colour, RoundedCornerShape(2.dp))
            )
        }
        Text(
            strength.summary,
            style = MaterialTheme.typography.bodySmall,
            color = colour,
        )
    }
}

/**
 * The generator, with the password visible before it is accepted.
 *
 * Shown rather than applied silently, because a password nobody has looked at is one nobody
 * noticed was rejected by the site's own rules — and because the length and the alphabet are
 * the two things people actually want to change.
 */
@Composable
private fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableFloatStateOf(PasswordRecipe.DefaultLength.toFloat()) }
    var uppercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var unambiguous by remember { mutableStateOf(false) }
    // Redrawn whenever the recipe changes, and on demand. The password itself is state, so
    // that opening the dialog twice does not silently hand back the same one.
    var nonce by remember { mutableIntStateOf(0) }

    val recipe = remember(length, uppercase, digits, symbols, unambiguous) {
        PasswordRecipe(
            length = length.roundToInt(),
            uppercase = uppercase,
            digits = digits,
            symbols = symbols,
            avoidAmbiguous = unambiguous,
        )
    }
    val drawn = remember(recipe, nonce) { PasswordGenerator.generate(recipe).reveal { it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate a password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    drawn,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${length.roundToInt()} characters, ${PasswordGenerator.bits(recipe).roundToInt()} bits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = PasswordRecipe.MinLength.toFloat()..64f,
                )
                // Lower case is not offered as a switch, which is what keeps every reachable
                // combination legal: a recipe with no classes at all is refused outright, and
                // this way the user cannot ask for one.
                Toggle("Capitals", uppercase) { uppercase = it }
                Toggle("Digits", digits) { digits = it }
                Toggle("Symbols", symbols) { symbols = it }
                Toggle("Avoid lookalike characters", unambiguous) { unambiguous = it }
                TextButton({ nonce++ }) { Text("Draw another") }
            }
        },
        confirmButton = { TextButton({ onUse(drawn) }) { Text("Use this") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
