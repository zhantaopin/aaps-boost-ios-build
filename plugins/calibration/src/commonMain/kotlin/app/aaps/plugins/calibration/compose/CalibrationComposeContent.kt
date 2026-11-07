package app.aaps.plugins.calibration.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.calibration.CalibrationStrings

internal class CalibrationComposeContent : ComposablePluginContent {

    private enum class CalibrationTab { Linear, FixedOffset }

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        // onSettings intentionally unused — the calibration plugin has no preferences screen.
        val linearViewModel: CalibrationViewModel = metroViewModel()
        val fixedViewModel: FixedOffsetViewModel = metroViewModel()

        var tab by remember { mutableStateOf(CalibrationTab.Linear) }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                CalibrationTab.values().forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CalibrationTab.values().size),
                        label = {
                            Text(
                                text = when (t) {
                                    CalibrationTab.Linear      -> stringResource(CalibrationStrings.linear_calibration_name)
                                    CalibrationTab.FixedOffset -> stringResource(CalibrationStrings.fixed_offset_name)
                                }
                            )
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    CalibrationTab.Linear -> CalibrationScreen(
                        viewModel = linearViewModel,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack
                    )
                    CalibrationTab.FixedOffset -> FixedOffsetScreen(
                        viewModel = fixedViewModel,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack
                    )
                }
            }
        }
    }
}
