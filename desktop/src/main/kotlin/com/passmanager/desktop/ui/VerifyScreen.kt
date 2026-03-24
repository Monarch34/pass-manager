package com.passmanager.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.passmanager.desktop.ui.components.ThemeToggleIconButton

private const val CODE_LENGTH = 6

@Composable
fun VerifyScreen(
    attemptsRemaining: Int,
    error: String?,
    safetyNumber: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onCodeSubmitted: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digits = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(CODE_LENGTH) { FocusRequester() } }
    val submitting = remember { mutableStateOf(false) }

    // On error: clear all inputs, re-enable, and refocus the first box
    LaunchedEffect(error) {
        if (error != null) {
            submitting.value = false
            for (i in digits.indices) digits[i] = ""
            try { focusRequesters[0].requestFocus() } catch (_: Exception) {}
        }
    }

    // Auto-focus first box on initial composition
    LaunchedEffect(Unit) {
        try { focusRequesters[0].requestFocus() } catch (_: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize()) {
        ThemeToggleIconButton(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val padH = when {
                maxWidth < 400.dp -> 16.dp
                maxWidth < 520.dp -> 24.dp
                else -> 32.dp
            }
            val padV = when {
                maxHeight < 560.dp -> 16.dp
                else -> 24.dp
            }
            val digitBoxDp = when {
                maxWidth < 400.dp -> 44.dp
                else -> 56.dp
            }
            val digitGap = when {
                maxWidth < 420.dp -> 4.dp
                else -> 8.dp
            }
            val groupGap = when {
                maxWidth < 420.dp -> 4.dp
                else -> 8.dp
            }
            val titleStyle = when {
                maxWidth < 400.dp -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineMedium
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padH, vertical = padV),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
        Text(
            text = Strings.VERIFY_TITLE,
            style = titleStyle,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = Strings.VERIFY_INSTRUCTION,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (safetyNumber.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = Strings.SAFETY_NUMBER_LABEL,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = safetyNumber,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = Strings.SAFETY_NUMBER_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))

        // 6 digit input boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(digitGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until CODE_LENGTH) {
                // Visual separator between groups of 3
                if (i == 3) {
                    Spacer(Modifier.width(groupGap))
                }

                DigitBox(
                    boxSize = digitBoxDp,
                    value = digits[i],
                    focusRequester = focusRequesters[i],
                    isError = error != null,
                    enabled = !submitting.value,
                    onValueChange = { newValue ->
                        if (submitting.value) return@DigitBox

                        // Handle paste: if user pastes a full 6-digit code
                        if (newValue.length >= CODE_LENGTH && newValue.all { it.isDigit() }) {
                            val code = newValue.take(CODE_LENGTH)
                            for (j in 0 until CODE_LENGTH) {
                                digits[j] = code[j].toString()
                            }
                            submitting.value = true
                            onCodeSubmitted(code)
                            return@DigitBox
                        }

                        // Single digit input
                        val digit = newValue.lastOrNull()?.takeIf { it.isDigit() }
                        if (digit != null) {
                            digits[i] = digit.toString()
                            if (i < CODE_LENGTH - 1) {
                                try { focusRequesters[i + 1].requestFocus() } catch (_: Exception) {}
                            } else {
                                // Last digit entered — auto-submit
                                val code = digits.joinToString("")
                                if (code.length == CODE_LENGTH) {
                                    submitting.value = true
                                    onCodeSubmitted(code)
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (submitting.value) return@DigitBox
                        if (digits[i].isEmpty() && i > 0) {
                            digits[i - 1] = ""
                            try { focusRequesters[i - 1].requestFocus() } catch (_: Exception) {}
                        } else {
                            digits[i] = ""
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Error message
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
        }

        // Attempts remaining
        Text(
            text = Strings.attemptsRemaining(attemptsRemaining),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(Strings.VERIFY_CANCEL)
            }
        }
        }
        }
    }
}

@Composable
private fun DigitBox(
    boxSize: Dp,
    value: String,
    focusRequester: FocusRequester,
    isError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val isFocused = remember { mutableStateOf(false) }
    val innerSize = boxSize - 8.dp
    val fontSp = ((boxSize.value * 0.42f).roundToInt()).coerceIn(18, 26).sp

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused.value -> MaterialTheme.colorScheme.primary
        value.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (isFocused.value || isError) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .size(boxSize)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .size(innerSize)
                    .onFocusChanged { isFocused.value = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                            onBackspace()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = fontSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }
}
