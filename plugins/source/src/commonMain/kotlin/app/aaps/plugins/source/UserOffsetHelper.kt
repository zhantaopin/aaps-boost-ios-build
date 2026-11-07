package app.aaps.plugins.source

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.CalibrationOffsetDoubleKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Applies the global manual BG user-offset to a glucose value before it enters the DB.
 *
 * Only active when FixedOffsetPlugin is the selected CALIBRATION plugin — otherwise returns raw.
 * No circular dependency on :plugins:calibration — the check uses a simple class-name string
 * so :plugins:source never needs to depend on :plugins:calibration (which crashes at startup
 * if added).
 *
 * Clamped to ±36 mg/dL (≈±2.0 mmol/L).
 */
fun applyUserOffset(
    rawValueMgdl: Double,
    preferences: Preferences,
    activePlugin: ActivePlugin,
): Double {
    // Only apply offset when FixedOffsetPlugin is the active CALIBRATION plugin.
    // String comparison avoids :source → :calibration circular dependency.
    val calClass = activePlugin.activeCalibration.javaClass.simpleName
    if (calClass != "FixedOffsetPlugin") return rawValueMgdl
    val offset = preferences.get(CalibrationOffsetDoubleKey.UserOffset).coerceIn(-36.0, 36.0)
    return rawValueMgdl + offset
}
