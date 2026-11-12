package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.CalibrationOffsetDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MIN_OFFSET_MGDL = -36.0
private const val MAX_OFFSET_MGDL = 36.0

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class FixedOffsetViewModel(
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FixedOffsetUiState(
            offsetMgdl = preferences.get(CalibrationOffsetDoubleKey.UserOffset),
            glucoseUnit = profileUtil.units
        )
    )
    val uiState: StateFlow<FixedOffsetUiState> = _uiState.asStateFlow()

    /** Write offset in mg/dL, clamped to ±36. */
    fun updateOffset(mgdl: Double) {
        val clamped = mgdl.coerceIn(MIN_OFFSET_MGDL, MAX_OFFSET_MGDL)
        preferences.put(CalibrationOffsetDoubleKey.UserOffset, clamped)
        _uiState.update { it.copy(offsetMgdl = clamped) }
    }
}

data class FixedOffsetUiState(
    val offsetMgdl: Double = 0.0,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL
)
