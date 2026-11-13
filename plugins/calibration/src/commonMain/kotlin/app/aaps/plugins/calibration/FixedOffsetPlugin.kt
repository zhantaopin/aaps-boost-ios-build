package app.aaps.plugins.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.plugins.calibration.compose.FixedOffsetComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Independent calibration plugin that applies a single fixed mg/dL offset to every CGM reading.
 *
 * Unlike LinearCalibrationPlugin (which fits a slope+offset over fingerstick entries), this plugin
 * adds a constant — the user dials it directly. The offset is applied by source-layer Workers
 * BEFORE readings land in the DB (see `UserOffsetHelper`), so the offset becomes part of the
 * stored glucose value and feeds APS, history, and any downstream consumer uniformly.
 *
 * Toggle by selecting this plugin from CALIBRATION → Fixed offset. Switching back to No
 * calibration or Linear calibration silences the offset immediately.
 *
 * Range is ±36 mg/dL (≈±2.0 mmol/L), clamped both at read time and at write time.
 */
@Inject
@SingleIn(AppScope::class)
// Bound as PluginBase, not implicitly: the class has more than one supertype, so Metro cannot pick
// one. The plugin list wants PluginBase.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(720)
class FixedOffsetPlugin(
    aapsLogger: AAPSLogger,
    override val rh: TextResolver,
    notificationManager: NotificationManager,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CALIBRATION)
        .icon(IcCalibration)
        .pluginName(CalibrationStrings.fixed_offset_name)
        .description(CalibrationStrings.description_fixed_offset)
        .composeContent { FixedOffsetComposeContent(preferences, profileUtil) },
    aapsLogger, rh, notificationManager
), Calibration {

    override suspend fun calibrate(
        data: MutableList<InMemoryGlucoseValue>,
        @Suppress("UNUSED_PARAMETER") context: CalibrationContext
    ): MutableList<InMemoryGlucoseValue> {
        // Identity — the fixed offset is applied once at the source layer (see UserOffsetHelper)
        // which writes GV.value with the offset baked in before anything reaches the DB.
        // calibrate() touching calibrated here would double-count the offset since
        // bucketedData.value already carries the source-layer shift.
        return data
    }

    override suspend fun addEntry(
        @Suppress("UNUSED_PARAMETER") bgMgdl: Double,
        @Suppress("UNUSED_PARAMETER") timestamp: Long
    ): AddEntryResult = AddEntryResult.Accepted

    override suspend fun checkPreconditions(): AddEntryResult = AddEntryResult.Accepted
}
