package com.passmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.passmanager.R

/**
 * Reusable delete-confirmation dialog with responsive button layout
 * (stacks vertically on narrow screens).
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // The platform default dialog width is around 300.dp and ignores what this layout asks for,
    // which put the two actions under the stacking threshold below and left them centred in a
    // column on every phone. Taking the width lets the padding and the max width here mean what
    // they say, and the stacked branch goes back to being for genuinely narrow screens.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackButtons = maxWidth < 340.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.height(24.dp))

                        // Both actions are text buttons, and the destructive one is red type
                        // rather than a red slab: a filled error button is the largest, loudest
                        // thing on a dialog whose whole point is to give the reader a moment to
                        // reconsider, and it sits exactly where a confirming tap would land.
                        val cancelButton: @Composable (Modifier) -> Unit = { mod ->
                            TextButton(
                                onClick = onDismiss,
                                modifier = mod.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    stringResource(R.string.cancel).uppercase(),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        val deleteButton: @Composable (Modifier) -> Unit = { mod ->
                            TextButton(
                                onClick = onConfirm,
                                modifier = mod.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    stringResource(R.string.item_delete_button).uppercase(),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (stackButtons) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                cancelButton(Modifier.fillMaxWidth())
                                deleteButton(Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.End
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                cancelButton(Modifier)
                                deleteButton(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
