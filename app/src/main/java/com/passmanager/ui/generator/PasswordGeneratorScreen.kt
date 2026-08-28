package com.passmanager.ui.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.domain.validation.PasswordStrengthEvaluator
import com.passmanager.ui.components.AppSnackbarHost
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelHeader
import com.passmanager.ui.components.PillButton
import com.passmanager.ui.components.PillOutlinedButton
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.theme.FootnoteStyle
import com.passmanager.ui.theme.StrengthFairColor
import com.passmanager.ui.util.rememberSecureClipboard
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val GenSectionGap = 12.dp

/** Characters per line in the preview block — sized to fill the card at monospace 19sp. */
private const val PreviewPasswordCharsPerLine = 16

/**
 * Entropy at which the meter reads full. Well past the point a password is unbreakable; the tick
 * row is a slope, not a pass mark, and stopping it at "strong" made every good password look
 * identical to every excellent one.
 */
private const val FullMeterEntropyBits = 130f

/** Thick track + vertical pill thumb. */
private val LengthTrackHeight = 10.dp
private val LengthThumbSize = DpSize(width = 5.dp, height = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    onPasswordSelected: (CharArray) -> Unit,
    onNavigateBack: () -> Unit,
    showUseButton: Boolean = true,
    viewModel: PasswordGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = rememberSecureClipboard()

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
    val sliderPrimary = MaterialTheme.colorScheme.primary
    val sliderPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val lengthSliderColors = SliderDefaults.colors(
        thumbColor = sliderPrimary,
        activeTrackColor = sliderPrimary,
        inactiveTrackColor = sliderPrimaryContainer.copy(alpha = 0.55f)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PanelHeader(
                title = stringResource(R.string.generator_title),
                large = true,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.action_back),
                onNavigationClick = onNavigateBack
            ) {
                // The Use button belongs beside the way back, not at the foot of the panel: the
                // generator is opened from a form to answer one question, and the answer is at the
                // top of the screen.
                if (showUseButton) {
                    PillButton(
                        text = stringResource(R.string.generator_use_button),
                        enabled = uiState.password.isNotEmpty(),
                        onClick = {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONFIRM
                            )
                            onPasswordSelected(uiState.password.toCharArray())
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GenSectionGap)
        ) {
            Spacer(Modifier.height(4.dp))

            GeneratedPasswordCard(
                password = uiState.password,
                entropyBits = uiState.entropyBits,
                onRegenerate = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    viewModel.generate()
                },
                onCopy = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    clipboard.copy(uiState.password)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.generator_copied))
                    }
                }
            )

            PanelCard(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.generator_length_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${sliderValue.roundToInt().coerceIn(8, 64)}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONTEXT_CLICK
                        )
                        viewModel.setLength(sliderValue.roundToInt().coerceIn(8, 64))
                    },
                    valueRange = 8f..64f,
                    steps = 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .semantics { contentDescription = lengthSemantics },
                    interactionSource = lengthSliderInteraction,
                    colors = lengthSliderColors,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = lengthSliderInteraction,
                            colors = lengthSliderColors,
                            enabled = true,
                            thumbSize = LengthThumbSize
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = lengthSliderColors,
                            enabled = true,
                            modifier = Modifier.height(LengthTrackHeight),
                            thumbTrackGapSize = 2.dp
                        )
                    }
                )

                Text(
                    text = stringResource(R.string.generator_length_hint),
                    style = FootnoteStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PanelCard(
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                CharsetSwitchRow(
                    label = stringResource(R.string.generator_uppercase),
                    checked = uiState.includeUppercase,
                    enabledCharsetCount = enabledCharsetCount,
                    onCheckedChange = { viewModel.toggleUppercase() }
                )
                CharsetSwitchRow(
                    label = stringResource(R.string.generator_lowercase),
                    checked = uiState.includeLowercase,
                    enabledCharsetCount = enabledCharsetCount,
                    onCheckedChange = { viewModel.toggleLowercase() }
                )
                CharsetSwitchRow(
                    label = stringResource(R.string.generator_digits),
                    checked = uiState.includeDigits,
                    enabledCharsetCount = enabledCharsetCount,
                    onCheckedChange = { viewModel.toggleDigits() }
                )
                CharsetSwitchRow(
                    label = stringResource(R.string.generator_symbols),
                    checked = uiState.includeSymbols,
                    enabledCharsetCount = enabledCharsetCount,
                    onCheckedChange = { viewModel.toggleSymbols() }
                )
                Text(
                    text = stringResource(R.string.generator_charset_hint),
                    style = FootnoteStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Opened from a bank form, the generator is not free to pick anything: say so, rather
            // than let the user wonder why the length snapped back on the way out.
            uiState.constraint?.let { constraint ->
                SectionFootnote(
                    stringResource(
                        R.string.generator_constraint_hint,
                        constraint.minLength,
                        constraint.maxLength
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The password, its meter and the two things you can do with it, in one card.
 *
 * The password block sits on a lighter tone than the rest of the card so the characters read as
 * output rather than as a heading, and the tick row is drawn along the seam between the two — it
 * belongs to the password above it, not to the controls below.
 */
@Composable
private fun GeneratedPasswordCard(
    password: String,
    entropyBits: Int,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit
) {
    val strength = remember(password) {
        if (password.isEmpty()) null else PasswordStrengthEvaluator.evaluate(password)
    }
    val strengthColor = when (strength?.ordinal) {
        0 -> MaterialTheme.colorScheme.error
        1 -> StrengthFairColor
        2 -> MaterialTheme.colorScheme.tertiary
        3 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val strengthLabels = listOf(
        stringResource(R.string.strength_weak),
        stringResource(R.string.strength_fair),
        stringResource(R.string.strength_good),
        stringResource(R.string.strength_strong)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 15.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (password.isEmpty()) {
                    Text(
                        text = stringResource(R.string.generator_empty_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Laid out sixteen to a line on a sixteen-column grid, so every character
                    // occupies the same slot whichever line it lands on and a shorter last line
                    // stays aligned with the one above it. Proportional monospace alone does not
                    // achieve that once a glyph is wider than the cell.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        password.chunked(PreviewPasswordCharsPerLine).forEach { line ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                repeat(PreviewPasswordCharsPerLine) { column ->
                                    Text(
                                        text = line.getOrNull(column)?.toString() ?: "",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 19.sp,
                                            lineHeight = 27.sp,
                                            textAlign = TextAlign.Center
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            EntropyMeter(
                entropyBits = entropyBits,
                color = strengthColor,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 3.dp)
            )
        }

        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strength?.let { strengthLabels[it.ordinal].uppercase() } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = strengthColor
                )
                Text(
                    text = stringResource(R.string.generator_entropy_line, entropyBits),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // What the pool actually turned out to be, rather than what was asked for: a symbol
            // set that is switched on can still produce a password without one.
            Text(
                text = characterMixLine(password),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillOutlinedButton(
                    text = "",
                    icon = Icons.Default.Refresh,
                    onClick = onRegenerate,
                    contentPadding = PaddingValues(vertical = 15.dp),
                    modifier = Modifier.width(56.dp)
                )
                PillButton(
                    text = stringResource(R.string.generator_copy_button),
                    enabled = password.isNotEmpty(),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Four ticks that fill as entropy climbs towards [FullMeterEntropyBits]. */
@Composable
private fun EntropyMeter(
    entropyBits: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val filled = ((entropyBits / FullMeterEntropyBits).coerceIn(0f, 1f) * 4).roundToInt()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < filled) color else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun characterMixLine(password: String): String {
    val letters = password.count { it.isLetter() }
    val digits = password.count { it.isDigit() }
    val symbols = password.length - letters - digits
    return stringResource(R.string.generator_mix_line, letters, digits, symbols)
}

@Composable
private fun CharsetSwitchRow(
    label: String,
    checked: Boolean,
    enabledCharsetCount: Int,
    onCheckedChange: () -> Unit
) {
    // The last enabled set cannot be switched off — there would be nothing to draw from — so it
    // dims rather than disappearing, and the footnote under the group says why.
    val isLastEnabled = checked && enabledCharsetCount <= 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLastEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            enabled = !isLastEnabled,
            onCheckedChange = { new ->
                if (new == checked) return@Switch
                if (!new && checked && enabledCharsetCount <= 1) return@Switch
                onCheckedChange()
            }
        )
    }
}
