package com.passmanager.ui.generator

import com.passmanager.R
import com.passmanager.ui.util.copyToClipboard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.ui.components.PasswordStrengthBar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val GenCardPadding = 16.dp
private val GenSectionGap = 12.dp
private val GenIconSize = 22.dp

/** Characters per line in the preview block — sized to fill the card at titleLarge monospace. */
private const val PreviewPasswordCharsPerLine = 16

/** Thick track + vertical pill thumb. */
private val LengthTrackHeight = 10.dp
private val LengthThumbSize = DpSize(width = 5.dp, height = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    onPasswordSelected: (String) -> Unit,
    onNavigateBack: () -> Unit,
    showUseButton: Boolean = true,
    viewModel: PasswordGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    var sliderValue by remember { mutableFloatStateOf(uiState.length.toFloat()) }
    LaunchedEffect(uiState.length) {
        sliderValue = uiState.length.toFloat()
    }

    val enabledCharsetCount = listOf(
        uiState.includeUppercase,
        uiState.includeLowercase,
        uiState.includeDigits,
        uiState.includeSymbols
    ).count { it }

    val lengthSemantics =
        stringResource(R.string.generator_length_semantics, sliderValue.roundToInt().coerceIn(8, 64))

    val lengthSliderInteraction = remember { MutableInteractionSource() }
    val sliderPrimary         = MaterialTheme.colorScheme.primary
    val sliderPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val lengthSliderColors = SliderDefaults.colors(
        thumbColor         = sliderPrimary,
        activeTrackColor   = sliderPrimary,
        inactiveTrackColor = sliderPrimaryContainer.copy(alpha = 0.55f)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.generator_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.generator_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GenSectionGap)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Preview card ───────────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(GenCardPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppShieldLogo(size = 32.dp, iconSize = 18.dp)
                        Text(
                            text = stringResource(R.string.generator_preview_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Password display
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            if (uiState.password.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.generator_empty_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = uiState.password.chunked(PreviewPasswordCharsPerLine)
                                        .joinToString("\n"),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp,
                                        lineHeight = MaterialTheme.typography.titleLarge.fontSize * 1.5f
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (uiState.password.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PasswordStrengthBar(
                            password = uiState.password,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Bottom row: entropy + copy button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (uiState.password.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = stringResource(R.string.generator_entropy_bits, uiState.entropyBits),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        FilledTonalButton(
                            onClick = {
                                if (uiState.password.isNotEmpty()) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                    copyToClipboard(context, "Password", uiState.password, clearAfterMs = 15_000L, scope = scope)
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.generator_copied)) }
                                }
                            },
                            enabled = uiState.password.isNotEmpty(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.generator_copy_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // ── Length card ────────────────────────────────────────────────────
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(GenCardPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(GenIconSize)
                        )
                        Text(
                            text = stringResource(R.string.generator_length_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.generator_length_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${sliderValue.roundToInt().coerceIn(8, 64)}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.generator_characters_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            viewModel.setLength(sliderValue.roundToInt().coerceIn(8, 64))
                        },
                        valueRange = 8f..64f,
                        steps = 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .semantics { contentDescription = lengthSemantics },
                        interactionSource = lengthSliderInteraction,
                        colors = lengthSliderColors,
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = lengthSliderInteraction,
                                colors = lengthSliderColors,
                                enabled = true,
                                thumbSize = LengthThumbSize,
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = lengthSliderColors,
                                enabled = true,
                                modifier = Modifier.height(LengthTrackHeight),
                                thumbTrackGapSize = 2.dp,
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "8",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "64",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ── Character sets card ────────────────────────────────────────────
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.generator_charset_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = GenCardPadding, vertical = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.generator_charset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = GenCardPadding)
                            .padding(bottom = 8.dp)
                    )
                    CharsetSwitchRow(
                        stringResource(R.string.generator_uppercase),
                        stringResource(R.string.generator_uppercase_desc),
                        uiState.includeUppercase,
                        enabledCharsetCount
                    ) { viewModel.toggleUppercase() }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = GenCardPadding)
                    )
                    CharsetSwitchRow(
                        stringResource(R.string.generator_lowercase),
                        stringResource(R.string.generator_lowercase_desc),
                        uiState.includeLowercase,
                        enabledCharsetCount
                    ) { viewModel.toggleLowercase() }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = GenCardPadding)
                    )
                    CharsetSwitchRow(
                        stringResource(R.string.generator_digits),
                        stringResource(R.string.generator_digits_desc),
                        uiState.includeDigits,
                        enabledCharsetCount
                    ) { viewModel.toggleDigits() }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = GenCardPadding)
                    )
                    CharsetSwitchRow(
                        stringResource(R.string.generator_symbols),
                        stringResource(R.string.generator_symbols_desc),
                        uiState.includeSymbols,
                        enabledCharsetCount
                    ) { viewModel.toggleSymbols() }
                }
            }

            // ── Action buttons ─────────────────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    viewModel.generate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.generator_regenerate_button),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (showUseButton) {
                Button(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        onPasswordSelected(uiState.password)
                    },
                    enabled = uiState.password.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.generator_use_button),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CharsetSwitchRow(
    headline: String,
    supporting: String,
    checked: Boolean,
    enabledCharsetCount: Int,
    onCheckedChange: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { new ->
                    if (new == checked) return@Switch
                    if (!new && checked && enabledCharsetCount <= 1) return@Switch
                    onCheckedChange()
                }
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
