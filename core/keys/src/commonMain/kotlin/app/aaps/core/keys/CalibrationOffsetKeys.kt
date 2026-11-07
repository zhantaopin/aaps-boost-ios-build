package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

/**
 * Non-preference keys for the manual BG user-offset feature.
 *
 * Placed in core:keys (not :plugins:calibration) so that :plugins:source can depend on it
 * without creating a source↔calibration circular dependency that crashes at startup.
 */
enum class CalibrationOffsetDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    /**
     * Fixed offset (mg/dL) applied to every CGM reading before it enters the DB.
     * Positive values shift BG up (APS becomes more conservative → 止血偏高).
     * Only takes effect when the active CALIBRATION plugin is FixedOffsetPlugin.
     * Clamped to ±36 mg/dL (≈±2.0 mmol/L). Default 0.0 = no offset.
     */
    UserOffset("calibration_user_offset_mgdl", 0.0),
}
