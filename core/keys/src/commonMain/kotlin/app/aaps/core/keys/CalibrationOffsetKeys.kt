package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec

/**
 * Non-preference keys for the manual BG user-offset feature.
 *
 * Placed in core:keys (not :plugins:calibration) so that :plugins:source can depend on it
 * without creating a source↔calibration circular dependency that crashes at startup.
 */
enum class CalibrationOffsetDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true,
    override val sync: SyncSpec? = null
) : DoubleNonPreferenceKey {

    /**
     * Fixed offset (mg/dL) applied to every CGM reading before it enters the DB.
     * Positive values shift BG up (APS becomes more conservative → 止血偏高).
     * Only takes effect when the active CALIBRATION plugin is FixedOffsetPlugin.
     * Clamped to ±36 mg/dL (≈±2.0 mmol/L). Default 0.0 = no offset.
     */
    // Bidirectional so a paired client can change the master's fixed offset and see it come back:
    // the calibration selection is synced too, so the same plugin sheet is on both devices.
    UserOffset("calibration_user_offset_mgdl", 0.0, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
}
