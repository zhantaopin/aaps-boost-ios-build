package app.aaps.plugins.calibration.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.calibration.CalibrationStrings

@Composable
internal fun FixedOffsetScreen(
    viewModel: FixedOffsetViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = stringResource(CalibrationStrings.fixed_offset_name)
    val backDesc = stringResource(CoreUiStrings.back)

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {}
            )
        )
    }

    FixedOffsetContent(
        state = state,
        onUpdateOffset = viewModel::updateOffset
    )
}

@Composable
private fun FixedOffsetContent(
    state: FixedOffsetUiState,
    onUpdateOffset: (Double) -> Unit
) {
    val offsetMgdl = state.offsetMgdl
    val glucoseUnit = state.glucoseUnit

    val offsetInUnits = if (glucoseUnit == GlucoseUnit.MMOL) offsetMgdl * Constants.MGDL_TO_MMOLL else offsetMgdl
    val displayUnits = if (glucoseUnit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AapsSpacing.medium)) {
                Text(
                    text = stringResource(CalibrationStrings.fixed_offset_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(AapsSpacing.small))
                Text(
                    text = stringResource(
                        CalibrationStrings.fixed_offset_hint,
                        "${offsetInUnits.formatSign(1)} $displayUnits"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AapsSpacing.small))
                Slider(
                    value = offsetMgdl.coerceIn(-36.0, 36.0).toFloat(),
                    valueRange = -36f..36f,
                    steps = 71,   // 72 ticks at 1 mg/dL each
                    onValueChange = { v -> onUpdateOffset(v.toDouble()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "-2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "+2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(AapsSpacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onUpdateOffset(-9.0) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(CalibrationStrings.fixed_offset_quick_minus)) }
                    Button(
                        onClick = { onUpdateOffset(0.0) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(CalibrationStrings.fixed_offset_quick_zero)) }
                    Button(
                        onClick = { onUpdateOffset(+9.0) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(CalibrationStrings.fixed_offset_quick_plus)) }
                }
            }
        }
    }
}

/**
 * Signed text with [decimals] decimals, e.g. `+0.8`.
 *
 * `String.format` is JVM-only and this file is also compiled for iOS, so it goes through
 * [NumberFormat], which is plain Kotlin and already knows the locale separator.
 */
private fun Double.formatSign(decimals: Int = 1): String {
    val text = NumberFormat.withDecimalsHalfUp(decimals).format(this)
    // The old `%+` pattern signed zero as well; keep that rather than testing > 0.
    return if (this >= 0) "+$text" else text
}
